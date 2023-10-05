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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTest;
import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuComponent.Kind;
import iu.type.TestArchives;

@SuppressWarnings("javadoc")
public class IuComponentTest {

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

			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.legacy.LegacyInterface", interfaces.next().name());
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.legacy.NotResource", interfaces.next().name());
			assertFalse(interfaces.hasNext(), () -> interfaces.next().name());

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
		IuTestLogger.expect("iu.type.Component", Level.WARNING, "Invalid class invalid.*",
				ClassNotFoundException.class);
		try (var parent = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"));
				var component = parent.extend(TestArchives.getComponentArchive("testcomponent"))) {

			assertEquals(Kind.MODULAR_JAR, component.kind());
			assertEquals("iu-java-type-testcomponent", component.version().name());
			assertEquals(IuTest.getProperty("project.version"), component.version().implementationVersion());

			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.type.testruntime.TestRuntime", interfaces.next().name());
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.type.testcomponent.TestBean", interfaces.next().name());
			assertFalse(interfaces.hasNext());

			var resources = component.resources().iterator();
			assertTrue(resources.hasNext());
			assertEquals("TestResource", resources.next().name());
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

			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.type.testruntime.TestRuntime", interfaces.next().name());
			assertFalse(interfaces.hasNext());

			var resources = component.resources().iterator();
			assertTrue(resources.hasNext());
			assertEquals("index.html", resources.next().name());
			assertTrue(resources.hasNext());
			assertEquals("WEB-INF/web.xml", resources.next().name());
			assertFalse(resources.hasNext(), () -> resources.next().name());
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

			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.legacy.LegacyInterface", interfaces.next().name());
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.legacy.NotResource", interfaces.next().name());
			assertFalse(interfaces.hasNext(), () -> interfaces.next().name());

			var resources = component.resources().iterator();
			assertTrue(resources.hasNext());
			assertEquals("two", resources.next().name());
			assertTrue(resources.hasNext());
			assertEquals("LegacyResource", resources.next().name());
			assertTrue(resources.hasNext());
			assertEquals("index.jsp", resources.next().name());
			assertTrue(resources.hasNext());
			assertEquals("WEB-INF/web.xml", resources.next().name());
			assertFalse(resources.hasNext(), () -> resources.next().name());
		}
	}

}