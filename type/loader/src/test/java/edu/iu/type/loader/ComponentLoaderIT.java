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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.IuStream;
import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTest;
import edu.iu.type.base.FilteringClassLoader;
import edu.iu.type.base.TemporaryFile;

@SuppressWarnings("javadoc")
public class ComponentLoaderIT {

	public static InputStream getComponentArchive(String componentName) throws IOException {
		return Files.newInputStream(Path.of(IuTest.getProperty(componentName + ".archive")));
	}

	public static InputStream[] getProvidedDependencyArchives(String componentName) throws IOException {
		Queue<InputStream> providedDependencyArchives = new ArrayDeque<>();
		var deps = IuTest.getProperty(componentName + ".deps");
		if (deps != null)
			for (var jar : Files.newDirectoryStream(Path.of(deps).toRealPath()))
				providedDependencyArchives.offer(Files.newInputStream(jar));
		return providedDependencyArchives.toArray(new InputStream[providedDependencyArchives.size()]);
	}

	@Test
	public void testLoadsRuntime() throws Exception {
		try (final var component = new IuComponentLoader(a -> {
			assertEquals("iu.util.type", a.getTypeModule().getName());
			assertEquals("iu.util.type.impl", a.getTypeImplementationModule().getName());
			final var module = a.getComponentModule();
			assertEquals("iu.util.type.testruntime", module.getName());
			assertSame(module, a.getController().layer().findModule(module.getName()).get());
		}, getComponentArchive("testruntime"), getProvidedDependencyArchives("testruntime"))) {
			assertDoesNotThrow(() -> component.getLoader().loadClass("edu.iu.type.IuType"));
		}
	}

	@Test
	public void testLoadsRuntimeInIsolation() throws Exception {
		final var loader = IuComponentLoader.class.getClassLoader();
		final Queue<URL> path = new ArrayDeque<>();
		path.offer(Path.of("target", "classes").toUri().toURL());
		final var destroy = TemporaryFile.init(() -> {
			for (final var jar : IuIterable.iter("iu-java-base.jar", "iu-java-type-base.jar", "iu-java-type.jar"))
				path.offer(TemporaryFile.init(t -> {
					try (final var in = loader.getResourceAsStream(jar); //
							final var out = Files.newOutputStream(t)) {
						IuStream.copy(in, out);
					}
					return t.toUri().toURL();
				}));
		});

		try (final var isolated = new URLClassLoader(path.toArray(size -> new URL[size]),
				new FilteringClassLoader(IuIterable.empty(), ClassLoader.getSystemClassLoader()))) {
			final var componentLoader = isolated.loadClass(IuComponentLoader.class.getName());
			assertNotSame(componentLoader, IuComponentLoader.class);
			final var getLoader = componentLoader.getMethod("getLoader");
			final var con = componentLoader.getConstructor(InputStream.class, InputStream[].class);
			try (final var component = (AutoCloseable) con.newInstance(getComponentArchive("testruntime"),
					getProvidedDependencyArchives("testruntime"))) {
				assertDoesNotThrow(() -> ((ClassLoader) getLoader.invoke(component)).loadClass("edu.iu.type.IuType"));
			}
		} finally {
			destroy.run();
		}
	}

	@Test
	public void testLoadsRuntimeAndWorks() throws Exception {
		var publicUrlThatWorksAndReturnsJson = "https://idp-stg.login.iu.edu/.well-known/openid-configuration";
		String expected;
		try (InputStream in = new URL(publicUrlThatWorksAndReturnsJson).openStream()) {
			expected = new String(in.readAllBytes());
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort(
					"Expected this to be a public URL that works and returns JSON " + publicUrlThatWorksAndReturnsJson);
			return;
		}

		try (final var componentLoader = new IuComponentLoader(c -> {
			final var module = c.getComponentModule();
			assertEquals("iu.util.type.testruntime", module.getName());
			assertSame(module, c.getController().layer().findModule(module.getName()).get());
		}, getComponentArchive("testruntime"), getProvidedDependencyArchives("testruntime"))) {

			var contextLoader = Thread.currentThread().getContextClassLoader();
			var loader = componentLoader.getLoader();
			try {
				Thread.currentThread().setContextClassLoader(loader);
				var urlReader = loader.loadClass("edu.iu.type.testruntime.UrlReader");
				var urlReader$ = urlReader.getConstructor().newInstance();
				assertEquals(urlReader.getMethod("parseJson", String.class).invoke(urlReader$, expected),
						urlReader.getMethod("get", String.class).invoke(urlReader$, publicUrlThatWorksAndReturnsJson));
			} finally {
				Thread.currentThread().setContextClassLoader(contextLoader);
			}
		}
	}

	@Test
	public void testCloseError() throws Exception {
		final var e = new IOException();
		final var componentLoader = new IuComponentLoader(getComponentArchive("testruntime"),
				getProvidedDependencyArchives("testruntime"));
		final var destroyField = componentLoader.getClass().getDeclaredField("destroy");
		destroyField.setAccessible(true);
		final var originalDestroy = (UnsafeRunnable) destroyField.get(componentLoader);
		destroyField.set(componentLoader, (UnsafeRunnable) () -> {
			originalDestroy.run();
			throw e;
		});
		assertSame(e, assertThrows(IOException.class, componentLoader::close));
	}

}