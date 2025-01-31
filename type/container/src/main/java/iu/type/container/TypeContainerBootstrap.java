/*

 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.type.container;

import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.lang.module.ModuleDescriptor.Exports;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.UnsafeRunnable;
import edu.iu.logging.IuLogContext;
import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceKey;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;
import edu.iu.type.base.FilteringClassLoader;
import edu.iu.type.base.ModularClassLoader;
import iu.type.container.spi.IuEnvironment;

/**
 * Authentication and authorization bootstrap.
 */
public class TypeContainerBootstrap implements UnsafeRunnable, AutoCloseable {
	static {
		IuObject.assertNotOpen(TypeContainerBootstrap.class);
	}

	private static final Logger LOG = Logger.getLogger(TypeContainerBootstrap.class.getName());

	private final TypeContainerArchive[] archives;
	private final ClassLoader parentLoader;
	private final ModuleLayer parentLayer;

	private volatile ModularClassLoader base;
	private volatile ModularClassLoader support;
	private volatile Deque<IuComponent> initializedComponents;
	private volatile boolean closed;

	/**
	 * Constructor
	 */
	public TypeContainerBootstrap() {
		final var componentsPropertyValue = IuRuntimeEnvironment.envOptional("iu.boot.components");
		if (componentsPropertyValue == null) {
			archives = null;
			parentLoader = null;
			parentLayer = null;
		} else {
			final var archivePathNames = componentsPropertyValue.split(",");
			archives = new TypeContainerArchive[archivePathNames.length];
			for (var i = 0; i < archives.length; i++)
				archives[i] = IuException.unchecked(Path.of(archivePathNames[i]), TypeContainerArchive::new);

			parentLoader = IuObject.class.getClassLoader();
			parentLayer = IuObject.class.getModule().getLayer();
		}
	}

	@Override
	public synchronized void run() throws Throwable {
		if (archives == null)
			return;

		LOG.fine("before init container");
		final var filter = new FilteringClassLoader(IuIterable.iter(IuObject.class.getPackageName()), parentLoader);
		final var api = IuIterable.of(() -> Stream.of(archives).flatMap(a -> Stream.of(a.api())).iterator());

		class PendingExport {
			Exports exports;
			Module module;
		}
		final var baseInit = new Consumer<Controller>() {
			Controller controller;
			Map<String, PendingExport> pendingExports = new LinkedHashMap<>();

			@Override
			public void accept(Controller c) {
				controller = c;
				for (final var module : c.layer().modules())
					for (final var exports : module.getDescriptor().exports())
						for (final var target : exports.targets()) {
							final var pendingExport = new PendingExport();
							pendingExport.module = module;
							pendingExport.exports = exports;
							pendingExports.put(target, pendingExport);
						}
			}
		};
		base = new ModularClassLoader(false, api, parentLayer, filter, baseInit);

		initializedComponents = new ArrayDeque<>();
		try {
			final var current = Thread.currentThread();
			final var restore = current.getContextClassLoader();
			try {
				current.setContextClassLoader(base);
				LOG.fine("after init base " + base.getModuleLayer());
			} finally {
				current.setContextClassLoader(restore);
			}

			for (final var archive : archives) {
				final ClassLoader parent;
				if (archive.support().length == 0)
					parent = base;
				else {
					support = new ModularClassLoader(false, IuIterable.iter(archive.support()), base.getModuleLayer(),
							base, c -> {
							});

					try {
						current.setContextClassLoader(support);
						LOG.fine(() -> "after init support " + support.getModuleLayer());
					} finally {
						current.setContextClassLoader(restore);
					}

					parent = support;
				}

				final List<InputStream> sources = new ArrayList<>(1 + archive.lib().length);
				sources.add(Files.newInputStream(archive.primary()));
				for (final var lib : archive.lib())
					sources.add(Files.newInputStream(lib));

				final var compInit = new Consumer<Controller>() {
					@Override
					public void accept(Controller c) {
						for (final var module : c.layer().modules()) {
							final var pendingExport = baseInit.pendingExports.get(module.getName());
							if (pendingExport != null)
								baseInit.controller.addExports(pendingExport.module, pendingExport.exports.source(),
										module);

							if (support != null)
								c.addReads(module, support.getUnnamedModule());
						}
					}
				};
				final var component = IuComponent.of(parent, base.getModuleLayer(), compInit, sources.get(0),
						sources.subList(1, sources.size()).toArray(InputStream[]::new));

				for (final var source : sources)
					source.close();

				LOG.fine(() -> "after create " + component);
				initializedComponents.push(component);
			}

			initializeComponents(initializedComponents);

		} catch (Throwable e) {
			IuException.suppress(e, this::close);
			throw e;
		}
	}

