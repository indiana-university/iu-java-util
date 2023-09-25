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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.net.URL;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import edu.iu.type.IuComponent;
import iu.type.TestArchives;

@SuppressWarnings("javadoc")
public class IuComponentTest {

	@Test
	public void testMustProvideNonBundledDependencies() {
		assertEquals("TODO", assertThrows(IllegalArgumentException.class,
				() -> IuComponent.of(TestArchives.getComponentArchive("testruntime"))).getMessage());
	}

	@Test
	public void testLoadsRuntime() throws Exception {
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

		try (var component = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"))) {
			var contextLoader = Thread.currentThread().getContextClassLoader();
			var loader = component.classLoader();
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

}

//private void assertInvalidFirstPathElement(String expectedMessage, String... entryNames) {
//	boolean hasManifest = false;
//	Queue<JarEntry> entries = new ArrayDeque<>();
//	for (var entryName : entryNames) {
//		if (entryName.equals("META-INF/MANIFEST.MF"))
//			hasManifest = true;
//		entries.offer(new JarEntry(entryName));
//	}
//	var manifest = hasManifest ? mock(Manifest.class) : null;
//	var firstEntry = entries.poll();
//	var remainingEntries = entries.toArray(new JarEntry[entries.size() + 1]); // ends with a null
//
//	var in = mock(InputStream.class);
//	var path = mock(Path.class);
//	var out = mock(OutputStream.class);
//	try (var mockFiles = mockStatic(Files.class)) {
//		try (var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
//			assertEquals(List.of(in), context.arguments());
//			when(jar.getNextJarEntry()).thenReturn(firstEntry, remainingEntries);
//			when(jar.getManifest()).thenReturn(manifest);
//		}); //
//				var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//					assertEquals(List.of(out), context.arguments());
//				})) {
//			mockFiles.when(() -> Files.newInputStream(path)).thenReturn(in);
//			mockFiles.when(() -> Files.newOutputStream(any())).thenReturn(out);
//			assertEquals(expectedMessage,
//					assertThrows(IllegalArgumentException.class, () -> IuComponent.of(path)).getMessage());
//		}
//	}
//}
//
//private void assertInvalidSecondPathElement(String expectedMessage, String... entryNames) {
//	boolean hasManifest = false;
//	Queue<JarEntry> entries = new ArrayDeque<>();
//	for (var entryName : entryNames) {
//		if (entryName.equals("META-INF/MANIFEST.MF"))
//			hasManifest = true;
//		entries.offer(new JarEntry(entryName));
//	}
//	var manifest = hasManifest ? mock(Manifest.class) : null;
//	var firstEntry = entries.poll();
//	var remainingEntries = entries.toArray(new JarEntry[entries.size() + 1]); // ends with a null
//
//	var temp = mock(Path.class);
//	var firstIn = mock(InputStream.class);
//	var firstPath = mock(Path.class);
//	var secondIn = mock(InputStream.class);
//	var secondPath = mock(Path.class);
//	var out = mock(OutputStream.class);
//	try (var mockFiles = mockStatic(Files.class)) {
//		try ( //
//				var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
//					if (List.of(firstIn).equals(context.arguments())) {
//						var firstManifest = mock(Manifest.class);
//						when(jar.getNextJarEntry()).thenReturn( //
//								new JarEntry("META-INF/MANIFEST.MF"), //
//								new JarEntry("META-INF/maven/pom.properties"), //
//								new JarEntry("module-info.class"), //
//								null);
//						when(jar.getManifest()).thenReturn(firstManifest);
//					} else {
//						assertEquals(List.of(secondIn), context.arguments());
//						when(jar.getNextJarEntry()).thenReturn(firstEntry, remainingEntries);
//						when(jar.getManifest()).thenReturn(manifest);
//					}
//				}); //
//				var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//					assertEquals(List.of(out), context.arguments());
//				})) {
//			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
//			mockFiles.when(() -> Files.newInputStream(firstPath)).thenReturn(firstIn);
//			mockFiles.when(() -> Files.newInputStream(secondPath)).thenReturn(secondIn);
//			mockFiles.when(() -> Files.newOutputStream(temp)).thenReturn(out);
//			assertEquals(expectedMessage,
//					assertThrows(IllegalArgumentException.class, () -> IuComponent.of(firstPath, secondPath))
//							.getMessage());
//		}
//	}
//}
//
//@BeforeAll
//public static void setupClass() throws ClassNotFoundException {
//	Class.forName(IuComponent.class.getName());
//	Class.forName(ComponentFactory.class.getName());
//	Class.forName(TemporaryFile.class.getName());
//}
//
//@Test
//public void testRequiresAtLeastOnePath() {
//	assertEquals("Must provide a component archive",
//			assertThrows(IllegalArgumentException.class, () -> IuComponent.of()).getMessage());
//}
//
//@Test
//public void testRejectsInvalidJarFile() throws IOException {
//	var path = mock(Path.class);
//	var out = mock(OutputStream.class);
//	try ( //
//			var mockFiles = mockStatic(Files.class); //
//			var mockJarInputStream = mockConstructionWithAnswer(JarInputStream.class, a -> {
//				throw new IOException();
//			}); //
//			var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//				assertEquals(List.of(out), context.arguments());
//			})) {
//		mockFiles.when(() -> Files.newOutputStream(any())).thenReturn(out);
//		assertEquals("Invalid or unreadable component archive",
//				assertThrows(IllegalArgumentException.class, () -> IuComponent.of(path)).getMessage());
//	}
//}
//
//@Test
//public void testRejectsInvalidSecondJarFile() {
//	var temp = mock(Path.class);
//	var firstIn = mock(InputStream.class);
//	var firstPath = mock(Path.class);
//	var secondPath = mock(Path.class);
//	var out = mock(OutputStream.class);
//	try (var mockFiles = mockStatic(Files.class)) {
//		try ( //
//				var mockJarInputStream = mockConstruction(JarInputStream.class, context -> {
//					if (List.of(firstIn).equals(context.arguments()))
//						return withSettings();
//					else
//						return withSettings().defaultAnswer(a -> {
//							throw new IOException();
//						});
//				}, (jar, context) -> {
//					if (List.of(firstIn).equals(context.arguments())) {
//						var firstManifest = mock(Manifest.class);
//						when(jar.getNextJarEntry()).thenReturn( //
//								new JarEntry("META-INF/MANIFEST.MF"), //
//								new JarEntry("META-INF/maven/pom.properties"), //
//								new JarEntry("module-info.class"), //
//								null);
//						when(jar.getManifest()).thenReturn(firstManifest);
//					}
//				}); //
//				var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//					assertEquals(List.of(out), context.arguments());
//				})) {
//			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
//			mockFiles.when(() -> Files.newInputStream(firstPath)).thenReturn(firstIn);
//			mockFiles.when(() -> Files.newOutputStream(temp)).thenReturn(out);
//			assertEquals("Invalid or unreadable module path entry",
//					assertThrows(IllegalArgumentException.class, () -> IuComponent.of(firstPath, secondPath))
//							.getMessage());
//		}
//	}
//}
//
//@Test
//public void testRejectsInvalidModuleInSecondJar() throws Exception {
//	var temp = mock(Path.class);
//	var firstIn = mock(InputStream.class);
//	var firstPath = mock(Path.class);
//	var secondIn = mock(InputStream.class);
//	var secondPath = mock(Path.class);
//	var out = mock(OutputStream.class);
//	var error = new Error();
//	try (var mockFiles = mockStatic(Files.class)) {
//		try ( //
//				var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
//					var manifest = mock(Manifest.class);
//					when(jar.getNextJarEntry()).thenReturn( //
//							new JarEntry("META-INF/MANIFEST.MF"), //
//							new JarEntry("META-INF/maven/pom.properties"), //
//							new JarEntry("module-info.class"), //
//							null);
//					when(jar.getManifest()).thenReturn(manifest);
//				}); //
//				var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//					assertEquals(List.of(out), context.arguments());
//				}); //
//				var mockComponentModuleFinder = mockConstruction(ComponentModuleFinder.class, (finder, context) -> {
//					when(finder.findAll()).thenThrow(error);
//				})) {
//			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
//			mockFiles.when(() -> Files.newInputStream(firstPath)).thenReturn(firstIn);
//			mockFiles.when(() -> Files.newOutputStream(temp)).thenReturn(out);
//			mockFiles.when(() -> Files.newInputStream(secondPath)).thenReturn(secondIn);
//
//			assertSame(error, assertThrows(Error.class, () -> IuComponent.of(firstPath, secondPath)));
//			verify(mockComponentModuleFinder.constructed().get(0)).close();
//		}
//	}
//}
//
//@Test
//public void testSuppressesCloseFailureAfterInvalidModuleInSecondJar() throws Exception {
//	var temp = mock(Path.class);
//	var firstIn = mock(InputStream.class);
//	var firstPath = mock(Path.class);
//	var secondIn = mock(InputStream.class);
//	var secondPath = mock(Path.class);
//	var out = mock(OutputStream.class);
//	var error = new Error();
//	var closeError = new IOException();
//	try (var mockFiles = mockStatic(Files.class)) {
//		try ( //
//				var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
//					var manifest = mock(Manifest.class);
//					when(jar.getNextJarEntry()).thenReturn( //
//							new JarEntry("META-INF/MANIFEST.MF"), //
//							new JarEntry("META-INF/maven/pom.properties"), //
//							new JarEntry("module-info.class"), //
//							null);
//					when(jar.getManifest()).thenReturn(manifest);
//				}); //
//				var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//					assertEquals(List.of(out), context.arguments());
//				}); //
//				var mockComponentModuleFinder = mockConstruction(ComponentModuleFinder.class, (finder, context) -> {
//					when(finder.findAll()).thenThrow(error);
//					doThrow(closeError).when(finder).close();
//				})) {
//			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
//			mockFiles.when(() -> Files.newInputStream(firstPath)).thenReturn(firstIn);
//			mockFiles.when(() -> Files.newOutputStream(temp)).thenReturn(out);
//			mockFiles.when(() -> Files.newInputStream(secondPath)).thenReturn(secondIn);
//
//			assertSame(error, assertThrows(Error.class, () -> IuComponent.of(firstPath, secondPath)));
//			assertSame(closeError, error.getSuppressed()[0]);
//		}
//	}
//}
//
//private void assertInvalidLegacyJarEmbeddedLib(String expectedMessage, String... entries) throws Throwable {
//	var in = mock(InputStream.class);
//	var path = mock(Path.class);
//	var out = mock(OutputStream.class);
//
//	var temp = mock(Path.class);
//	var url = mock(URL.class);
//	var uri = mock(URI.class);
//	when(uri.toURL()).thenReturn(url);
//	when(temp.toUri()).thenReturn(uri);
//
//	try (var mockFiles = mockStatic(Files.class)) {
//		try ( //
//				var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
//					var manifest = mock(Manifest.class);
//					when(jar.getNextJarEntry()).thenReturn( //
//							new JarEntry("META-INF/MANIFEST.MF"), //
//							new JarEntry("META-INF/maven/pom.properties"), //
//							new JarEntry("META-INF/iu.properties"), //
//							new JarEntry("META-INF/lib/.jar"), //
//							null);
//					when(jar.getManifest()).thenReturn(manifest);
//				}); //
//				var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//					if (List.of(out).equals(context.arguments())) {
//						doThrow(new UnsupportedOperationException("ganbatte")).when(jar)
//								.putNextEntry(argThat(new ArgumentMatcher<JarEntry>() {
//									@Override
//									public boolean matches(JarEntry argument) {
//										return argument.getName().equals("META-INF/lib/.jar");
//									}
//								}));
////								new JarEn?try("META-INF/lib/.jar"));
//					} else
//						fail();
//				})) {
//			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
//			mockFiles.when(() -> Files.newInputStream(path)).thenReturn(in);
//			mockFiles.when(() -> Files.newOutputStream(temp)).thenReturn(out);
//
//			var illegalArgument = assertThrows(IllegalArgumentException.class, () -> IuComponent.of(path));
//			assertEquals("Invalid legacy component archive", illegalArgument.getMessage());
//		}
//	}
//}
//
//@Test
//public void testRejectsInvalidLegacyJar() throws MalformedURLException {
//	var in = mock(InputStream.class);
//	var path = mock(Path.class);
//	var out = mock(OutputStream.class);
//
//	var error = new MalformedURLException();
//	var uri = mock(URI.class);
//	var temp = mock(Path.class);
//	when(temp.toUri()).thenReturn(uri);
//	when(uri.toURL()).thenThrow(error);
//
//	try (var mockFiles = mockStatic(Files.class)) {
//		try ( //
//				var mockJarInputStream = mockConstruction(JarInputStream.class, (jar, context) -> {
//					var manifest = mock(Manifest.class);
//					when(jar.getNextJarEntry()).thenReturn( //
//							new JarEntry("META-INF/MANIFEST.MF"), //
//							new JarEntry("META-INF/maven/pom.properties"), //
//							new JarEntry("META-INF/iu.properties"), //
//							null);
//					when(jar.getManifest()).thenReturn(manifest);
//				}); //
//				var mockJarOutputStream = mockConstruction(JarOutputStream.class, (jar, context) -> {
//					assertEquals(List.of(out), context.arguments());
//				})) {
//			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
//			mockFiles.when(() -> Files.newInputStream(path)).thenReturn(in);
//			mockFiles.when(() -> Files.newOutputStream(temp)).thenReturn(out);
//
//			var illegalArgument = assertThrows(IllegalArgumentException.class, () -> IuComponent.of(path));
//			assertEquals("Invalid legacy component archive", illegalArgument.getMessage());
//			assertSame(error, illegalArgument.getCause());
//		}
//	}
//}
//
//@Test
//public void testRejectesLegacyJarEmbeddedLibWithoutManifest() throws Throwable {
//	assertInvalidLegacyJarEmbeddedLib("Embedded library archive must include a manifest");
//}
//
//@Test
//public void testRejectsMissingManifest() throws Throwable {
//	assertInvalidFirstPathElement("Component archive must include a manifest");
//}
//
//@Test
//public void testRejectsEarFile() throws Throwable {
//	assertInvalidFirstPathElement(
//			"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "META-INF/ejb/endorsed/.jar", "META-INF/ejb/lib/.jar", "META-INF/lib/.jar",
//			".jar");
//	assertInvalidFirstPathElement(
//			"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.war");
//	assertInvalidFirstPathElement(
//			"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.rar");
//	assertInvalidFirstPathElement(
//			"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.so");
//	assertInvalidFirstPathElement(
//			"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.dll");
//	assertInvalidFirstPathElement(
//			"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "META-INF/application.xml");
//	assertInvalidFirstPathElement(
//			"Component must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "META-INF/ra.xml");
//}
//
//@Test
//public void testRejectsSecondEarFile() throws Throwable {
//	assertInvalidSecondPathElement(
//			"Module path entry must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", ".jar");
//	assertInvalidSecondPathElement(
//			"Module path entry must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.war");
//	assertInvalidSecondPathElement(
//			"Module path entry must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.rar");
//	assertInvalidSecondPathElement(
//			"Module path entry must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.so");
//	assertInvalidSecondPathElement(
//			"Module path entry must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "any/.dll");
//	assertInvalidSecondPathElement(
//			"Module path entry must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "META-INF/application.xml");
//	assertInvalidSecondPathElement(
//			"Module path entry must not be defined by an Enterprise Application (ear) or Resource Adapter Archive (rar) file",
//			"META-INF/MANIFEST.MF", "META-INF/ra.xml");
//}
//
//@Test
//public void testRejectsUberJar() throws Throwable {
//	assertInvalidFirstPathElement("Component must not be a shaded (uber-)jar", "META-INF/MANIFEST.MF",
//			"META-INF/maven/a/pom.properties", "META-INF/maven/b/pom.properties");
//}
//
//@Test
//public void testRejectsEmptyJar() throws Throwable {
//	assertInvalidFirstPathElement("Component must include Maven properties", "META-INF/MANIFEST.MF");
//	assertInvalidFirstPathElement("Component must include a module descriptor or META-INF/iu.properties",
//			"META-INF/MANIFEST.MF", "META-INF/maven/pom.properties");
//}
//
//@Test
//public void testRejectsInvalidWar() throws Throwable {
//	assertInvalidFirstPathElement("Web archive must not define classes outside WEB-INF/classes/",
//			"META-INF/MANIFEST.MF", ".class", "WEB-INF/");
//	assertInvalidFirstPathElement("Web archive must not define classes outside WEB-INF/classes/",
//			"META-INF/MANIFEST.MF", "WEB-INF/", ".class");
//	assertInvalidFirstPathElement("Web archive must not define embedded libraries outside WEB-INF/lib/",
//			"META-INF/MANIFEST.MF", "META-INF/lib/.jar", "WEB-INF/");
//	assertInvalidFirstPathElement("Web archive must not define embedded libraries outside WEB-INF/lib/",
//			"META-INF/MANIFEST.MF", "WEB-INF/", "WEB-INF/lib/.jar", "META-INF/lib/.jar");
//	assertInvalidFirstPathElement(
//			"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties",
//			"META-INF/MANIFEST.MF", "META-INF/iu-type.properties", "WEB-INF/");
//	assertInvalidFirstPathElement(
//			"Web archive must define META-INF/iu-type.properties as WEB-INF/classes/META-INF/iu-type.properties",
//			"META-INF/MANIFEST.MF", "WEB-INF/", "META-INF/iu-type.properties");
//	assertInvalidFirstPathElement(
//			"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties",
//			"META-INF/MANIFEST.MF", "META-INF/iu.properties", "WEB-INF/");
//	assertInvalidFirstPathElement(
//			"Web archive must define META-INF/iu.properties as WEB-INF/classes/META-INF/iu.properties",
//			"META-INF/MANIFEST.MF", "WEB-INF/", "META-INF/iu.properties");
//}
//
//@Test
//public void testRejectsNonJarEmbeddedLibs() throws Throwable {
//	assertInvalidFirstPathElement("Embedded library must be a Java Archive (jar) file", "META-INF/MANIFEST.MF",
//			"META-INF/iu.properties", "META-INF/lib/", "META-INF/lib/.jar", "META-INF/lib/notjar");
//}
//
//@Test
//public void testRejectsMixOfModularAndLegacy() throws Throwable {
//	assertInvalidFirstPathElement("Modular component must not define META-INF/iu.properties",
//			"META-INF/MANIFEST.MF", "module-info.class", "META-INF/iu.properties");
//	assertInvalidFirstPathElement("Modular component must not define META-INF/iu.properties",
//			"META-INF/MANIFEST.MF", "META-INF/iu.properties", "module-info.class");
//	assertInvalidFirstPathElement("Modular jar component must not include embedded libraries",
//			"META-INF/MANIFEST.MF", "module-info.class", "META-INF/lib/.jar");
//	assertInvalidFirstPathElement("Modular jar component must not include embedded libraries",
//			"META-INF/MANIFEST.MF", "META-INF/lib/.jar", "module-info.class");
//}
//
//@Test
//public void testRejectsNonModularDependencies() throws Throwable {
//	assertInvalidSecondPathElement("Module path entry must include a manifest");
//	assertInvalidSecondPathElement("Module path entry must include Maven properties", "META-INF/MANIFEST.MF");
//	assertInvalidSecondPathElement("Module path entry must not be a web archive", "META-INF/MANIFEST.MF",
//			"WEB-INF/");
//	assertInvalidSecondPathElement("Module path entry must not define META-INF/iu.properties",
//			"META-INF/MANIFEST.MF", "META-INF/iu.properties");
//	assertInvalidSecondPathElement("Module path entry must include a module descriptor", "META-INF/MANIFEST.MF",
//			"META-INF/maven/pom.properties");
//}
//
//@Test
//public void testRejectsEmbeddedLibsInModularDependencies() throws Throwable {
//	assertInvalidSecondPathElement("Module path entry must not include embedded libraries", "META-INF/MANIFEST.MF",
//			"META-INF/ejb/endorsed/.jar");
//	assertInvalidSecondPathElement("Module path entry must not include embedded libraries", "META-INF/MANIFEST.MF",
//			"META-INF/ejb/lib/.jar");
//	assertInvalidSecondPathElement("Module path entry must not include embedded libraries", "META-INF/MANIFEST.MF",
//			"META-INF/lib/.jar");
//}
//
//@Test
//public void testRejectsUberJarInModularDependencies() throws Throwable {
//	assertInvalidSecondPathElement("Module path entry must not be a shaded (uber-)jar", "META-INF/MANIFEST.MF",
//			"META-INF/maven/pom.properties", "META-INF/maven/pom.properties");
//}
//
//@Test
//public void testLoadsModularComponent() throws Throwable {
//	try (IuComponent component = ComponentFactory.newComponent(getModulePath("testcomponent"))) {
//		assertNotNull(component);
//		assertEquals(IuComponent.Kind.MODULAR_JAR, component.kind());
//		assertEquals("iu-java-type-testcomponent", component.name());
//		assertEquals(IuTest.getProperty("project.version"), component.version());
//		assertEquals("testcomponent",
//				component.classLoader().loadClass("edu.iu.type.testcomponent.TestBean").getModule().getName());
//	}
//}
//
//@Test
//public void testLoadsModularComponentDoesNotDelegateToSystem() throws Throwable {
//	try (IuComponent component = ComponentFactory.newComponent(getModulePath("testcomponent"))) {
//		assertThrows(ClassNotFoundException.class,
//				() -> component.classLoader().loadClass(IuComponent.class.getName()));
//		assertSame(Object.class, component.classLoader().loadClass(Object.class.getName()));
//		var timer = component.classLoader().loadClass("java.util.Timer");
//		assertSame(java.util.Timer.class, timer);
//		assertNull(component.classLoader().getResource("META-INF/iu-test.properties"));
//	}
//}
//
//@Test
//public void testModularComponentWorks() throws Throwable {
//
//}
//
//@Test
//public void testLoadsLegacy() throws Throwable {
//	try (IuComponent component = ComponentFactory.newComponent(getModulePath("testlegacy"))) {
//		assertNotNull(component);
//		assertNull(component.classLoader().loadClass("edu.iu.legacy.LegacyInterface").getModule().getName());
//	}
//}
//
//@Test
//public void testDelegatesModularComponent() throws Throwable {
//	try (var runtime = ComponentFactory.newComponent(getModulePath("testruntime"));
//			var component = runtime.extend(getModulePath("testcomponent"))) {
//		assertNotNull(component);
//		assertEquals("testcomponent",
//				component.classLoader().loadClass("edu.iu.type.testcomponent.TestBean").getModule().getName());
//		assertEquals("iu.util.type.testruntime",
//				component.classLoader().loadClass("edu.iu.type.testruntime.TestRuntime").getModule().getName());
//	}
//}
//
//@Test
//public void testInterfaces() throws Throwable {
//	try (var runtime = ComponentFactory.newComponent(getModulePath("testruntime"));
//			var component = runtime.extend(getModulePath("testcomponent"))) {
//
//		var expected = new LinkedHashSet<>(
//				Set.of("edu.iu.type.testruntime.TestRuntime", "edu.iu.type.testcomponent.TestBean"));
//		var interfaces = component.interfaces();
//		for (var i : interfaces) {
//			var name = i.name();
//			assertTrue(expected.remove(name));
//		}
//		assertTrue(expected.isEmpty());
//	}
//}
//
//@Test
//public void testLegacyInterfaces() throws Throwable {
//	try (var component = ComponentFactory.newComponent(getModulePath("testlegacy"))) {
//		var expected = new LinkedHashSet<>(Set.of("edu.iu.legacy.LegacyInterface"));
//		var interfaces = component.interfaces();
//		for (var i : interfaces) {
//			var name = i.name();
//			assertTrue(expected.remove(name));
//		}
//		assertTrue(expected.isEmpty());
//	}
//}
