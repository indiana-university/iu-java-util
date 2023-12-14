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
package iu.type.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuStream;
import edu.iu.type.IuComponent;
import edu.iu.type.IuType;
import edu.iu.type.base.FilteringClassLoader;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;
import edu.iu.type.base.TemporaryFile.IORunnable;
import edu.iu.type.bundle.IuTypeBundle;
import edu.iu.type.spi.IuTypeSpi;

/**
 * Service provider implementation.
 * 
 * @see IuTypeSpi
 */
public class TypeBundleSpi implements IuTypeSpi, AutoCloseable {

	private static final ClassLoader TYPE_SPI_LOADER = IuTypeSpi.class.getClassLoader();
	private static final ModuleLayer TYPE_SPI_LAYER = IuTypeSpi.class.getModule().getLayer();
	private static TypeBundleSpi instance;

	/**
	 * Implementation hook for {@link IuTypeBundle#getModule}.
	 * 
	 * @return {@link Module}
	 */
	public static Module getModule() {
		return instance.delegate.getClass().getModule();
	}

	/**
	 * Implementation hook for {@link IuTypeBundle#shutdown()}.
	 * 
	 * @throws Exception from {@link #close()}
	 */
	public static void shutdown() throws Exception {
		if (instance != null)
			instance.close();
	}

	private IuTypeSpi delegate;
	private final ModularClassLoader bundleLoader;
	private final IORunnable destroy;

	/**
	 * Target constructor for {@link ServiceLoader}.
	 * 
	 * @throws IOException if an error occurs establishing a module path for the
	 *                     bundle
	 */
	public TypeBundleSpi() throws IOException {
		assert instance == null;

		final Deque<Path> libs = new ArrayDeque<>();
		destroy = TemporaryFile.init(() -> {
			final var bundle = TypeBundleSpi.class.getClassLoader().getResource("iu-java-type-impl-bundle.jar");
			final var bundleConnection = bundle.openConnection();
			bundleConnection.setUseCaches(false);
			try (final var in = bundleConnection.getInputStream(); //
					final var bundleJar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = bundleJar.getNextJarEntry()) != null) {
					final var name = entry.getName();
					if (name.endsWith(".jar")) {
						final var lib = name.startsWith("lib/");
						final var bundledLib = TemporaryFile.init(path -> {
							try (final var out = Files.newOutputStream(path)) {
								IuStream.copy(bundleJar, out);
							}
							return path;
						});
						if (lib)
							libs.offer(bundledLib);
						else
							libs.offerFirst(bundledLib);
					}

					bundleJar.closeEntry();
				}
			}
		});

		class Box {
			ModularClassLoader bundleLoader;
			IuTypeSpi delegate;
		}
		final var box = new Box();

		try {
			IuException.checked(IOException.class,
					() -> IuException.initialize(
							new ModularClassLoader(false, libs, TYPE_SPI_LAYER,
									new FilteringClassLoader(IuIterable.iter("edu.iu", "edu.iu.type",
											"edu.iu.type.base", "edu.iu.type.spi"), TYPE_SPI_LOADER),
									null),
							bundleLoader -> {
								box.bundleLoader = bundleLoader;
								box.delegate = ServiceLoader.load(IuTypeSpi.class, bundleLoader).iterator().next();
								return null;
							}));
		} catch (Throwable e) {
			IuException.suppress(e, destroy);
			throw e;
		}

		this.bundleLoader = box.bundleLoader;
		this.delegate = box.delegate;

		instance = this;
	}

	@Override
	public IuType<?, ?> resolveType(Type type) {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.resolveType(type);
	}

	@Override
	public IuComponent createComponent(ClassLoader parent, BiConsumer<Module, Controller> controllerCallback,
			InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources) throws IOException {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.createComponent(parent, controllerCallback, componentArchiveSource,
				providedDependencyArchiveSources);
	}

	@Override
	public IuComponent scanComponentEntry(ClassLoader classLoader, Path pathEntry)
			throws IOException, ClassNotFoundException {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.scanComponentEntry(classLoader, pathEntry);
	}

	@Override
	public synchronized void close() throws Exception {
		instance = null;

		final var delegate = this.delegate;
		if (delegate != null) {
			this.delegate = null;
			IuException.checked(() -> IuException.suppress(bundleLoader::close, destroy));
		}
	}

}
