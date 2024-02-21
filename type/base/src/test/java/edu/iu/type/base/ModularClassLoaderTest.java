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
package edu.iu.type.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ModuleLayer.Controller;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class ModularClassLoaderTest {

	private Deque<UnsafeRunnable> onTeardown = new ArrayDeque<>();

	private Iterable<Path> getModulePath(String componentName) throws IOException {
		Deque<Path> path = new ArrayDeque<>();
		final var firstEntry = Path.of(IuTest.getProperty(componentName + ".archive"));

		Path web = null;
		try (final var in = Files.newInputStream(firstEntry); //
				final var jar = new JarInputStream(in)) {
			OutputStream webOut = null;
			JarOutputStream webJar = null;
			JarEntry entry;
			while ((entry = jar.getNextJarEntry()) != null) {
				final var name = entry.getName();
				if (name.startsWith("WEB-INF/")) {
					if (web == null) {
						web = TemporaryFile.init(out -> {
							onTeardown.add(() -> Files.delete(out));
							return out;
						});
						webOut = Files.newOutputStream(web);
						webJar = new JarOutputStream(webOut);
					}
					if (name.startsWith("WEB-INF/lib/") && name.endsWith(".jar")) {
						final var embeddedLib = TemporaryFile.init(lib -> {
							try (final var out = Files.newOutputStream(lib)) {
								out.write(jar.readAllBytes());
							}
							return lib;
						});
						path.add(embeddedLib);
						onTeardown.add(() -> Files.delete(embeddedLib));
					} else if (name.startsWith("WEB-INF/classes/")) {
						final var outEntry = new JarEntry(name.substring(16));
						webJar.putNextEntry(outEntry);
						webJar.write(jar.readAllBytes());
						webJar.closeEntry();
					}
				}
				if (webJar == null && name.startsWith("META-INF/lib/") && name.endsWith(".jar")) {
					final var embeddedLib = TemporaryFile.init(lib -> {
						try (final var out = Files.newOutputStream(lib)) {
							out.write(jar.readAllBytes());
						}
						return lib;
					});
					path.add(embeddedLib);
					onTeardown.add(() -> Files.delete(embeddedLib));
				}
			}
			if (web != null) {
				path.add(web);
				webJar.close();
				webOut.close();
			}
		}

		if (web == null)
			path.add(firstEntry);

		var deps = IuTest.getProperty(componentName + ".deps");
		if (deps != null)
			for (var jar : Files.newDirectoryStream(Path.of(deps).toRealPath()))
				path.offer(jar);
		return path;
	}

	private ModularClassLoader createModularLoader(String componentName, ClassLoader parent) throws IOException {
		final ModularClassLoader loader;
		if (parent == null)
			loader = new ModularClassLoader("testweb".equals(componentName), getModulePath(componentName),
					ModuleLayer.boot(), null, null);
		else
			loader = new ModularClassLoader("testweb".equals(componentName), getModulePath(componentName),
					(parent instanceof ModularClassLoader m) ? m.getModuleLayer() : ModuleLayer.boot(), parent, null);
		onTeardown.push(loader::close);
		return loader;
	}

	@AfterEach
	public void teardown() throws Throwable {
		while (!onTeardown.isEmpty())
			onTeardown.pop().run();
	}

	@Test
	public void testPassesController() throws IOException {
		class Box {
			Controller controller;
		}
		final var box = new Box();
		final var loader = new ModularClassLoader(false, getModulePath("testruntime"), ModuleLayer.boot(), null,
				c -> box.controller = c);
		onTeardown.push(loader::close);
		assertInstanceOf(Controller.class, box.controller);
	}

	@Test
	public void testNullModuleIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertTrue(loader.isOpen(null, null));
	}

	@Test
	public void testClassResourceIsNotEncapsulaed() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		assertTrue(loader.isOpen(module, ".class"));
	}

	@Test
	public void testRootPackageIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		assertTrue(loader.isOpen(module, "foo"));
		assertTrue(loader.isOpen(module, "/foo"));
	}

	@Test
	public void testMetaInfIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		assertTrue(loader.isOpen(module, "META-INF/whatever"));
	}

	@Test
	public void testOpenPackagesAreOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		when(module.isExported("bar.foo")).thenReturn(true);
		when(module.isExported("foo.bar")).thenReturn(true);
		when(module.isOpen("bar.foo")).thenReturn(true);
		assertFalse(loader.isOpen(module, "foo/bar/baz"));
		assertTrue(loader.isOpen(module, "bar/foo/baz"));
		assertTrue(loader.isOpen(module, "/bar/foo/baz"));
	}

	@Test
	public void testModuleShortCircuit() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final Set<String> expected = new HashSet<>();
		expected.add("jakarta.json");
		expected.add("jakarta.annotation");
		expected.add("jakarta.interceptor");
		expected.add("org.eclipse.parsson");
		loader.findResource("META-INF/LICENSE.md", (moduleRef, url) -> {
			final var name = moduleRef.descriptor().name();
			assertTrue(expected.remove(name), name + " " + expected);
			return true;
		});
		assertEquals(3, expected.size());
	}

	@Test
	public void testClasspathShortCircuit() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		class Box {
			boolean found;
		}
		final var box = new Box();
		loader.findResource("META-INF/LICENSE.md", (moduleRef, url) -> {
			if (moduleRef != null)
				return false;
			else
				return box.found = true;
		});
		assertTrue(box.found);
	}

	@Test
	public void testLoadsByModule() throws IOException, ClassNotFoundException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "edu.iu.type.testruntime.TestRuntime";
		final var testRuntime = loader.findClass("iu.util.type.testruntime", className);
		assertSame(testRuntime, loader.loadClass(className));
	}

	@Test
	public void testLoadByModuleHandlesInvalidModule() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "edu.iu.type.testruntime.TestRuntime";
		assertNull(loader.findClass("foo.bar", className));
	}

	@Test
	public void testLoadByModuleHandlesInvalidClass() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "foo.bar";
		assertNull(loader.findClass("iu.util.type.testruntime", className));
	}

	@Test
	public void testFindModuleResources() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var resources = loader.findResources("module-info.class");

		final Set<URL> set = new HashSet<>();
		while (resources.hasMoreElements()) {
			final var url = resources.nextElement();
			assertTrue(set.add(url), () -> set + " " + url);
		}
		assertEquals(5, set.size(), set::toString);
	}

	@Test
	public void testFindsClassInModule() throws IOException, ClassNotFoundException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "edu.iu.type.testruntime.TestRuntime";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testFindsClassInClasspath() throws IOException, ClassNotFoundException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "jakarta.ejb.EJB";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testFindsClassInBaseModule() throws IOException, ClassNotFoundException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "java.net.http.HttpClient";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testFindsClassInWebModule() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "edu.iu.type.testweb.TestServlet";
		final var loaded = loader.loadClass(className, true);
		assertSame(loader.loadClass(className), loaded);
	}

	@Test
	public void testFindsClassInParentModule() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "edu.iu.type.testruntime.TestRuntime";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testDoesntFindClassDelegatedToParent() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "jakarta.ejb.EJB";
		assertThrows(ClassNotFoundException.class, () -> loader.findClass(className));
	}

	@Test
	public void testLoadsClassDelegatedToParent() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "org.apache.commons.lang.StringEscapeUtils";
		final var ejb = loader.loadClass(className, true);
		assertSame(ejb, parent.loadClass(className));
	}

	@Test
	public void testFindNoResources() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var resources = loader.findResources("foo/bar");
		assertFalse(resources.hasMoreElements());
	}

	@Test
	public void testFindClasspathResources() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var resources = loader.findResources("META-INF/LICENSE.txt");

		final Set<URL> set = new HashSet<>();
		while (resources.hasMoreElements()) {
			final var url = resources.nextElement();
			assertTrue(set.add(url), () -> set + " " + url);
		}
		assertEquals(1, set.size(), set::toString);
	}

	@Test
	public void testFindModuleResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertNotNull(loader.findResource("module-info.class"));
	}

	@Test
	public void testFindNoResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertNull(loader.findResource("foo/bar"));
	}

	@Test
	public void testFindClasspathResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertNotNull(loader.findResource("META-INF/LICENSE.txt"));
	}

	@Test
	public void testFindResourceByModule() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var jsonLicense = loader.findResource("jakarta.json", "META-INF/LICENSE.md");
		assertNotNull(jsonLicense);
		assertNull(loader.findResource("iu.util.type.testruntime", "META-INF/LICENSE.md"));
		assertNull(loader.findResource("foo.bar", "META-INF/LICENSE.md"));
		assertNotNull(loader.findResource(null, "META-INF/LICENSE.md"));
		assertNull(loader.findResource(null, "foo.bar"));
	}

	@Test
	public void testDoesntFindEncapsulatedResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var resources = loader.findResources("org/eclipse/parsson/messages.properties");
		assertFalse(resources.hasMoreElements());
		assertNull(loader.findResource("org/eclipse/parsson/messages.properties"));
	}

}
