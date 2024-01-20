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
package edu.iu.type.base;

import java.io.IOException;
import java.lang.ModuleLayer.Controller;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuStream;

/**
 * Module-aware {@link ClassLoader} implementation.
 */
public class ModularClassLoader extends ClassLoader implements AutoCloseable {

	private final boolean web;
	private final CloseableModuleFinder moduleFinder;
	private final ModuleLayer moduleLayer;
	private final Map<String, byte[]> classData;
	private final Map<String, List<URL>> resourceUrls;
	
	/**
	 * Constructor.
	 * 
	 * @param web                true for <a href=
	 *                           "https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0#web-application-class-loader">web
	 *                           classloading semantics</a>; false for normal parent
	 *                           delegation semantics
	 * @param path               class/module path
	 * @param parentLayer        parent module layer
	 * @param parent             parent class loader
	 * @param controllerCallback receives a reference to the {@link Controller} for
	 *                           the module layer created in conjunction with this
	 *                           loader. API Note from {@link Controller}: <em>Care
	 *                           should be taken with Controller objects, they
	 *                           should never be shared with untrusted code.</em>
	 * @throws IOException if an error occurs reading a class path entry
	 */
	public ModularClassLoader(boolean web, Iterable<Path> path, ModuleLayer parentLayer, ClassLoader parent,
			Consumer<Controller> controllerCallback) throws IOException {
		super(parent);
		registerAsParallelCapable();

		final Queue<Path> classpath = new ArrayDeque<>();
		final Queue<Path> modulepath = new ArrayDeque<>();
		for (final var pathEntry : path) {
			boolean hasModuleInfo = false;
			try (final var in = Files.newInputStream(pathEntry); //
					final var jar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null)
					if (entry.getName().equals("module-info.class")) {
						hasModuleInfo = true;
						break;
					}
			}
			if (hasModuleInfo)
				modulepath.offer(pathEntry);
			else
				classpath.offer(pathEntry);
		}

		this.web = web;
		classData = new LinkedHashMap<>();
		resourceUrls = new LinkedHashMap<>();

