/*
 * Copyright © 2026 Indiana University
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
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.type.IuComponent;
import edu.iu.type.IuType;
import edu.iu.type.base.FilteringClassLoader;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;
import edu.iu.type.bundle.IuTypeBundle;
import edu.iu.type.spi.IuTypeSpi;

/**
 * Service provider implementation.
 * 
 * @see IuTypeSpi
 */
public class TypeBundleSpi implements IuTypeSpi, AutoCloseable {
	static {
		IuObject.assertNotOpen(TypeBundleSpi.class);
	}

	private static final ClassLoader TYPE_SPI_LOADER = IuTypeSpi.class.getClassLoader();
	private static final ModuleLayer TYPE_SPI_LAYER = Objects.requireNonNull(IuTypeSpi.class.getModule().getLayer());
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

	private volatile ModularClassLoader bundleLoader;
	private volatile IuTypeSpi delegate;

	/**
	 * Target constructor for {@link ServiceLoader}.
	 * 
	 * @throws IOException if an error occurs establishing a module path for the
	 *                     bundle
	 */
	public TypeBundleSpi() throws IOException {
		assert instance == null;

		bundleLoader = ModularClassLoader.of(
				new FilteringClassLoader(IuIterable
						.iter("edu.iu", "edu.iu.type", "edu.iu.type.base", "edu.iu.type.spi"), TYPE_SPI_LOADER),
				TYPE_SPI_LAYER,
				() -> TemporaryFile.readBundle(Objects.requireNonNull(
						TypeBundleSpi.class.getClassLoader().getResource("iu-java-type-impl-bundle.jar"),
						"Missing iu-java-type-impl-bundle.jar classpath entry")),
				c -> {
					final var iuTypeBase = ModularClassLoader.class.getModule();
					c.addReads(c.layer().findModule("iu.util.type.impl").get(), iuTypeBase);
				});

		delegate = IuException.checked(IOException.class, () -> IuException.initialize(bundleLoader,
				a -> ServiceLoader.load(IuTypeSpi.class, a).findFirst().get()));

		instance = this;
	}

	@Override
	public IuType<?, ?> resolveType(Type type) {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.resolveType(type);
	}

	@Override
	public IuComponent scanComponentEntry(ClassLoader classLoader, ModuleLayer moduleLayer, Path pathEntry)
			throws IOException, ClassNotFoundException {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.scanComponentEntry(classLoader, moduleLayer, pathEntry);
	}

	@Override
	public IuComponent createComponent(ClassLoader parent, ModuleLayer parentLayer,
			Consumer<Controller> controllerCallback, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.createComponent(parent, parentLayer, controllerCallback, componentArchiveSource,
				providedDependencyArchiveSources);
	}

	@Override
	public synchronized void close() throws Exception {
		instance = null;

		this.delegate = null;

		if (bundleLoader != null) {
			final var bundleLoader = this.bundleLoader;
			this.bundleLoader = null;
			bundleLoader.close();
		}
	}

}
