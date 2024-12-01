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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.test.IuTestLogger;
import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;
import edu.iu.type.base.TemporaryFile;

@SuppressWarnings("javadoc")
public class TypeContainerBootstrapTest {

	@Test
	public void testEmpty() throws Exception {
		System.getProperties().remove("iu.boot.components");

		try (final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			assertDoesNotThrow(typeContainerBootstrap::run);
		}
	}

	@Test
	public void testInit() throws Exception {
		final var value = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var artifactId = "a" + IdGenerator.generateId().replace('_', '.');
		final var groupId = IdGenerator.generateId();
		final var version = "1.2.3";
		final var primary = TemporaryFile.init(p -> {
			try (final var primaryJar = Files.newOutputStream(p); //
					final var jar = new JarOutputStream(primaryJar)) {
				final var manifest = new Manifest();
				manifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
				jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				manifest.write(jar);
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("module-info.class"));
				try (final var mod = Files.newInputStream(Path.of("target/classes/module-info.class"))) {
					IuStream.copy(mod, jar);
				}
				jar.closeEntry();

				jar.putNextEntry(new JarEntry(name));
				jar.write(IuText.utf8(value));
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
				final var mavenProps = new Properties();
				mavenProps.setProperty("groupId", groupId);
				mavenProps.setProperty("artifactId", artifactId);
				mavenProps.setProperty("version", version);
				mavenProps.store(jar, null);
				jar.closeEntry();
			}
			return p;
		});

		final var archiveName = TemporaryFile.of(InputStream.nullInputStream());
		System.setProperty("iu.boot.components", archiveName.toString());

