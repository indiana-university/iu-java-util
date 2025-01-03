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
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.UnsafeRunnable;
import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceKey;
import edu.iu.type.IuResourceReference;
import edu.iu.type.base.FilteringClassLoader;
import edu.iu.type.base.ModularClassLoader;

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
	 * Initializes components.
	 * 
	 * @param componentsToInitialize Created components that have not yet been
	 *                               initialized
	 * @throws Throwable If an error occurs
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static void initializeComponents(Iterable<IuComponent> componentsToInitialize) throws Throwable {
		final Map<IuResourceKey<?>, IuResource<?>> boundResources = new LinkedHashMap<>();
		final List<TypeContainerResource> containerResources = new ArrayList<>();
		for (final var component : componentsToInitialize)
			for (final var resource : component.resources()) {
				IuObject.require(boundResources.put(IuResourceKey.from(resource), resource), Objects::isNull,
						"already bound " + resource);
				containerResources.add(new TypeContainerResource(resource, component));
				LOG.fine(() -> "after create " + resource);
			}

		final Map<IuResourceKey<?>, Queue<IuResourceReference<?, ?>>> resourceReferences = new LinkedHashMap<>();
		for (final var component : componentsToInitialize)
			for (final var resourceRef : component.resourceReferences()) {
				final var key = IuResourceKey.from(resourceRef);
				final IuResource resource = Objects.requireNonNull(boundResources.get(key),
						"Missing resource binding " + key);
				final var resourceReferenceQueue = resourceReferences.computeIfAbsent(key, k -> new ArrayDeque<>());
				resourceReferenceQueue.add(resourceRef);
				resourceRef.bind(resource);
				LOG.fine(() -> "bind " + resourceRef + " " + resource);
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
