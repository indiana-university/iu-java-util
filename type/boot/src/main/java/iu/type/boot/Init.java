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
package iu.type.boot;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Logger;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.UnsafeRunnable;
import edu.iu.logging.IuLogContext;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;

/**
 * Base component container initialization hook.
 */
public class Init implements AutoCloseable {
	static {
		IuObject.assertNotOpen(Init.class);
	}

	private static final Logger LOG = Logger.getLogger(Init.class.getName());

	private volatile ModularClassLoader loader;
	private volatile AutoCloseable container;

	/**
	 * Initializes an isolated container runtime environment.
	 * 
	 * @throws IOException If an I/O errors occurs reading the container bundler or
	 *                     a component archive.
	 */
	public Init() throws IOException {
		IuLogContext.initialize();
		
		final var init = new UnsafeRunnable() {
			private ModularClassLoader loader;
			private AutoCloseable containerBootstrap;

			@Override
			public void run() throws Throwable {
				LOG.fine("before init loader");
				loader = new ModularClassLoader(false,
						TemporaryFile.readBundle(ClassLoader.getSystemResource("iu-java-type-container-bundle.jar")),
						ModuleLayer.boot(), ClassLoader.getSystemClassLoader(), c -> {
							final var bootModule = Init.class.getModule();
							final var containerModule = c.layer().findModule("iu.util.type.container").get();
							c.addExports(containerModule, "iu.type.container", bootModule);

							final var iuTypeBase = ModuleLayer.boot().findModule("iu.util.type.base").get();
							c.addReads(containerModule, iuTypeBase);
							c.addReads(c.layer().findModule("iu.util.type.bundle").get(), iuTypeBase);
						});

				try {
					LOG.fine("after init loader " + loader.getModuleLayer());
					containerBootstrap = (AutoCloseable) loader //
							.loadClass("iu.type.container.TypeContainerBootstrap").getConstructor().newInstance();

					LOG.fine("after init container bootstrap " + containerBootstrap);
				} catch (Throwable e) {
					IuException.suppress(e, loader::close);
					throw e;
				}
			}
		};
		TemporaryFile.init(init);
		loader = Objects.requireNonNull(init.loader);
		container = Objects.requireNonNull(init.containerBootstrap);
	}

	@Override
	public synchronized void close() {
		final var container = this.container;
		final var loader = this.loader;
		if (container != null) {
			this.container = null;
			this.loader = null;

			LOG.fine("before destroy container bootstrap " + container);
			var error = IuException.suppress(null, container::close);

			LOG.fine("before destroy loader " + loader.getModuleLayer());
			error = IuException.suppress(error, loader::close);

			if (error != null)
				throw IuException.unchecked(error);
		}
	}

	/**
	 * Entry point.
	 * 
	 * @param a arguments
	 */
	public static void main(String... a) {
		IuException.unchecked(() -> {
			try (final var init = new Init()) {
				((UnsafeRunnable) init.container).run();
			}
		});
	}

}