	/**
	 * Initializes the environment for a {@link IuComponent}
	 * 
	 * @param component {@link IuComponent}
	 * @return component
	 */
	static IuEnvironment initEnvironment(IuComponent component) {
		final var loader = component.classLoader();
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try {
			current.setContextClassLoader(loader);

			final var properties = new Properties();
			final var propertiesResource = loader.getResourceAsStream("META-INF/iu-type-container.properties");
			if (propertiesResource != null)
				IuException.unchecked(() -> {
					properties.load(propertiesResource);
					propertiesResource.close();
				});

			final var development = "true".equals(properties.getProperty("development"));
			final var endpoint = properties.getProperty("endpoint");
			final var application = properties.getProperty("application");
			final var environment = properties.getProperty("environment");
			final var module = properties.getProperty("module");
			final var runtime = properties.getProperty("runtime");
			final var comp = properties.getProperty("component");

			IuLogContext.initializeContext(null, development, endpoint, application, environment, module, runtime,
					comp);

			final Queue<Runnable> undo = new ArrayDeque<>();
			final BiConsumer<String, String> inject = (name, value) -> {
				if (value == null)
					return;

				final var oldValue = System.getProperty(name);
				System.setProperty(name, value);
				if (oldValue == null)
					undo.add(() -> System.clearProperty(name));
				else
					undo.add(() -> System.setProperty(name, oldValue));
			};

			synchronized (System.getProperties()) {
				try {
					inject.accept("iu.endpoint", endpoint);
					inject.accept("iu.application", application);
					inject.accept("iu.environment", environment);
					inject.accept("iu.module", module);
					inject.accept("iu.runtime", runtime);
					inject.accept("iu.component", comp);

					final var envLoader = ServiceLoader.load(IuEnvironment.class).iterator();

					if (envLoader.hasNext())
						return envLoader.next();
					else
						return new DefaultEnvironment();

				} finally {
					undo.forEach(Runnable::run);
				}
			}

		} finally {
			current.setContextClassLoader(restore);
		}
	}