		for (final var classpathEntry : classpath) {
			final var resourceRootUrl = "jar:" + classpathEntry.toUri() + "!/";

			try (final var in = Files.newInputStream(classpathEntry); //
					final var jar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null) {
					final var name = entry.getName();
					var resourceList = resourceUrls.get(name);
					if (resourceList == null)
						resourceUrls.put(name, resourceList = new ArrayList<>());
					resourceList.add(new URL(resourceRootUrl + name));

					if (name.endsWith(".class"))
						classData.put(name.substring(0, name.length() - 6).replace('/', '.'), IuStream.read(jar));

					jar.closeEntry();
				}
			}
		}

		class Box {
			CloseableModuleFinder moduleFinder;
			ModuleLayer moduleLayer;
		}
		final var box = new Box();
		IuException.checked(IOException.class,
				() -> IuException.initialize(new CloseableModuleFinder(modulepath.toArray(new Path[modulepath.size()])),
						moduleFinder -> {
							box.moduleFinder = moduleFinder;

							final Collection<String> moduleNames = new ArrayDeque<>();
							for (final var moduleRef : moduleFinder.findAll())
								moduleNames.add(moduleRef.descriptor().name());

							final var configuration = Configuration.resolveAndBind( //
									moduleFinder, List.of(parentLayer.configuration()), ModuleFinder.of(), moduleNames);

							final var controller = ModuleLayer.defineModules(configuration, List.of(parentLayer),
									a -> this);
							box.moduleLayer = controller.layer();
							if (controllerCallback != null)
								controllerCallback.accept(controller);
							return null;
						}));
		this.moduleFinder = box.moduleFinder;
		this.moduleLayer = box.moduleLayer;
	}

	/**
	 * Gets the module layer associated with this class loader.
	 * 
	 * @return {@link ModuleLayer}
	 */
	public ModuleLayer getModuleLayer() {
		return moduleLayer;
	}

	@Override
	public void close() throws IOException {
		moduleFinder.close();
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if (!web || IuObject.isPlatformName(name))
			return super.loadClass(name, resolve);

		synchronized (getClassLoadingLock(name)) {
			Class<?> rv = this.findLoadedClass(name);
			if (rv != null)
				return rv;

			try {
				rv = findClass(name);
				if (resolve)
					resolveClass(rv);
				return rv;
			} catch (ClassNotFoundException e) {
				// will attempt throw again when called from
				// super.loadClass if also not found in parent
			}

			return super.loadClass(name, resolve);
		}
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		final var classData = this.classData.get(name);
		if (classData == null) {
			final var resourceName = name.replace('.', '/') + ".class";

			for (final var moduleRef : moduleFinder.findAll()) {
				final var classFromModule = IuException.unchecked(() -> {
					final var resource = moduleRef.open().read(resourceName);
					if (resource.isEmpty())
						return (Class<?>) null;

					final var moduleClassData = resource.get();
					return defineClass(name, moduleClassData, null);
				});

				if (classFromModule != null)
					return classFromModule;
			}

			var parentLoader = getParent();
			if (parentLoader == null)
				parentLoader = ClassLoader.getPlatformClassLoader();

			final var parentClass = parentLoader.loadClass(name);
			if (parentClass.getModule().isNamed())
				return parentClass;
			else
				throw new ClassNotFoundException(name);

		} else
			return defineClass(name, classData, 0, classData.length);
	}

	@Override
	protected Class<?> findClass(String moduleName, String name) {
		final var moduleRef = moduleFinder.find(moduleName);
		if (moduleRef.isEmpty())
			return null;

		final var resourceName = name.replace('.', '/') + ".class";
		return IuException.unchecked(() -> {
			final var resource = moduleRef.get().open().read(resourceName);
			if (resource.isEmpty())
				return null;

			return defineClass(name, resource.get(), null);
		});
	}

	@Override
	protected URL findResource(String moduleName, String name) throws IOException {
		if (moduleName == null) {
			final var classpathResources = resourceUrls.get(name);
			if (classpathResources != null)
				return classpathResources.get(0);
			else
				return null;
		}

		final var moduleRef = moduleFinder.find(moduleName);
		if (moduleRef.isEmpty())
			return null;

		final var resource = moduleRef.get().open().find(name);
		if (resource.isEmpty())
			return null;
		else
			return resource.get().toURL();
	}

	@Override
	protected URL findResource(String name) {
		class Box implements BiPredicate<ModuleReference, URL> {
			ModuleReference moduleReference;
			URL resource;

			@Override
			public boolean test(ModuleReference moduleReference, URL resource) {
				this.moduleReference = moduleReference;
				this.resource = resource;
				return true;
			}
		}
		final var box = new Box();
		findResource(name, box);

		if (box.moduleReference == null //
				|| isOpen(moduleLayer.findModule(box.moduleReference.descriptor().name()).get(), name))
			return box.resource;
		else
			return null;

	}

	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		final Queue<URL> resources = new ArrayDeque<>();
		findResource(name, (moduleRef, resource) -> {
			if (moduleRef == null //
					|| isOpen(moduleLayer.findModule(moduleRef.descriptor().name()).get(), name))
				resources.offer(resource);
			return false;
		});

		final var i = resources.iterator();
		return new Enumeration<URL>() {
			@Override
			public boolean hasMoreElements() {
				return i.hasNext();
			}

			@Override
			public URL nextElement() {
				return i.next();
			}
		};
	}

	/**
	 * Finds module and resource URL for resources available from this class loader.
	 * 
	 * @param name              resource name
	 * @param resourcePredicate receives a reference to the module and resource URL
	 *                          for each instance of the resource found; returns
	 *                          true to terminate the loop after inspecting the
	 *                          reference, false to keep searching
	 */
	void findResource(String name, BiPredicate<ModuleReference, URL> resourcePredicate) {
		for (final var moduleRef : moduleFinder.findAll()) {
			final var optionalResource = IuException.unchecked(() -> moduleRef.open().find(name));
			if (optionalResource.isPresent())
				if (resourcePredicate.test(moduleRef, IuException.unchecked(optionalResource.get()::toURL)))
					return;
		}

		final var classpathResources = resourceUrls.get(name);
		if (classpathResources != null)
			for (URL classpathResource : classpathResources)
				if (resourcePredicate.test(null, classpathResource))
					return;
	}

	/**
	 * Helper fragment for {@link #findResource(String)} and
	 * {@link #findResources(String)}.
	 * 
	 * @param module module that contains the resource
	 * @param name   resource name
	 * 
	 * @return true if the resource is either not encapsulated or is in a package
	 *         that is unconditionally open
	 * 
	 * @see #findResource(String)
	 * @see #findResources(String)
	 * @see Module#getResourceAsStream(String)
	 */
	boolean isOpen(Module module, String name) {
		// + A resource in a named module may be encapsulated ...
		if (module == null)
			return true; // not in a module

		// ... so that it cannot be located by code in other modules.
		// + Whether a resource can be located or not is determined as follows:
		// + If the resource name ends with ".class" then it is not encapsulated.
		if (name.endsWith(".class"))
			return true; // not encapsulated

		final var lastSlash = name.lastIndexOf('/');
		/*
		 * + If the resource is not in a package (no slash in resource name) in the
		 * module then the resource is not encapsulated.
		 */
		// + "META-INF" is not a legal package name
		if (lastSlash <= 0 || name.startsWith("META-INF/"))
			return true;

		// + A leading slash is ignored when deriving the package name.
		final var startOfPackageName = name.charAt(0) == '/' ? 1 : 0;

		/*
		 * + A package name is derived from the subsequence of characters that precedes
		 * the last '/' in the name and then replacing each '/' character in the
		 * subsequence with '.'.
		 */
		final String packageName = name.substring(startOfPackageName, lastSlash).replace('/', '.');

		// + If the package name is a package in the module ...
		if (!module.isExported(packageName))
			return false;

		/*
		 * ... then the resource can only be located by the caller of this method when
		 * the package is open to at least the caller's module ... + additionally, it
		 * must not find non-".class" resources in packages of named modules unless the
		 * package is opened unconditionally.
		 */
		// ==> the caller's module is not important: .class is not encapsulated, others
		// must be in an unconditionally open package
		if (module.isOpen(packageName))
			return true;

		return false;
	}

}
