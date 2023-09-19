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
package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockConstructionWithAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;
import edu.iu.type.IuComponent;
import iu.type.ComponentFactory;

@SuppressWarnings("javadoc")
public class ComponentFactoryTest {

	private static Path[] getModulePath(String componentName) throws IOException {
		Queue<Path> modulepath = new ArrayDeque<>();
		modulepath.add(Path.of(IuTest.getProperty(componentName + ".jar")));
		var deps = IuTest.getProperty(componentName + ".deps");
		if (deps != null)
			for (var jar : Files.newDirectoryStream(Path.of(deps).toRealPath()))
				modulepath.offer(jar);
		return modulepath.toArray(new Path[modulepath.size()]);
	}

	@BeforeAll
	public static void setupClass() throws ClassNotFoundException {
		Class.forName(ComponentFactory.class.getName());
	}

	@Test
	public void testRequiresAtLeastOnePath() {
		assertEquals("Must provide a component archive",
				assertThrows(IllegalArgumentException.class, () -> IuComponent.of()).getMessage());
	}

	@Test
	public void testHandlesInvalidJarFile() throws IOException {
		var path = mock(Path.class);
		try ( //
				var mockFiles = mockStatic(Files.class); //
				var mockJarInputStream = mockConstructionWithAnswer(JarInputStream.class, a -> {
					throw new IOException();
				})) {
			assertEquals("Invalid or unreadable component archive",
					assertThrows(IllegalArgumentException.class, () -> IuComponent.of(path)).getMessage());
		}
	}

