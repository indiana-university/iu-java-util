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
package iu.type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ModuleLayer.Controller;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.CloseableModuleFinder;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;

/**
 * Creates instances of {@link Component} for
 * {@link TypeSpi#createComponent(ClassLoader, ModuleLayer, Consumer, InputStream, InputStream...)}.
 */
final class ComponentFactory {

	private ComponentFactory() {
	}

	/**
	 * Checks a archive version against another archive to verify two don't have the
	 * same version name.
	 * 
	 * <p>
	 * Deletes the archive temp file before throwing
	 * {@link IllegalArgumentException} if the version names match.
	 * 
	 * @param alreadyProvidedArchive archive already in the component path
	 * @param archive                archve to verify before adding to the component
	 *                               path.
	 */
	static void checkIfAlreadyProvided(ComponentArchive alreadyProvidedArchive, ComponentArchive archive) {
		if (alreadyProvidedArchive.version().name().equals(archive.version().name())) {
			var illegalArgumentException = new IllegalArgumentException(
					archive.version() + " was already provided by " + alreadyProvidedArchive.version());

			try {
				Files.delete(archive.path());
			} catch (Throwable deleteFailure) {
				illegalArgumentException.addSuppressed(deleteFailure);
			}

			throw illegalArgumentException;
		}
	}

	/**
	 * Creates a modular component.
	 * 
	 * @param parent             parent component
	 * @param parentLoader       {@link ClassLoader} for parent delegation
	 * @param parentLayer        {@link ModuleLayer} to extend
	 * @param archives           component path
	 * @param controllerCallback receives a reference to the {@link Controller} for
	 *                           the component's module layer
	 * @param destroy            thunk for final cleanup after closing the component
	 * @return module component
	 * @throws IOException If an I/O error occurs reading from an archive
	 */
	static Component createModular(Component parent, ClassLoader parentLoader, ModuleLayer parentLayer,
			Iterable<ComponentArchive> archives, Consumer<Controller> controllerCallback, UnsafeRunnable destroy)
			throws IOException {
		final var firstComponent = archives.iterator().next();
		final String firstModuleName;
		try (final var finder = new CloseableModuleFinder(firstComponent.path())) {
			firstModuleName = finder.findAll().iterator().next().descriptor().name();
		}

		return IuException.checked(IOException.class,
				() -> IuException.initialize(new ModularClassLoader(firstComponent.kind().isWeb(),
						IuIterable.map(archives, ComponentArchive::path), parentLayer, parentLoader, c -> {

							final var firstModule = c.layer().findModule(firstModuleName).get();
							firstModule.getPackages()
									.forEach(p -> c.addOpens(firstModule, p, ComponentFactory.class.getModule()));

							if (controllerCallback != null)
								controllerCallback.accept(c);

						}), loader -> new Component(parent, loader, loader.getModuleLayer(), archives,
								() -> IuException.suppress(loader::close, destroy))));
	}

	/**
	 * Creates a modular component.
	 * 
	 * @param parent       parent component
	 * @param parentLoader {@link ClassLoader} for parent delegation
	 * @param archives     component path
	 * @param destroy      thunk for final cleanup after closing the component
	 * @return module component
	 * @throws IOException If an I/O error occurs reading from an archive
	 */
	static Component createLegacy(Component parent, ClassLoader parentLoader, Queue<ComponentArchive> archives,
			UnsafeRunnable destroy) throws IOException {
		var path = new URL[archives.size()];
		{
			var i = 0;
			for (var archive : archives)
				path[i++] = archive.path().toUri().toURL();
		}

		final var loader = new LegacyClassLoader(archives.iterator().next().kind().isWeb(), path, parentLoader);

		return new Component(parent, loader, null, archives, () -> IuException.suppress(loader::close, destroy));
	}

	/**
	 * Creates a component from the source queue.
	 * 
	 * @param parent             parent component
	 * @param parentLoader       {@link ClassLoader} for parent delegation
	 * @param parentLayer        {@link ModuleLayer} to extend
	 * @param controllerCallback receives a reference to {@link Controller} for the
	 *                           component's module layer
	 * @param sources            source queue; will be drained and all entries
	 *                           closed when the component is closed, or if an
	 *                           initialization error occurs.
	 * @return fully loaded component instance
	 * @throws IOException If an I/O error occurs reaching from an archive source
	 */
	static Component createFromSourceQueue(Component parent, ClassLoader parentLoader, ModuleLayer parentLayer,
			Consumer<Controller> controllerCallback, Queue<ArchiveSource> sources) throws IOException {
		Queue<ComponentArchive> archives = new ArrayDeque<>();
		Queue<ComponentVersion> unmetDependencies = new ArrayDeque<>();

		final var destroy = TemporaryFile.init(() -> {
			while (!sources.isEmpty())
				try (var source = sources.poll()) {
					dep: for (var sourceDependency : source.dependencies()) {
						if (parent != null)
							for (var version : parent.versions())
								if (version.meets(sourceDependency))
									continue dep;
						for (var archive : archives)
							if (archive.version().meets(sourceDependency))
								continue dep;
						unmetDependencies.add(sourceDependency);
					}

					var archive = ComponentArchive.from(source);
					for (var alreadyProvidedArchive : archives)
						checkIfAlreadyProvided(alreadyProvidedArchive, archive);

					var unmetDependencyIterator = unmetDependencies.iterator();
					while (unmetDependencyIterator.hasNext()) {
						final var unmetDependency = unmetDependencyIterator.next();
						if (archive.version().meets(unmetDependency))
							unmetDependencyIterator.remove();
					}
					archives.offer(archive);

					for (var bundledDependency : archive.bundledDependencies())
						sources.offer(bundledDependency);
				}

			if (!unmetDependencies.isEmpty())
				throw new IllegalArgumentException("Not all depdendencies were met, missing " + unmetDependencies);

		});

		try {
			var kind = archives.iterator().next().kind();
			if (kind.isModular())
				return createModular(parent, parentLoader, parentLayer, archives, controllerCallback, destroy);
			else
				return createLegacy(parent, parentLoader, archives, destroy);
		} catch (Throwable e) {
			IuException.suppress(e, destroy);
			throw e;
		}
	}

	/**
	 * Creates a component from the source inputs
	 * 
	 * @param parent                           parent component
	 * @param parentLoader                     {@link ClassLoader} for parent
	 *                                         delegation
	 * @param parentLayer                      {@link ModuleLayer} to extend
	 * @param controllerCallback               receives a reference to the
	 *                                         {@link Controller} for the
	 *                                         component's {@link ModuleLayer}
	 * @param componentArchiveSource           component archive source input
	 * @param providedDependencyArchiveSources dependency source inputs
	 * @return fully loaded component instance
	 */
	static Component createComponent(Component parent, ClassLoader parentLoader, ModuleLayer parentLayer,
			Consumer<Controller> controllerCallback, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) {

		Queue<ArchiveSource> sources = new ArrayDeque<>();
		Throwable thrown = null;
		try {
			sources.offer(new ArchiveSource(componentArchiveSource));
			for (var providedDependencyArchiveSource : providedDependencyArchiveSources)
				sources.offer(new ArchiveSource(providedDependencyArchiveSource));

			return createFromSourceQueue(parent, parentLoader, parentLayer, controllerCallback, sources);

		} catch (Throwable e) {
			thrown = e;
			throw IuException.unchecked(e);
		} finally {
			final var throwing = thrown != null;
			while (!sources.isEmpty())
				thrown = IuException.suppress(thrown, sources.poll()::close);

			if (!throwing && thrown != null)
				throw IuException.unchecked(thrown);
		}
	}

}
