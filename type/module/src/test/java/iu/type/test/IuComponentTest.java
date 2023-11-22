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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import edu.iu.IuIterable;
import edu.iu.legacy.Incompatible;
import edu.iu.legacy.Repurposed;
import edu.iu.test.IuTest;
import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuComponent.Kind;
import iu.type.IuTypeTestCase;
import iu.type.TestArchives;

@SuppressWarnings("javadoc")
public class IuComponentTest extends IuTypeTestCase {

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.type.ParameterizedElement", Level.FINEST, "replaced type argument .*");
	}

	@Test
	public void testMustProvideNonBundledDependencies() {
		assertEquals("Not all depdendencies were met, missing [parsson-1.1+]",
				assertThrows(IllegalArgumentException.class,
						() -> IuComponent.of(TestArchives.getComponentArchive("testruntime"))).getMessage());
	}

	@Test
	public void testMustNotProvideTheSameComponentTwice() {
		assertEquals(
				"iu-java-type-testruntime-7.0.0-SNAPSHOT was already provided by iu-java-type-testruntime-7.0.0-SNAPSHOT",
				assertThrows(IllegalArgumentException.class,
						() -> IuComponent.of(TestArchives.getComponentArchive("testruntime"),
								TestArchives.getComponentArchive("testruntime")))
						.getMessage());
	}

	@Test
	public void testMustNotProvideTheSameComponentTwiceAndErrorOnDelete() {
		assertEquals(
				"iu-java-type-testruntime-7.0.0-SNAPSHOT was already provided by iu-java-type-testruntime-7.0.0-SNAPSHOT",
				assertThrows(IllegalArgumentException.class,
						() -> IuComponent.of(TestArchives.getComponentArchive("testruntime"),
								TestArchives.getComponentArchive("testruntime")))
						.getMessage());
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

			assertEquals(Kind.MODULAR_JAR, component.kind());
			assertEquals("iu-java-type-testruntime", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.type.testruntime.TestRuntime", interfaces.next().name());
			assertFalse(interfaces.hasNext());

			var contextLoader = Thread.currentThread().getContextClassLoader();
			var loader = component.classLoader();
			try {
				Thread.currentThread().setContextClassLoader(loader);
				var urlReader = loader.loadClass("edu.iu.type.testruntime.UrlReader");
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
	public void testLoadsLegacy() throws Exception {
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

		try (var component = IuComponent.of(TestArchives.getComponentArchive("testlegacy"))) {

			assertEquals(Kind.LEGACY_JAR, component.kind());
			assertEquals("iu-java-type-testlegacy", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			var expectedInterfaces = new HashSet<>(
					Set.of("edu.iu.legacy.LegacyInterface", "edu.iu.legacy.NotResource"));
			for (final var i : component.interfaces())
				assertTrue(expectedInterfaces.remove(i.name()));
			assertTrue(expectedInterfaces.isEmpty(), expectedInterfaces::toString);

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
		IuTestLogger.expect("iu.type.ComponentResource", Level.CONFIG,
				"Resource initialization failure; .* edu.iu.type.testcomponent.TestResource",
				UnsupportedOperationException.class,
				t -> "@AroundConstruct not supported in this version".equals(t.getMessage()));
		IuTestLogger.expect("iu.type.Component", Level.WARNING, "Invalid class invalid.*", ClassFormatError.class);
		try (var parent = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"));
				var component = parent.extend(TestArchives.getComponentArchive("testcomponent"))) {

			assertEquals(Kind.MODULAR_JAR, component.kind());
			assertEquals("iu-java-type-testcomponent", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			var expectedInterfaces = new HashSet<>(
					Set.of("edu.iu.type.testruntime.TestRuntime", "edu.iu.type.testcomponent.TestBean"));
			for (final var i : component.interfaces())
				assertTrue(expectedInterfaces.remove(i.name()));
			assertTrue(expectedInterfaces.isEmpty(), expectedInterfaces::toString);

			var resources = component.resources().iterator();
			// TODO: restore after implementing @AroundConstruct
			// assertTrue(resources.hasNext());
			// assertEquals("TestResource", resources.next().name());
			assertFalse(resources.hasNext());
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
			assertFalse(interfaces.hasNext());

			var expectedResources = new HashSet<>(Set.of("index.html", "WEB-INF/web.xml"));
			for (final var r : component.resources())
				assertTrue(expectedResources.remove(r.name()));
			assertTrue(expectedResources.isEmpty(), expectedResources::toString);
		}
	}

	@Test
	public void testLoadsLegacyWar() throws Exception {
		try (var parent = IuComponent.of(TestArchives.getComponentArchive("testlegacy"));
				var component = parent.extend(TestArchives.getComponentArchive("testlegacyweb"),
						TestArchives.getProvidedDependencyArchives("testlegacyweb"))) {

			assertEquals(Kind.LEGACY_WAR, component.kind());
			assertEquals("iu-java-type-testlegacyweb", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			var expectedInterfaces = new HashSet<>(
					Set.of("edu.iu.legacy.LegacyInterface", "edu.iu.legacy.NotResource"));
			for (final var i : component.interfaces())
				assertTrue(expectedInterfaces.remove(i.name()));
			assertTrue(expectedInterfaces.isEmpty(), expectedInterfaces::toString);

			var incompatible = component.annotatedTypes(Incompatible.class).iterator();
			assertTrue(incompatible.hasNext());
			assertEquals("edu.iu.legacy.LegacyResource", incompatible.next().name());
			assertFalse(incompatible.hasNext(), () -> incompatible.next().name());

			var testsShouldBeEmpty = component.annotatedTypes(Test.class).iterator();
			assertFalse(testsShouldBeEmpty.hasNext(), () -> testsShouldBeEmpty.next().name());

			var expectedResources = new HashSet<>(Set.of("two", "LegacyResource", "index.jsp", "WEB-INF/web.xml"));
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

			IuComponent.scan(ClassLoader.getSystemClassLoader(), root);

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

			IuComponent.scan(ClassLoader.getSystemClassLoader(), root);

		} finally {
			while (!toDelete.isEmpty())
				Files.deleteIfExists(toDelete.pop());
		}

	}

	@Test
	public void testJunkFolder() throws Exception {
		final Consumer<Executable> assertMissing = exec -> assertEquals(
				"Missing ../maven-archiver/pom.properties or META-INF/maven/{groupId}/{artifactId}/pom.properties",
				assertThrows(IllegalArgumentException.class, exec).getMessage());
		final Deque<Path> toDelete = new ArrayDeque<>();
		try {
			final var root = Files.createTempDirectory("iu-java-type-testJunkFolder");
			toDelete.push(root);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));

			final var maven = root.resolve("maven");
			toDelete.push(maven);
			Files.createDirectory(maven);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));

			final var groupId = maven.resolve("fake.group.id");
			toDelete.push(groupId);
			Files.createFile(groupId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));
			Files.delete(groupId);

			Files.createDirectory(groupId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));

			final var badGroup = maven.resolve("bad.group.id");
			toDelete.push(badGroup);
			Files.createDirectory(badGroup);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));
			Files.delete(badGroup);

			final var artifactId = groupId.resolve("iu-fake-artifact");
			toDelete.push(artifactId);
			Files.createFile(artifactId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));
			Files.delete(artifactId);

			Files.createDirectory(artifactId);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));

			final var badArtifact = groupId.resolve("iu-bad-artifact");
			toDelete.push(badArtifact);
			Files.createDirectory(badArtifact);
			assertMissing.accept(() -> IuComponent.scan(ClassLoader.getSystemClassLoader(), root));
			Files.delete(badArtifact);

			final var pomProperties = artifactId.resolve("pom.properties");
			toDelete.push(pomProperties);
			try (final var out = Files.newBufferedWriter(pomProperties)) {
				out.write("""
						artifactId=iu-fake-artifact
						version=1.2.3-SNAPSHOT
						""");
			}

			IuComponent.scan(ClassLoader.getSystemClassLoader(), root);

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
			assertTrue(IuIterable.remaindersAreEqual(i, scannedInterfacesIterator));
		}
	}

}