	private void assertInvalidJar(String expectedMessage, String... entryNames) {
		boolean hasManifest = false;
		Queue<JarEntry> entries = new ArrayDeque<>();
		for (var entryName : entryNames) {
			if (entryName.equals("META-INF/MANIFEST.MF"))
				hasManifest = true;
			entries.offer(new JarEntry(entryName));
		}
		var manifest = hasManifest ? mock(Manifest.class) : null;
		var firstEntry = entries.poll();
		var remainingEntries = entries.toArray(new JarEntry[entries.size() + 1]); // ends with a null

		var in = mock(InputStream.class);
		var path = mock(Path.class);
		try (var mockFiles = mockStatic(Files.class)) {
			try (var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
				assertEquals(List.of(in), context.arguments());
				when(jar.getNextJarEntry()).thenReturn(firstEntry, remainingEntries);
				when(jar.getManifest()).thenReturn(manifest);
			})) {
				mockFiles.when(() -> Files.newInputStream(path)).thenReturn(in);
				assertEquals(expectedMessage,
						assertThrows(IllegalArgumentException.class, () -> IuComponent.of(path)).getMessage());
			}
		}
	}

	@Test
	public void testRejectsMissingManifest() throws Throwable {
		assertInvalidJar("Component archive must include a manifest");
	}

	@Test
	public void testRejectsEarFile() throws Throwable {
		assertInvalidJar(
				"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
				"META-INF/MANIFEST.MF", "META-INF/ejb/endorsed/.jar", "META-INF/ejb/lib/.jar", "META-INF/lib/.jar",
				".jar");
		assertInvalidJar(
				"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
				"META-INF/MANIFEST.MF", "any/.war");
		assertInvalidJar(
				"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
				"META-INF/MANIFEST.MF", "any/.rar");
		assertInvalidJar(
				"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
				"META-INF/MANIFEST.MF", "any/.so");
		assertInvalidJar(
				"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
				"META-INF/MANIFEST.MF", "any/.dll");
		assertInvalidJar(
				"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
				"META-INF/MANIFEST.MF", "META-INF/application.xml");
		assertInvalidJar(
				"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
				"META-INF/MANIFEST.MF", "META-INF/ra.xml");
	}

	@Test
	public void testRejectsUberJar() throws Throwable {
		assertInvalidJar("Component must not be a shaded (uber-)jar", "META-INF/MANIFEST.MF",
				"META-INF/maven/a/pom.properties", "META-INF/maven/b/pom.properties");
	}

	@Test
	public void testRejectsEmptyJar() throws Throwable {
		assertInvalidJar("Component must include Maven properties", "META-INF/MANIFEST.MF");
		assertInvalidJar("Component must include a module descriptor or META-INF/iu.properties", "META-INF/MANIFEST.MF",
				"META-INF/maven/pom.properties");
	}

	@Test
	public void testRejectsInvalidWar() throws Throwable {
		assertInvalidJar("Web archive must not define classes outside WEB-INF/classes/", "META-INF/MANIFEST.MF",
				".class", "WEB-INF/");
		assertInvalidJar("Web archive must not define classes outside WEB-INF/classes/", "META-INF/MANIFEST.MF",
				"WEB-INF/", ".class");
		assertInvalidJar("Web archive must not define embedded libraries outside WEB-INF/lib/", "META-INF/MANIFEST.MF",
				"META-INF/lib/.jar", "WEB-INF/");
		assertInvalidJar("Web archive must not define embedded libraries outside WEB-INF/lib/", "META-INF/MANIFEST.MF",
				"WEB-INF/", "META-INF/lib/.jar");
		assertInvalidJar(
				"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties",
				"META-INF/MANIFEST.MF", "META-INF/iu-type.properties", "WEB-INF/");
		assertInvalidJar(
				"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties",
				"META-INF/MANIFEST.MF", "WEB-INF/", "META-INF/iu-type.properties");
		assertInvalidJar("Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties",
				"META-INF/MANIFEST.MF", "META-INF/iu.properties", "WEB-INF/");
		assertInvalidJar("Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties",
				"META-INF/MANIFEST.MF", "WEB-INF/", "META-INF/iu.properties");
	}

	@Test
	public void testLoadsComponentDefiningModule() throws Throwable {
		IuComponent component = ComponentFactory.newComponent(getModulePath("testcomponent"));
		assertNotNull(component);
		assertEquals("testcomponent",
				component.classLoader().loadClass("edu.iu.type.testcomponent.TestBean").getModule().getName());
	}

	@Test
	public void testLoadsLegacy() throws Throwable {
		IuComponent component = ComponentFactory.newComponent(getModulePath("testlegacy"));
		assertNotNull(component);
		assertNull(component.classLoader().loadClass("edu.iu.legacy.LegacyInterface").getModule().getName());
	}

	@Test
	public void testDelegatesComponent() throws Throwable {
		IuComponent component = ComponentFactory.newComponent(getModulePath("testruntime"))
				.extend(getModulePath("testcomponent"));
		assertNotNull(component);
		assertEquals("testcomponent",
				component.classLoader().loadClass("edu.iu.type.testcomponent.TestBean").getModule().getName());
		assertEquals("iu.util.type.testruntime",
				component.classLoader().loadClass("edu.iu.type.testruntime.TestRuntime").getModule().getName());
	}

	@Test
	public void testInterfaces() throws Throwable {
		IuComponent component = ComponentFactory.newComponent(getModulePath("testruntime"))
				.extend(getModulePath("testcomponent"));

		var expected = new LinkedHashSet<>(
				Set.of("edu.iu.type.testruntime.TestRuntime", "edu.iu.type.testcomponent.TestBean"));
		var interfaces = component.interfaces();
		for (var i : interfaces) {
			var name = i.name();
			assertTrue(expected.remove(name));
		}
		assertTrue(expected.isEmpty());

		component = ComponentFactory.newComponent(getModulePath("testlegacy"));
		expected = new LinkedHashSet<>(Set.of("edu.iu.legacy.LegacyInterface"));
		interfaces = component.interfaces();
		for (var i : interfaces) {
			var name = i.name();
			assertTrue(expected.remove(name));
		}
		assertTrue(expected.isEmpty());
	}

}
