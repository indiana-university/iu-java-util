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
package iu.type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import edu.iu.type.IuComponent;

final class ComponentFactory {

	private ComponentFactory() {
	}

	static IuComponent createModular(Component parent, Queue<ComponentArchive> archives) {
		var path = new Path[archives.size()];
		{
			var i = 0;
			for (var archive : archives)
				path[i++] = archive.path();
		}

		var moduleFinder = new ComponentModuleFinder(path);
		try {
			var moduleNames = moduleFinder.findAll().stream().map(ref -> ref.descriptor().name())
					.collect(Collectors.toList());

			ClassLoader parentClassLoader;
			ModuleLayer parentModuleLayer;
			if (parent == null) {
				parentClassLoader = null;
				parentModuleLayer = ModuleLayer.boot();
			} else {
				parentClassLoader = parent.classLoader();
				parentModuleLayer = parent.controller().layer();
			}

			var configuration = Configuration.resolveAndBind( //
					moduleFinder, List.of(parentModuleLayer.configuration()), ModuleFinder.of(), moduleNames);

			var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(parentModuleLayer),
					parentClassLoader);

			return new Component(parent, controller, controller.layer().findLoader(moduleNames.iterator().next()),
					moduleFinder, archives);

		} catch (RuntimeException | Error e) {
			try {
				moduleFinder.close();
			} catch (Throwable e2) {
				e.addSuppressed(e2);
			}
			throw e;
		}
	}

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

	static IuComponent createFromSourceQueue(Component parent, Queue<ArchiveSource> sources) throws IOException {
		Queue<ComponentArchive> archives = new ArrayDeque<>();
		Queue<ComponentVersion> unmetDependencies = new ArrayDeque<>();

		try {
			while (!sources.isEmpty())
				try (var source = sources.poll()) {
					dep: for (var sourceDependency : source.dependencies()) {
						for (var archive : archives)
							if (archive.version().meets(sourceDependency))
								continue dep;
						unmetDependencies.add(sourceDependency);
					}

					var archive = ComponentArchive.from(source);
					for (var alreadyProvidedArchive : archives)
						checkIfAlreadyProvided(alreadyProvidedArchive, archive);

					var unmetDependencyIterator = unmetDependencies.iterator();
					while (unmetDependencyIterator.hasNext())
						if (archive.version().meets(unmetDependencyIterator.next()))
							unmetDependencies.remove();
					archives.offer(archive);

					for (var bundledDependency : archive.bundledDependencies())
						sources.offer(bundledDependency);
				}

			if (!unmetDependencies.isEmpty())
				throw new IllegalArgumentException("Not all depdendencies were met, missing " + unmetDependencies);

			var kind = archives.iterator().next().kind();
			if (kind.isModular())
				return createModular(parent, archives);
			else
				// TODO Auto-generated method stub
				throw new UnsupportedOperationException("TODO");

		} catch (IOException | RuntimeException | Error e) {
			while (!archives.isEmpty())
				try {
					Files.delete(archives.poll().path());
				} catch (Throwable e2) {
					e.addSuppressed(e2);
				}
			throw e;
		}
	}

	static IuComponent createComponent(Component parent, InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException {

		Queue<ArchiveSource> sources = new ArrayDeque<>();
		Throwable thrown = null;
		try {
			sources.offer(new ArchiveSource(componentArchiveSource));
			for (var providedDependencyArchiveSource : providedDependencyArchiveSources)
				sources.offer(new ArchiveSource(providedDependencyArchiveSource));

			return createFromSourceQueue(parent, sources);

		} catch (IOException | RuntimeException | Error e) {
			thrown = e;
			throw e;
		} finally {
			final var throwing = thrown != null;
			while (!sources.isEmpty())
				try {
					sources.poll().close();
				} catch (IOException | RuntimeException | Error e) {
					if (thrown == null)
						thrown = e;
					else
						thrown.addSuppressed(e);
				}
			if (!throwing && thrown != null)
				if (thrown instanceof IOException)
					throw (IOException) thrown;
				else if (thrown instanceof RuntimeException)
					throw (RuntimeException) thrown;
				else
					throw (Error) thrown;
		}
	}

}