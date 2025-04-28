/*
 * Copyright © 2025 Indiana University
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import edu.iu.IuIterable;
import edu.iu.legacy.Incompatible;
import edu.iu.legacy.Repurposed;
import edu.iu.test.IuTest;
import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuComponent.Kind;
import edu.iu.type.IuField;
import edu.iu.type.IuType;
import iu.type.IuTypeTestCase;
import iu.type.TestArchives;
import jakarta.annotation.Resource;

@SuppressWarnings("javadoc")
public class IuComponentTest extends IuTypeTestCase {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument .*");
	}

	@Test
	public void testMustProvideNonBundledDependencies() {
		assertEquals("Not all dependencies were met, missing [parsson-1.1+]",
				assertThrows(IllegalArgumentException.class,
						() -> IuComponent.of(TestArchives.getComponentArchive("testruntime"))).getMessage());
	}

	@Test
	public void testMustNotProvideTheSameComponentTwice() {
		assertThrows(IllegalArgumentException.class, () -> IuComponent
				.of(TestArchives.getComponentArchive("testruntime"), TestArchives.getComponentArchive("testruntime")));
	}

	@Test
	public void testMustNotProvideTheSameComponentTwiceAndErrorOnDelete() {
		assertThrows(IllegalArgumentException.class, () -> IuComponent
				.of(TestArchives.getComponentArchive("testruntime"), TestArchives.getComponentArchive("testruntime")));
	}

	@Test
	public void testLoadsRuntime() throws Exception {
		var publicUrlThatWorksAndReturnsJson = "https://idp-stg.login.iu.edu/.well-known/openid-configuration";
		String expected;
		try (InputStream in = URI.create(publicUrlThatWorksAndReturnsJson).toURL().openStream()) {
			expected = new String(in.readAllBytes());
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort(
					"Expected this to be a public URL that works and returns JSON " + publicUrlThatWorksAndReturnsJson);
			return;
		}

		try (var component = IuComponent.of((controller) -> {
			assertTrue(controller.layer().findModule("iu.util.type.testruntime").isPresent());
		}, TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"))) {
			assertTrue(component.toString().startsWith("Component [parent=null, kind=MODULAR_JAR, versions=["),
					component::toString);

			assertEquals(Kind.MODULAR_JAR, component.kind());
			assertEquals("iu-java-type-testruntime", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.type.testruntime.TestRuntime", interfaces.next().name());

			var contextLoader = Thread.currentThread().getContextClassLoader();
			var loader = component.classLoader();
			var layer = component.moduleLayer();
			try {
				Thread.currentThread().setContextClassLoader(loader);
				var urlReader = loader.loadClass("edu.iu.type.testruntime.UrlReader");
				assertSame(layer, urlReader.getModule().getLayer());
				assertSame(urlReader, loader.loadClass("edu.iu.type.testruntime.UrlReader"));
				var ejb = loader.loadClass("jakarta.ejb.EJB");
				assertSame(ejb, loader.loadClass("jakarta.ejb.EJB"));
				var urlReader$ = urlReader.getConstructor().newInstance();
				assertEquals(urlReader.getMethod("parseJson", String.class).invoke(urlReader$, expected),
						urlReader.getMethod("get", String.class).invoke(urlReader$, publicUrlThatWorksAndReturnsJson));
			} finally {
				Thread.currentThread().setContextClassLoader(contextLoader);
			}
		}
	}

	@Test
	public void testRejectsLegacy() throws Exception {
		final var error = assertThrows(IllegalArgumentException.class,
				() -> IuComponent.of(TestArchives.getComponentArchive("testlegacy")));
		assertEquals("First component must be a module", error.getMessage());
	}

	@Disabled
	@Test
	public void testLoadsLegacy() throws Exception {
		var publicUrlThatWorksAndReturnsJson = "https://idp-stg.login.iu.edu/.well-known/openid-configuration";
		String expected;
		try (InputStream in = URI.create(publicUrlThatWorksAndReturnsJson).toURL().openStream()) {
			expected = new String(in.readAllBytes());
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort(
					"Expected this to be a public URL that works and returns JSON " + publicUrlThatWorksAndReturnsJson);
			return;
		}

		try (var component = IuComponent.of(TestArchives.getComponentArchive("testlegacy"))) {

			assertEquals(Kind.LEGACY_JAR, component.kind());
			assertEquals("iu-java-type-testlegacy", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			var resources = component.resources().iterator();
			assertTrue(resources.hasNext());
			while (resources.hasNext()) {
				final var resource = resources.next();
				assertInstanceOf(resource.type().erasedClass(), resource.get());
			}

			var contextLoader = Thread.currentThread().getContextClassLoader();
			var loader = component.classLoader();
			try {
				Thread.currentThread().setContextClassLoader(loader);
				var urlReader = loader.loadClass("edu.iu.legacy.LegacyUrlReader");
				var urlReader$ = urlReader.getConstructor().newInstance();
				assertEquals(urlReader.getMethod("parseJson", String.class).invoke(urlReader$, expected),
						urlReader.getMethod("get", String.class).invoke(urlReader$, publicUrlThatWorksAndReturnsJson));
			} finally {
				Thread.currentThread().setContextClassLoader(contextLoader);
			}
		}
	}

	@Test
	public void testLoadsTestComponent() throws Exception {
		// TODO: remove after implementing @AroundConstruct
		IuTestLogger.allow("iu.type.ComponentResource", Level.CONFIG,
				"Resource initialization failure; .* edu.iu.type.testcomponent.TestResource",
				UnsupportedOperationException.class,
				t -> "@AroundConstruct not supported in this version".equals(t.getMessage()));
		IuTestLogger.allow("iu.type.Component", Level.WARNING, "Invalid class invalid.*", ClassFormatError.class);
		try (var parent = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"));
				var component = parent.extend(TestArchives.getComponentArchive("testcomponent"))) {

			assertEquals(Kind.MODULAR_JAR, component.kind());
			assertEquals("iu-java-type-testcomponent", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			final Set<String> interfaces = new HashSet<>();
			IuIterable.map(component.interfaces(), IuType::name).forEach(interfaces::add);
			assertTrue(interfaces.contains("edu.iu.type.testruntime.TestRuntime"), interfaces::toString);
			assertTrue(interfaces.contains("edu.iu.type.testcomponent.TestBean"), interfaces::toString);
			assertFalse(interfaces.contains("jakarta.ejb.SessionContext"), interfaces::toString);
			assertFalse(interfaces.contains("jakarta.transaction.TransactionManager"), interfaces::toString);

			assertFalse(component
					.annotatedAttributes(parent.classLoader().loadClass("jakarta.ejb.EJB").asSubclass(Annotation.class))
					.iterator().hasNext());

			var found = false;
			final var resourceRefs = component.annotatedAttributes(Resource.class);
			for (final var resourceRef : resourceRefs)
				if (resourceRef.name().equals("stringList")) {
					found = true;
					assertInstanceOf(IuField.class, resourceRef);
					assertEquals("edu.iu.type.testcomponent.TestBeanImpl", resourceRef.declaringType().name());
					assertEquals(List.class, resourceRef.type().erasedClass());
					assertEquals(String.class, resourceRef.type().referTo(List.class).typeParameter("E").erasedClass());
				}
			assertTrue(found);

			found = false;
			for (final var resourceRef : component.resourceReferences())
				if (resourceRef.name().equals("stringList")) {
					found = true;
					assertEquals("edu.iu.type.testcomponent.TestBeanImpl", resourceRef.referrerType().name());
					assertEquals(List.class, resourceRef.type().erasedClass());
					assertEquals(String.class, resourceRef.type().referTo(List.class).typeParameter("E").erasedClass());
				}
			assertTrue(found);

			final Set<String> expected = new HashSet<>(Set.of("urlReader", "priorityResource", "testResource"));
			for (final var resource : component.resources()) {
				final var name = resource.name();
				assertTrue(expected.remove(name), () -> name + " " + expected);
				if (name.equals("testResource")) {
					// TODO: STARCH-653 Implement @AroundConstruct
					assertThrows(UnsupportedOperationException.class, resource::get);
				}
			}
			assertTrue(expected.isEmpty(), expected::toString);

			final var target = component.classLoader().loadClass("edu.iu.type.testcomponent.TestBean");
			final var view = IuComponent.scan(target);
			assertSame(IuType.of(target), view.interfaces().iterator().next());
			view.close();
			component.interfaces(); // not closed
		}
	}

	@Test
	public void testLoadsTestWar() throws Exception {
		try (var parent = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"));
				var component = parent.extend(TestArchives.getComponentArchive("testweb"),
						TestArchives.getProvidedDependencyArchives("testweb"))) {

			assertEquals(Kind.MODULAR_WAR, component.kind());
			assertEquals("iu-java-type-testweb", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			final var loader = component.classLoader();
			final var testRuntime = loader.loadClass("edu.iu.type.testruntime.TestRuntime");
			assertSame(testRuntime, loader.loadClass("edu.iu.type.testruntime.TestRuntime"));

			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertSame(testRuntime, interfaces.next().erasedClass());

			final var annotated = component.annotatedTypes(Resource.class);
			assertTrue(annotated.iterator().hasNext());
			assertEquals("edu.iu.type.testruntime.UrlReader", annotated.iterator().next().autoboxClass().getName());

			var expectedResources = new HashSet<>(Set.of("urlReader", "index.html", "WEB-INF/web.xml"));
			for (final var r : component.resources()) {
				if (r.name().equals("urlReader"))
					assertEquals("edu.iu.type.testruntime.UrlReader", r.type().autoboxClass().getName());
				else
					assertInstanceOf(byte[].class, r.get());
				assertTrue(expectedResources.remove(r.name()));
			}
			assertTrue(expectedResources.isEmpty(), expectedResources::toString);
		}
	}

	@Disabled
	@Test
	public void testLoadsLegacyWar() throws Exception {
		try (var parent = IuComponent.of(TestArchives.getComponentArchive("testlegacy"));
				var component = parent.extend(TestArchives.getComponentArchive("testlegacyweb"),
						TestArchives.getProvidedDependencyArchives("testlegacyweb"))) {

			assertEquals(Kind.LEGACY_WAR, component.kind());
			assertEquals("iu-java-type-testlegacyweb", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			final Set<String> interfaces = new HashSet<>();
			IuIterable.map(component.interfaces(), IuType::name).forEach(interfaces::add);
			assertTrue(interfaces.contains("edu.iu.legacy.LegacyInterface"), interfaces::toString);
			assertTrue(interfaces.contains("edu.iu.legacy.NotResource"), interfaces::toString);

			var incompatible = component.annotatedTypes(Incompatible.class).iterator();
			assertTrue(incompatible.hasNext());
			assertEquals("edu.iu.legacy.LegacyResource", incompatible.next().name());
			assertFalse(incompatible.hasNext(), () -> incompatible.next().name());

			var testsShouldBeEmpty = component.annotatedTypes(Test.class).iterator();
			assertFalse(testsShouldBeEmpty.hasNext(), () -> testsShouldBeEmpty.next().name());

			var expectedResources = new HashSet<>(Set.of("two", "legacyResource", "index.jsp", "WEB-INF/web.xml"));
			for (final var r : component.resources())
				assertTrue(expectedResources.remove(r.name()));
			assertTrue(expectedResources.isEmpty(), expectedResources::toString);
		}
	}

	@Test
	public void testScanFolder() throws Exception {
		var scannedView = IuComponent.scan(getClass());
		assertSame(Repurposed.class, scannedView.interfaces().iterator().next().erasedClass());
	}

	@Test
	public void testFolderWithIuProps() throws Exception {
		final Deque<Path> toDelete = new ArrayDeque<>();
		try {
			final var root = Files.createTempDirectory("iu-java-type-testIuProps");
			toDelete.push(root);
			final var maven = root.resolve("maven");
			toDelete.push(maven);
			Files.createDirectory(maven);
			final var groupId = maven.resolve("fake.group.id");
			toDelete.push(groupId);
			Files.createDirectory(groupId);
			final var artifactId = groupId.resolve("iu-fake-artifact");
			toDelete.push(artifactId);
			Files.createDirectory(artifactId);
			final var pomProperties = artifactId.resolve("pom.properties");
			toDelete.push(pomProperties);
			try (final var out = Files.newBufferedWriter(pomProperties)) {
				out.write("""
						artifactId=iu-fake-artifact
						version=1.2.3-SNAPSHOT
						""");
			}

			final var metaInf = root.resolve("META-INF");
			toDelete.push(metaInf);
			Files.createDirectory(metaInf);
			final var iuProperties = metaInf.resolve("iu.properties");
			toDelete.push(iuProperties);
			try (final var out = Files.newBufferedWriter(iuProperties)) {
				out.write("""
						foo=bar
						""");
			}

			IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root);

		} finally {
			while (!toDelete.isEmpty())
				Files.deleteIfExists(toDelete.pop());
		}

	}

	@Test
	public void testFolderWithIuTypeProps() throws Exception {
		final Deque<Path> toDelete = new ArrayDeque<>();
		try {
			final var root = Files.createTempDirectory("iu-java-type-testIuTypeProps");
			toDelete.push(root);
			final var maven = root.resolve("maven");
			toDelete.push(maven);
			Files.createDirectory(maven);
			final var groupId = maven.resolve("fake.group.id");
			toDelete.push(groupId);
			Files.createDirectory(groupId);
			final var artifactId = groupId.resolve("iu-fake-artifact");
			toDelete.push(artifactId);
			Files.createDirectory(artifactId);
			final var pomProperties = artifactId.resolve("pom.properties");
			toDelete.push(pomProperties);
			try (final var out = Files.newBufferedWriter(pomProperties)) {
				out.write("""
						artifactId=iu-fake-artifact
						version=1.2.3-SNAPSHOT
						""");
			}

			final var metaInf = root.resolve("META-INF");
			toDelete.push(metaInf);
			Files.createDirectory(metaInf);
			final var iuTypeProperties = metaInf.resolve("iu-type.properties");
			toDelete.push(iuTypeProperties);
			try (final var out = Files.newBufferedWriter(iuTypeProperties)) {
				out.write("""
						foo=bar
						""");
			}

			IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root);

		} finally {
			while (!toDelete.isEmpty())
				Files.deleteIfExists(toDelete.pop());
		}

	}

	@Test
	public void testJunkFolder() throws Exception {
		final Consumer<Executable> assertMissing = Assertions::assertDoesNotThrow;
//		assertEquals(
//				"Missing ../maven-archiver/pom.properties or META-INF/maven/{groupId}/{artifactId}/pom.properties",
//				assertThrows(IllegalArgumentException.class, exec).getMessage());
		final Deque<Path> toDelete = new ArrayDeque<>();
		try {
			final var root = Files.createTempDirectory("iu-java-type-testJunkFolder");
			toDelete.push(root);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));

			final var maven = root.resolve("maven");
			toDelete.push(maven);
			Files.createDirectory(maven);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));

			final var groupId = maven.resolve("fake.group.id");
			toDelete.push(groupId);
			Files.createFile(groupId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));
			Files.delete(groupId);

			Files.createDirectory(groupId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));

			final var badGroup = maven.resolve("bad.group.id");
			toDelete.push(badGroup);
			Files.createDirectory(badGroup);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));
			Files.delete(badGroup);

			final var artifactId = groupId.resolve("iu-fake-artifact");
			toDelete.push(artifactId);
			Files.createFile(artifactId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));
			Files.delete(artifactId);

			Files.createDirectory(artifactId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));

			final var badArtifact = groupId.resolve("iu-bad-artifact");
			toDelete.push(badArtifact);
			Files.createDirectory(badArtifact);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root));
			Files.delete(badArtifact);

			final var pomProperties = artifactId.resolve("pom.properties");
			toDelete.push(pomProperties);
			try (final var out = Files.newBufferedWriter(pomProperties)) {
				out.write("""
						artifactId=iu-fake-artifact
						version=1.2.3-SNAPSHOT
						""");
			}

			IuComponent.scan(ClassLoader.getSystemClassLoader(), ModuleLayer.boot(), root);

		} finally {
			while (!toDelete.isEmpty())
				Files.deleteIfExists(toDelete.pop());
		}
	}

	@Test
	public void testScanJar() throws Exception {
		try (var controlComponent = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"))) {
			final var interfaces = controlComponent.interfaces();
			final var i = interfaces.iterator();
			final var targetInterface = i.next().erasedClass();

			var scannedView = IuComponent.scan(targetInterface);
			final var scannedInterfacesIterator = scannedView.interfaces().iterator();
			assertSame(targetInterface, scannedInterfacesIterator.next().erasedClass());
		}
	}

}
