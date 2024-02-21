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
package iu.auth.bundle;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import edu.iu.IuException;
import edu.iu.IuStream;
import edu.iu.UnsafeRunnable;
import edu.iu.type.base.TemporaryFile;
import edu.iu.type.loader.IuComponentLoader;

/**
 * Bootstrap SPI instance.
 */
public class Bootstrap {

	private static IuComponentLoader impl;
	private static UnsafeRunnable shutdown;

	private static IuComponentLoader loadImpl() throws Throwable {
		final var url = Bootstrap.class.getClassLoader().getResource("iu-java-auth-impl-bundle.jar");
		final var uc = url.openConnection();
		uc.setUseCaches(false);

		class Box {
			InputStream primary = null;
			final Queue<URL> utilDeps = new ArrayDeque<>();
			final Queue<InputStream> moduleDeps = new ArrayDeque<>();
		}
		final var box = new Box();
		final var cleanUtils = TemporaryFile.init(() -> {
			try (final var in = uc.getInputStream(); final var jar = new JarInputStream(in)) {
				JarEntry entry;
				while ((entry = jar.getNextJarEntry()) != null) {
					final var name = entry.getName();
					if ("iu-java-auth-impl.jar".equals(name))
						box.primary = new ByteArrayInputStream(IuStream.read(jar));
					else // if (name.endsWith(".jar"))
					if (name.startsWith("jakarta.") //
							|| name.startsWith("iu-java-") //
							|| name.equals("parsson.jar"))
						box.moduleDeps.add(new ByteArrayInputStream(IuStream.read(jar)));
					else
						box.utilDeps.add(TemporaryFile.init(t -> {
							try (final var out = Files.newOutputStream(t)) {
								IuStream.copy(jar, out);
							}
							return t.toUri().toURL();
						}));
				}
			}
		});
		final var parent = new URLClassLoader(box.utilDeps.toArray(URL[]::new), Bootstrap.class.getClassLoader());

		final var impl = new IuComponentLoader(parent, Set.of("edu.iu", "edu.iu.auth", "edu.iu.auth.basic",
				"edu.iu.auth.oauth", "edu.iu.auth.oidc", "edu.iu.auth.spi"), controller -> {
				}, Objects.requireNonNull(box.primary, "primary"), box.moduleDeps.toArray(a -> new InputStream[a]));

		shutdown = () -> {
			impl.close();
			parent.close();
			cleanUtils.run();
			Bootstrap.impl = null;
		};

		return impl;
	}

	static {
		impl = IuException.unchecked(Bootstrap::loadImpl);
	}

	/**
	 * Loads a service interface using bootstrapped class loader.
	 * 
	 * @param <T>              service type
	 * @param serviceInterface service interface
	 * @return SPI implementation
	 */
	static <T> T load(Class<T> serviceInterface) {
		final var delegate = ServiceLoader.load(serviceInterface, Objects.requireNonNull(impl).getLoader()).findFirst()
				.get();
		return serviceInterface.cast(Proxy.newProxyInstance(serviceInterface.getClassLoader(),
				new Class<?>[] { serviceInterface }, (proxy, method, args) -> {
					if (impl == null)
						throw new IllegalStateException("iu.util.auth.impl closed");
					return IuException.checkedInvocation(() -> method.invoke(delegate, args));
				}));
	}

	/**
	 * Tears down the auth implementation module.
	 * <p>
	 * <em>May</em> only be accessed programmatically or by JVM args, by the
	 * component in control of bootstrapping authentication.
	 * </p>
	 */
	static synchronized void shutdown() {
		if (shutdown != null) {
			IuException.unchecked(shutdown);
			shutdown = null;
		}
	}

	private Bootstrap() {
	}

}
