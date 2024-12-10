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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.TemporaryFile;

/**
 * Parses a type container into shared API, primary component archive, module
 * dependencies, and private support libraries.
 */
class TypeContainerArchive implements AutoCloseable {

	private final Path primary;
	private final Path[] api;
	private final Path[] lib;
	private final Path[] support;
	private final Path[] embedded;

	private final UnsafeRunnable onClose;

	/**
	 * Constructor.
	 * 
	 * @param path {@link Path}
	 * @throws IOException If an error occurs reading the component source bundle
	 */
	TypeContainerArchive(Path path) throws IOException {
		final var init = new UnsafeRunnable() {
			Path primary = null;
			final Queue<Path> api = new ArrayDeque<>();
			final Queue<Path> lib = new ArrayDeque<>();
			final Queue<Path> support = new ArrayDeque<>();
			final Queue<Path> embedded = new ArrayDeque<>();

			@Override
			public void run() throws Throwable {
				try (final var in = Files.newInputStream(path); //
						final var componentJar = new JarInputStream(in)) {
					JarEntry entry;
					while ((entry = componentJar.getNextJarEntry()) != null) {
						final var name = entry.getName();
						if (name.endsWith("/"))
							continue;

						if (name.endsWith(".jar"))
							if (name.startsWith("api/"))
								api.offer(TemporaryFile.of(componentJar));
							else if (name.startsWith("lib/"))
								lib.offer(TemporaryFile.of(componentJar));
							else if (name.startsWith("support/"))
								support.offer(TemporaryFile.of(componentJar));
							else if (name.indexOf('/') != -1)
								embedded.offer(TemporaryFile.of(componentJar));
							else
								primary = IuObject.once(primary, TemporaryFile.of(componentJar));
						else if (name.endsWith(".war"))
							embedded.offer(TemporaryFile.of(componentJar));
						else
							throw new IllegalArgumentException("Unexpected entry " + name);
					}
				}
			}

		};
		onClose = TemporaryFile.init(init);

		this.primary = Objects.requireNonNull(init.primary);
		this.api = init.api.toArray(Path[]::new);
		this.lib = init.lib.toArray(Path[]::new);
		this.support = init.support.toArray(Path[]::new);
		this.embedded = init.embedded.toArray(Path[]::new);
	}

	/**
	 * Returns the primary component archive.
	 * 
	 * @return {@link Path}
	 */
	Path primary() {
		return primary;
	}

	/**
	 * Returns the shared API.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] api() {
		return api;
	}

	/**
	 * Returns the module dependencies.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] lib() {
		return lib;
	}

	/**
	 * Returns the support libraries.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] support() {
		return support;
	}

	/**
	 * Returns the embedded components.
	 * 
	 * @return {@link Path}[]
	 */
	Path[] embedded() {
		return embedded;
	}

	@Override
	public void close() throws Exception {
		IuException.checked(onClose);
	}

}