		try (final var mockTypeContainerArchive = mockConstruction(TypeContainerArchive.class, (a, ctx) -> {
			when(a.api()).thenReturn(new Path[0]);
			when(a.support()).thenReturn(new Path[0]);
			when(a.lib()).thenReturn(new Path[0]);
			when(a.primary()).thenReturn(primary);
		}); final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");
			assertDoesNotThrow(typeContainerBootstrap::run);
			assertDoesNotThrow(typeContainerBootstrap::close);
		}
	}

	@Test
	public void testInitSupport() throws Exception {
		final var value = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var artifactId = "a" + IdGenerator.generateId().replace('_', '.');
		final var groupId = IdGenerator.generateId();
		final var version = "1.2.3";
		final var primary = TemporaryFile.init(p -> {
			try (final var primaryJar = Files.newOutputStream(p); //
					final var jar = new JarOutputStream(primaryJar)) {
				final var manifest = new Manifest();
				manifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
				jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				manifest.write(jar);
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("module-info.class"));
				try (final var mod = Files.newInputStream(Path.of("target/classes/module-info.class"))) {
					IuStream.copy(mod, jar);
				}
				jar.closeEntry();

				jar.putNextEntry(new JarEntry(name));
				jar.write(IuText.utf8(value));
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
				final var mavenProps = new Properties();
				mavenProps.setProperty("groupId", groupId);
				mavenProps.setProperty("artifactId", artifactId);
				mavenProps.setProperty("version", version);
				mavenProps.store(jar, null);
				jar.closeEntry();
			}
			return p;
		});

		final var libArtifactId = "l" + IdGenerator.generateId().replace('_', '.');
		final var libGroupId = IdGenerator.generateId();
		final var libVersion = "2.3.4";
		final var lib = TemporaryFile.init(p -> {
			try (final var primaryJar = Files.newOutputStream(p); //
					final var jar = new JarOutputStream(primaryJar)) {
				final var manifest = new Manifest();
				manifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
				jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				manifest.write(jar);
				jar.closeEntry();

				jar.putNextEntry(
						new JarEntry("META-INF/maven/" + libGroupId + "/" + libArtifactId + "/pom.properties"));
				final var mavenProps = new Properties();
				mavenProps.setProperty("groupId", libGroupId);
				mavenProps.setProperty("artifactId", libArtifactId);
				mavenProps.setProperty("version", libVersion);
				mavenProps.store(jar, null);
				jar.closeEntry();
			}
			return p;
		});

		final var support = TemporaryFile.of(InputStream.nullInputStream());
		final var archiveName = TemporaryFile.of(InputStream.nullInputStream());
		System.setProperty("iu.boot.components", archiveName.toString());

		try (final var mockTypeContainerArchive = mockConstruction(TypeContainerArchive.class, (a, ctx) -> {
			when(a.api()).thenReturn(new Path[0]);
			when(a.support()).thenReturn(new Path[] { support });
			when(a.lib()).thenReturn(new Path[] { lib });
			when(a.primary()).thenReturn(primary);
		}); final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init support ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy support ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");
			assertDoesNotThrow(typeContainerBootstrap::run);
		}
	}

	@Test
	public void testInitError() throws Exception {
		final var value = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var artifactId = "a" + IdGenerator.generateId().replace('_', '.');
		final var groupId = IdGenerator.generateId();
		final var version = "1.2.3";
		final var primary = TemporaryFile.init(p -> {
			try (final var primaryJar = Files.newOutputStream(p); //
					final var jar = new JarOutputStream(primaryJar)) {
				final var manifest = new Manifest();
				manifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
				jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				manifest.write(jar);
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("module-info.class"));
				try (final var mod = Files.newInputStream(Path.of("target/classes/module-info.class"))) {
					IuStream.copy(mod, jar);
				}
				jar.closeEntry();

				jar.putNextEntry(new JarEntry(name));
				jar.write(IuText.utf8(value));
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
				final var mavenProps = new Properties();
				mavenProps.setProperty("groupId", groupId);
				mavenProps.setProperty("artifactId", artifactId);
				mavenProps.setProperty("version", version);
				mavenProps.store(jar, null);
				jar.closeEntry();
			}
			return p;
		});

		final var archiveName = TemporaryFile.of(InputStream.nullInputStream());
		System.setProperty("iu.boot.components", archiveName.toString());

		final var error = new IllegalStateException();
		try (final var mockTypeContainerArchive = mockConstruction(TypeContainerArchive.class, (a, ctx) -> {
			when(a.api()).thenReturn(new Path[0]);
			when(a.support()).thenReturn(new Path[0]);
			when(a.lib()).thenThrow(error);
			when(a.primary()).thenReturn(primary);
		}); final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");
			assertSame(error, assertThrows(IllegalStateException.class, typeContainerBootstrap::run));
		}
	}

	@Test
	public void testInitSupportError() throws Exception {
		final var value = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var artifactId = "a" + IdGenerator.generateId().replace('_', '.');
		final var groupId = IdGenerator.generateId();
		final var version = "1.2.3";
		final var primary = TemporaryFile.init(p -> {
			try (final var primaryJar = Files.newOutputStream(p); //
					final var jar = new JarOutputStream(primaryJar)) {
				final var manifest = new Manifest();
				manifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
				jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				manifest.write(jar);
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("module-info.class"));
				try (final var mod = Files.newInputStream(Path.of("target/classes/module-info.class"))) {
					IuStream.copy(mod, jar);
				}
				jar.closeEntry();

				jar.putNextEntry(new JarEntry(name));
				jar.write(IuText.utf8(value));
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
				final var mavenProps = new Properties();
				mavenProps.setProperty("groupId", groupId);
				mavenProps.setProperty("artifactId", artifactId);
				mavenProps.setProperty("version", version);
				mavenProps.store(jar, null);
				jar.closeEntry();
			}
			return p;
		});

		final var support = TemporaryFile.of(InputStream.nullInputStream());
		final var archiveName = TemporaryFile.of(InputStream.nullInputStream());
		System.setProperty("iu.boot.components", archiveName.toString());

		final var error = new IllegalStateException();
		try (final var mockTypeContainerArchive = mockConstruction(TypeContainerArchive.class, (a, ctx) -> {
			when(a.api()).thenReturn(new Path[0]);
			when(a.support()).thenReturn(new Path[] { support });
			when(a.lib()).thenThrow(error);
			when(a.primary()).thenReturn(primary);
		}); final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init support ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy support ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");
			assertSame(error, assertThrows(IllegalStateException.class, typeContainerBootstrap::run));
		}
	}

	@Test
	public void testInitCloseError() throws Exception {
		final var value = IdGenerator.generateId();
		final var name = IdGenerator.generateId();
		final var artifactId = "a" + IdGenerator.generateId().replace('_', '.');
		final var groupId = IdGenerator.generateId();
		final var version = "1.2.3";
		final var primary = TemporaryFile.init(p -> {
			try (final var primaryJar = Files.newOutputStream(p); //
					final var jar = new JarOutputStream(primaryJar)) {
				final var manifest = new Manifest();
				manifest.getMainAttributes().put(Name.MANIFEST_VERSION, "1.0");
				jar.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
				manifest.write(jar);
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("module-info.class"));
				try (final var mod = Files.newInputStream(Path.of("target/classes/module-info.class"))) {
					IuStream.copy(mod, jar);
				}
				jar.closeEntry();

				jar.putNextEntry(new JarEntry(name));
				jar.write(IuText.utf8(value));
				jar.closeEntry();

				jar.putNextEntry(new JarEntry("META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties"));
				final var mavenProps = new Properties();
				mavenProps.setProperty("groupId", groupId);
				mavenProps.setProperty("artifactId", artifactId);
				mavenProps.setProperty("version", version);
				mavenProps.store(jar, null);
				jar.closeEntry();
			}
			return p;
		});

		final var archiveName = TemporaryFile.of(InputStream.nullInputStream());
		System.setProperty("iu.boot.components", archiveName.toString());

		final var error = new IllegalStateException();
		try (final var mockTypeContainerArchive = mockConstruction(TypeContainerArchive.class, (a, ctx) -> {
			when(a.api()).thenReturn(new Path[0]);
			when(a.support()).thenReturn(new Path[0]);
			when(a.lib()).thenReturn(new Path[0]);
			when(a.primary()).thenReturn(primary);
			doThrow(error).when(a).close();
		}); final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before init container");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after init base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy Component .*");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "before destroy base ");
			IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after destroy container");
			assertDoesNotThrow(typeContainerBootstrap::run);
			assertSame(error, assertThrows(IllegalStateException.class, typeContainerBootstrap::close));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void testInitResourceRef() {
		class A {
		}

		final var aResource = mock(IuResource.class);
		when(aResource.type()).thenReturn(IuType.of(A.class));
		when(aResource.priority()).thenReturn(-1);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create " + aResource);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + aResource);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
				"before join TypeContainerResource \\[iu.util.type.container/" + A.class.getName().replace("$", "\\$")
						+ "/\\d+, priority=-1, started=true, error=null\\]");

		final var name = IdGenerator.generateId();
		final var bResource = mock(IuResource.class);
		when(bResource.name()).thenReturn(name);
		when(bResource.type()).thenReturn(IuType.of(A.class));
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create " + bResource);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + bResource);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
				"before join TypeContainerResource \\[iu.util.type.container/" + A.class.getName().replace("$", "\\$")
						+ "/" + name + "/\\d+, priority=0, started=true, error=null\\]");

		final var resourceRef = mock(IuResourceReference.class);
		when(resourceRef.type()).thenReturn(IuType.of(A.class));
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
				"bind " + resourceRef + " " + aResource);

		final var component = mock(IuComponent.class);
		when(component.resources()).thenReturn((Iterable) IuIterable.iter(aResource, bResource));
		when(component.resourceReferences()).thenReturn((Iterable) IuIterable.iter(resourceRef));
		final var componentsToInitialize = IuIterable.iter(component);
		assertDoesNotThrow(() -> TypeContainerBootstrap.initializeComponents(componentsToInitialize));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testInitResourceFirstError() {
		class A {
		}

		final var aResource = mock(IuResource.class);
		when(aResource.type()).thenReturn(IuType.of(A.class));
		when(aResource.priority()).thenReturn(-1);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create " + aResource);

		final var error = new IllegalStateException();
		final var name = IdGenerator.generateId();
		final var bResource = mock(IuResource.class);
		when(bResource.name()).thenReturn(name);
		when(bResource.type()).thenReturn(IuType.of(A.class));
		when(bResource.get()).thenThrow(error);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create " + bResource);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + bResource);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
				"before join TypeContainerResource \\[iu.util.type.container/" + A.class.getName().replace("$", "\\$")
						+ "/" + name + "/\\d+, priority=0, started=true, error=null\\]");
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.SEVERE, "fail resource " + bResource,
				IllegalStateException.class, a -> a == error);

		final var component = mock(IuComponent.class);
		when(component.resources()).thenReturn((Iterable) IuIterable.iter(aResource, bResource));
		final var componentsToInitialize = (Iterable) IuIterable.iter(component);
		assertSame(error, assertThrows(IllegalStateException.class,
				() -> TypeContainerBootstrap.initializeComponents(componentsToInitialize)));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testInitResourceSecondError() {
		class A {
		}

		final var error = new IllegalStateException();
		final var aResource = mock(IuResource.class);
		when(aResource.type()).thenReturn(IuType.of(A.class));
		when(aResource.priority()).thenReturn(-1);
		when(aResource.get()).thenThrow(error);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create " + aResource);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + aResource);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
				"before join TypeContainerResource \\[iu.util.type.container/" + A.class.getName().replace("$", "\\$")
						+ "/\\d+, priority=-1, started=true, error=null\\]");
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.SEVERE, "fail resource " + aResource,
				IllegalStateException.class, a -> a == error);

		final var name = IdGenerator.generateId();
		final var bResource = mock(IuResource.class);
		when(bResource.name()).thenReturn(name);
		when(bResource.type()).thenReturn(IuType.of(A.class));
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE, "after create " + bResource);
		IuTestLogger.expect(TypeContainerResource.class.getName(), Level.FINE, "init resource " + bResource);
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.FINE,
				"before join TypeContainerResource \\[iu.util.type.container/" + A.class.getName().replace("$", "\\$")
						+ "/" + name + "/\\d+, priority=0, started=true, error=null\\]");

		final var component = mock(IuComponent.class);
		when(component.resources()).thenReturn((Iterable) IuIterable.iter(aResource, bResource));
		final var componentsToInitialize = IuIterable.iter(component);
		assertSame(error, assertThrows(IllegalStateException.class,
				() -> TypeContainerBootstrap.initializeComponents(componentsToInitialize)));
	}

}