	/**
	 * Helper method for {@link #initializeComponents(Iterable)}.
	 * 
	 * @param boundResources bound resources cache
	 * @param refInstance    reference instance cache
	 * @param envByComp      preinitialized environment instances
	 * @param component      component containing the resource reference
	 * @param resourceRef    resource reference to resolve
	 * @param key            resource key constructed from resourceRef
	 * @return bound resource or {@link EnvironmentResourceTest}
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static IuResource resolveResource( //
			Map<IuResourceKey<?>, Queue<IuResource<?>>> boundResources, //
			Map<IuType<?, ?>, Object> refInstance, //
			Map<IuComponent, IuEnvironment> envByComp, //
			IuComponent component, //
			IuResourceReference<?, ?> resourceRef, //
			IuResourceKey<?> key) {

		final var name = resourceRef.name();
		final var type = resourceRef.type();

		final Iterable<IuResource<?>> resources = boundResources.get(key);
		if (resources == null) {
			final var referrerType = resourceRef.referrerType();

			var instance = refInstance.get(referrerType);
			if (instance == null)
				try {
					instance = referrerType.constructor().exec();
					refInstance.put(referrerType, instance);
				} catch (Throwable e) {
					final var npe = new NullPointerException("Missing resource binding " + key);
					npe.initCause(e);
					throw npe;
				}

			final var defaultValue = ((IuResourceReference) resourceRef).value(instance);
			return new EnvironmentResource(envByComp.get(component), name, type, defaultValue);
		}

		final var erased = type.erasedClass();
		final var resourceIterator = resources.iterator();
		final var firstResource = resourceIterator.next();
		if (erased == Iterable.class //
				&& type.typeParameter("T").erasedClass().isAssignableFrom(firstResource.type().erasedClass()))
			return new CompoundResource(name, type, resources);

		if (resourceIterator.hasNext())
			throw new IllegalStateException("Multiple resources defined matching " + resourceRef + " " + resources);

		return firstResource;
	}

	/**
	 * Initializes components.
	 * 
	 * @param componentsToInitialize Created components that have not yet been
	 *                               initialized
	 * @throws Throwable If an error occurs
	 */
	@SuppressWarnings("unchecked")
	static void initializeComponents(Iterable<IuComponent> componentsToInitialize) throws Throwable {
		final Map<IuComponent, IuEnvironment> envByComp = new LinkedHashMap<>();
		final Map<IuResourceKey<?>, Queue<IuResource<?>>> boundResources = new LinkedHashMap<>();
		final List<TypeContainerResource> containerResources = new ArrayList<>();

		for (final var component : componentsToInitialize) {
			final var env = initEnvironment(component);
			envByComp.put(component, env);
			LOG.config("environment " + env + "; " + component);

			for (final var resource : component.resources()) {
				final var key = IuResourceKey.from(resource);
				final Queue<IuResource<?>> resources = boundResources.computeIfAbsent(key, a -> new ArrayDeque<>());
				resources.offer(resource);
				containerResources.add(new TypeContainerResource(resource, component));
				LOG.fine(() -> "after create " + resource);
			}
		}

		final Map<IuType<?, ?>, Object> refInstance = new LinkedHashMap<>();
		final Map<IuResourceKey<?>, Queue<IuResourceReference<?, ?>>> resourceReferences = new LinkedHashMap<>();
		for (final var component : componentsToInitialize)
			for (final var resourceRef : component.resourceReferences()) {
				final var key = IuResourceKey.from(resourceRef);
				final var resource = resolveResource(boundResources, refInstance, envByComp, component, resourceRef,
						key);

				final var resourceReferenceQueue = resourceReferences.computeIfAbsent(key, k -> new ArrayDeque<>());
				resourceReferenceQueue.add(resourceRef);
				resourceRef.bind(resource);

				final var r = resource;
				LOG.fine(() -> "bind " + resourceRef + " " + r);
			}

		var priority = 0;
		Throwable error = null;
		final Queue<TypeContainerResource> pendingResources = new ArrayDeque<>();
		containerResources.sort(TypeContainerResource::compareTo);
		for (final var containerResource : containerResources) {
			if (containerResource.priority() != priority) {
				priority = containerResource.priority();
				while (!pendingResources.isEmpty()) {
					final var pendingResource = pendingResources.poll();
					LOG.fine(() -> "before join " + pendingResource);
					error = IuException.suppress(error, pendingResource::join);
				}
				if (error != null)
					throw error;
			}

			containerResource.asyncInit();
			pendingResources.add(containerResource);
		}

		for (final var containerResource : pendingResources) {
			LOG.fine(() -> "before join " + containerResource);
			error = IuException.suppress(error, containerResource::join);
		}

		if (error != null)
			throw error;

	}

	@Override
	public synchronized void close() throws Exception {
		if (archives == null)
			return;

		Throwable error = null;

		final var initializedComponents = this.initializedComponents;
		if (initializedComponents != null) {
			this.initializedComponents = null;
			synchronized (initializedComponents) {
				while (!initializedComponents.isEmpty()) {
					final var component = initializedComponents.pop();
					LOG.fine("before destroy " + component);
					error = IuException.suppress(error, component::close);
				}
			}
		}

		final var support = this.support;
		if (support != null) {
			this.support = null;
			LOG.fine("before destroy support " + support.getModuleLayer());
			error = IuException.suppress(error, support::close);
		}

		final var base = this.base;
		if (base != null) {
			this.base = null;
			LOG.fine("before destroy base " + base.getModuleLayer());
			error = IuException.suppress(error, base::close);
		}

		for (var i = 0; i < archives.length; i++)
			if (archives[i] != null) {
				error = IuException.suppress(error, archives[i]::close);
				archives[i] = null;
			}

		if (error != null)
			throw IuException.checked(error);
		else if (!closed) {
			closed = true;
			LOG.fine("after destroy container");
		}
	}

}
