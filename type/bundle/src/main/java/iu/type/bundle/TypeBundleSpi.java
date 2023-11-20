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
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuStream;
import edu.iu.type.IuComponent;
import edu.iu.type.IuType;
import edu.iu.type.spi.IuTypeSpi;

/**
 * Service provider implementation.
 * 
 * @see IuTypeSpi
 */
public class TypeBundleSpi implements IuTypeSpi, AutoCloseable {

	private static final Logger LOG = Logger.getLogger(TypeBundleSpi.class.getName());

	private record CloseableDelegate(IuTypeSpi spi, Runnable close) {
	}

	/**
	 * Copies a jar archive from a source stream to a temp file name relative to its
	 * source
	 * 
	 * @param sourceName   visual name of the jar file, typically {@code artifactId}
	 * @param sourceStream input stream for reading raw (jar) file data
	 * @return {@link Path} to a temp file with the raw jar archive, suitable for
	 *         use with an isolated {@link ClassLoader}
	 * 
	 * @throws IOException if the jar data could not be copied
	 */
	static Path copyJarToTempFile(String sourceName, InputStream sourceStream) throws IOException {
		final var target = Files.createTempFile("iu-type-bundle_" + sourceName, ".jar");
		try (final var out = Files.newOutputStream(target, StandardOpenOption.CREATE)) {
			IuStream.copy(sourceStream, out);
		} catch (Throwable e) {
			try {
				Files.deleteIfExists(target);
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw IuException.checked(e, IOException.class);
		}
		return target;
	}

	/**
	 * Closes open resources, then deletes temp files, after releasing all
	 * references to the delegate.
	 * 
	 * <p>
	 * Implements {@link #close()}
	 * </p>
	 * 
	 * @param openResources open resources
	 * @param tempFiles     temp files
	 */
	static void cleanUp(AutoCloseable openResources, Iterable<Path> tempFiles) {
		try {
			openResources.close();
		} catch (Throwable e) {
			LOG.log(Level.WARNING, e, () -> "Failed to clean up resources");
		}

		for (final var temp : tempFiles)
			try {
				Files.deleteIfExists(temp);
			} catch (Throwable e) {
				LOG.log(Level.WARNING, e, () -> "Failed to clean up temp file " + temp);
			}
	}

	private static final CloseableDelegate createDelegate() throws IOException {
		final Deque<Path> libs = new ArrayDeque<>();

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
					final var entryName = name.substring(lib ? 4 : 0, name.length() - 4);
					final var bundledLib = copyJarToTempFile(entryName, bundleJar);
					if (lib)
						libs.offer(bundledLib);
					else
						libs.offerFirst(bundledLib);
				}

				bundleJar.closeEntry();
			}
		}

		final var moduleFinder = new BundleModuleFinder(libs.toArray(new Path[libs.size()]));
		final Runnable close = () -> cleanUp(moduleFinder, libs);
		try {
			final Queue<String> moduleNames = new ArrayDeque<>();
			moduleNames.add("iu.util.type");
			for (final var moduleRefence : moduleFinder.findAll())
				moduleNames.offer(moduleRefence.descriptor().name());

			final var spiModule = IuTypeSpi.class.getModule();
			final var parentModuleLayer = spiModule.getLayer();
			final var configuration = Configuration.resolveAndBind( //
					ModuleFinder.of(), List.of(parentModuleLayer.configuration()), moduleFinder, moduleNames);
			final var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(parentModuleLayer),
					parentModuleLayer.findLoader("iu.util.type"));

			final var layer = controller.layer();

			return new CloseableDelegate(
					ServiceLoader.load(IuTypeSpi.class, layer.findLoader("iu.util.type.impl")).iterator().next(),
					close);

		} catch (RuntimeException | Error e) {
			try {
				close.run();
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
	}

	private CloseableDelegate delegate = createDelegate();

	/**
	 * Target constructor for {@link ServiceLoader}.
	 * 
	 * @throws IOException if an error occurs establishing a module path for the
	 *                     bundle
	 */
	public TypeBundleSpi() throws IOException {
	}

	@Override
	public IuType<?, ?> resolveType(Type type) {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.spi.resolveType(type);
	}

	@Override
	public IuComponent createComponent(InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.spi.createComponent(componentArchiveSource, providedDependencyArchiveSources);
	}

	@Override
	public IuComponent scanComponentEntry(ClassLoader classLoader, Path pathEntry)
			throws IOException, ClassNotFoundException {
		if (delegate == null)
			throw new IllegalStateException("closed");
		return delegate.spi.scanComponentEntry(classLoader, pathEntry);
	}

	@Override
	public synchronized void close() {
		final var delegate = this.delegate;
		if (delegate != null) {
			this.delegate = null;
			delegate.close.run();
		}
	}

}
