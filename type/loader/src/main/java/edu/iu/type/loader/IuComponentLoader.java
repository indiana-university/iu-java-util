/*
 * Copyright © 2023 Indiana University
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuStream;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.FilteringClassLoader;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;

/**
 * Loads components via IU type introspection into an isolated modular
 * environment, without corrupting the application class path.
 */
public class IuComponentLoader implements AutoCloseable {

	private static final URL[] TYPE_BUNDLE_MODULE_PATH = IuException.unchecked(() -> {
		final var loader = IuComponentLoader.class.getClassLoader();
		return new URL[] { //
				Objects.requireNonNull(loader.getResource("iu-java-type.jar")), //
				Objects.requireNonNull(loader.getResource("iu-java-base.jar")), //
				Objects.requireNonNull(loader.getResource("iu-java-type-api.jar")), //
				Objects.requireNonNull(loader.getResource("iu-java-type-base.jar")), //
		};
	});

	private final UnsafeRunnable destroy;
	private final ModularClassLoader typeBundleLoader;
	private final AutoCloseable component;
	private final ClassLoader loader;

	/**
	 * Validates a <strong>component archive</strong>, all <strong>dependency
	 * archives</strong>, loads a <strong>component</strong>, and returns a
	 * {@link ClassLoader} for accessing classes managed by the component.
	 * 
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archives</strong>.
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public IuComponentLoader(InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources)
			throws IOException {
		this(null, componentArchiveSource, providedDependencyArchiveSources);
	}

	/**
	 * Validates a <strong>component archive</strong>, all <strong>dependency
	 * archives</strong>, loads a <strong>component</strong>, and returns a
	 * {@link ClassLoader} for accessing classes managed by the component.
	 * 
	 * @param controllerCallback               receives a reference to an
	 *                                         {@link IuComponentController} that
	 *                                         may be used to set up access rules
	 *                                         for the component. This reference
	 *                                         <em>should not</em> be passed beyond
	 *                                         the scope of the callback; see
	 *                                         {@link ModularClassLoader}
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archives</strong>.
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public IuComponentLoader(Consumer<IuComponentController> controllerCallback, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException {
		this(IuIterable.empty(), null, controllerCallback, componentArchiveSource, providedDependencyArchiveSources);
	}

	/**
	 * Validates a <strong>component archive</strong>, all <strong>dependency
	 * archives</strong>, loads a <strong>component</strong>, and returns a
	 * {@link ClassLoader} for accessing classes managed by the component.
	 * 
	 * @param allowedPackages                  non-platform classes to allow
	 *                                         delegated access to; see
	 *                                         {@link FilteringClassLoader}
	 * @param parent                           parent {@link ClassLoader} for
	 *                                         delegated access; see
	 *                                         {@link FilteringClassLoader}
	 * @param controllerCallback               receives a reference to an
	 *                                         {@link IuComponentController} that
	 *                                         may be used to set up access rules
	 *                                         for the component. This reference
	 *                                         <em>should not</em> be passed beyond
	 *                                         the scope of the callback; see
	 *                                         {@link ModularClassLoader}
	 * @param componentArchiveSource           {@link InputStream} for reading the
	 *                                         <strong>component archive</strong>.
	 * @param providedDependencyArchiveSources {@link InputStream}s for reading all
	 *                                         <strong>provided dependency
	 *                                         archives</strong>.
	 * @throws IOException If an IO error occurs initializing the component
	 */
	public IuComponentLoader(Iterable<String> allowedPackages, ClassLoader parent,
			Consumer<IuComponentController> controllerCallback, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException {
		class Box {
			ModularClassLoader typeBundleLoader;
			AutoCloseable component;
			ClassLoader loader;
		}
		final var box = new Box();

		final var filteredParent = new FilteringClassLoader(allowedPackages, parent);
		destroy = TemporaryFile.init(() -> {
			final var typeBundleJars = new Path[TYPE_BUNDLE_MODULE_PATH.length];

			for (var i = 0; i < TYPE_BUNDLE_MODULE_PATH.length; i++) {
				final var connection = TYPE_BUNDLE_MODULE_PATH[i].openConnection();
				connection.setUseCaches(false);
				try (final var in = connection.getInputStream()) {
					typeBundleJars[i] = TemporaryFile.init(path -> {
						try (final var out = Files.newOutputStream(path)) {
							IuStream.copy(in, out);
						}
						return path;
					});
				}
			}

			box.typeBundleLoader = IuException.checked(IOException.class, () -> IuException.initialize(
					new ModularClassLoader(false, IuIterable.iter(typeBundleJars), filteredParent, controller -> {
					}), typeBundleLoader -> {
						final var bundleSpi = typeBundleLoader.loadClass("edu.iu.type.spi.IuTypeSpi");
						final var getModule = bundleSpi.getMethod("getModule");
						getModule.setAccessible(true);
						final var typeModule = (Module) getModule.invoke(null);

						final var iuComponent = typeBundleLoader.loadClass("edu.iu.type.IuComponent");
						final var of = iuComponent.getMethod("of", BiConsumer.class, InputStream.class,
								InputStream[].class);
						final var classLoader = iuComponent.getMethod("classLoader");

						class ComponentController implements IuComponentController {
							private final Module componentModule;
							private final Controller controller;

							ComponentController(Module componentModule, Controller controller) {
								this.componentModule = componentModule;
								this.controller = controller;
							}

							@Override
							public Module getTypeModule() {
								return typeModule;
							}

							@Override
							public Module getComponentModule() {
								return componentModule;
							}

							@Override
							public Controller getController() {
								return controller;
							}
						}

						return IuException.checkedInvocation(() -> {
							box.component = (AutoCloseable) of.invoke(null,
									(BiConsumer<Module, Controller>) (module, controller) -> {
										if (controllerCallback != null)
											controllerCallback.accept(new ComponentController(module, controller));
									}, componentArchiveSource, providedDependencyArchiveSources);
							box.loader = (ClassLoader) classLoader.invoke(box.component);
							return typeBundleLoader;
						});
					}));
		});

		typeBundleLoader = box.typeBundleLoader;
		component = box.component;
		loader = box.loader;
	}

	/**
	 * Gets the component's {@link ClassLoader}.
	 * 
	 * @return {@link ClassLoader}
	 */
	public ClassLoader getLoader() {
		return loader;
	}

	@Override
	public void close() throws IOException {
		var closeError = IuException.suppress(null, component::close);
		closeError = IuException.suppress(closeError, typeBundleLoader::close);
		closeError = IuException.suppress(closeError, destroy::run);
		if (closeError != null)
			throw IuException.checked(closeError, IOException.class);
	}

}