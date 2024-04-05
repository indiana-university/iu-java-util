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
package edu.iu.type.loader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;
import iu.type.loader.LoadedComponent;

/**
 * Loads components via IU type introspection into an isolated modular
 * environment, without corrupting the application class path.
 */
public class IuComponentLoader implements AutoCloseable {

	private static final Module IU_BASE = IuObject.class.getModule();
	private static final boolean IU_BASE_IS_NAMED = IU_BASE.isNamed();
	private static final Module IU_TYPE_BASE = ModularClassLoader.class.getModule();
	private static final boolean IU_TYPE_BASE_IS_NAMED = IU_TYPE_BASE.isNamed();

	private static final URL[] TYPE_BUNDLE_MODULE_PATH = IuException.unchecked(() -> {
		final var loader = IuComponentLoader.class.getClassLoader();
		final Queue<URL> path = new ArrayDeque<>();
		if (!IU_BASE_IS_NAMED)
			path.offer(Objects.requireNonNull(loader.getResource("iu-java-base.jar")));
		if (!IU_TYPE_BASE_IS_NAMED)
			path.offer(Objects.requireNonNull(loader.getResource("iu-java-type-base.jar")));
		path.offer(Objects.requireNonNull(loader.getResource("iu-java-type.jar")));
		path.offer(Objects.requireNonNull(loader.getResource("iu-java-type-api.jar")));
		return path.toArray(size -> new URL[size]);
	});

	private static Iterable<Supplier<Path>> toModulePath(Iterable<InputStream> modules) {
		return IuIterable.cat( //
				IuIterable.map(IuIterable.iter(TYPE_BUNDLE_MODULE_PATH), a -> () -> TemporaryFile.of(a)),
				IuIterable.map(modules, a -> () -> TemporaryFile.of(a)));
	}

	private volatile ModularClassLoader loader;
	private volatile Module typeImplModule;

	/**
	 * Constructor.
	 * 
	 * @param typeConsumerModules Iterates modules that <em>may</em> require
	 *                            iu.util.type to extend type introspection
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public IuComponentLoader(Iterable<InputStream> typeConsumerModules) {
		this(IuObject.class.getClassLoader(),
				Objects.requireNonNullElseGet(IuObject.class.getModule().getLayer(), ModuleLayer::boot),
				typeConsumerModules, c -> {
				});
	}

	/**
	 * Constructor.
	 * 
	 * @param parent              parent {@link ClassLoader}
	 * @param parentLayer         parent {@link ModuleLayer}; <em>should<em> contain
	 *                            the iu.util and iu.util.type.loader modules
	 * @param typeConsumerModules Iterates modules that <em>may</em> require
	 *                            iu.util.type to extend type introspection
	 * @param controllerCallback  accepts a {@link Controller} for the
	 *                            {@link ModuleLayer} that includes iu.util.type and
	 *                            all modules defined by typeConsumerModules
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public IuComponentLoader(ClassLoader parent, ModuleLayer parentLayer, Iterable<InputStream> typeConsumerModules,
			Consumer<Controller> controllerCallback) {
		loader = ModularClassLoader.of(parent, parentLayer, () -> toModulePath(typeConsumerModules),
				controllerCallback);

		typeImplModule = IuException.unchecked(() -> {
			final var typeBundle = loader.loadClass("edu.iu.type.bundle.IuTypeBundle");
			final var getModule = typeBundle.getMethod("getModule");
			return (Module) getModule.invoke(null);
		});
	}

	public IuLoadedComponent load() {
		final var loadedComponent = new LoadedComponent();
		return loadedComponent;
//			
//			box.component = (AutoCloseable) of.invoke(null, typeBundleLoader.getModuleLayer(),
//					typeBundleLoader, (BiConsumer<Module, Controller>) (module, controller) -> {
//		box.component = (AutoCloseable) of.invoke(null, typeBundleLoader.getModuleLayer(),
//				typeBundleLoader, (BiConsumer<Module, Controller>) (module, controller) -> {
//					if (controllerCallback != null)
//						controllerCallback.accept(new ComponentController(module, controller));
//				}, componentArchiveSource, providedDependencyArchiveSources);
//		box.loader = (ClassLoader) classLoader.invoke(box.component);

	}

	/**
	 * Gets the component's {@link ClassLoader}.
	 * 
	 * @return {@link ClassLoader}
	 */
	public ModularClassLoader getLoader() {
		if (loader == null)
			throw new IllegalStateException("closed");
		else
			return loader;
	}

	@Override
	public synchronized void close() throws Exception {
		if (typeImplModule != null)
			typeImplModule = null;

		if (loader != null) {
			loader.close();
			loader = null;
		}
	}

}
