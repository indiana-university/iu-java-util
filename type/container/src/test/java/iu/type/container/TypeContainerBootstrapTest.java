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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
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
import edu.iu.type.IuResourceKey;
import edu.iu.type.IuResourceReference;
import edu.iu.type.IuType;
import edu.iu.type.base.TemporaryFile;
import edu.iu.type.bundle.IuTypeBundle;
import iu.type.container.spi.IuEnvironment;

@SuppressWarnings("javadoc")
public class TypeContainerBootstrapTest extends TypeContainerTestCase {

	@Test
	public void testEmpty() throws Exception {
		System.getProperties().remove("iu.boot.components");

		final var env = mock(IuEnvironment.class);
		final var loader = mock(ServiceLoader.class);
		when(loader.iterator()).thenReturn(IuIterable.iter(env).iterator());

		try (final var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(IuEnvironment.class)).thenReturn(loader);

			try (final var typeContainerBootstrap = assertDoesNotThrow(TypeContainerBootstrap::new)) {
				assertDoesNotThrow(typeContainerBootstrap::run);
			}
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

				jar.putNextEntry(new JarEntry("iu/type/container/spi/IuEnvironment.class"));
				try (final var mod = Files
						.newInputStream(Path.of("target/classes/iu/type/container/spi/IuEnvironment.class"))) {
					IuStream.copy(mod, jar);
				}
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

		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.CONFIG,
				"environment DefaultEnvironment \\[config=.*\\]; Component \\[.*\\]");

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

				jar.putNextEntry(new JarEntry("iu/type/container/spi/IuEnvironment.class"));
				try (final var mod = Files
						.newInputStream(Path.of("target/classes/iu/type/container/spi/IuEnvironment.class"))) {
					IuStream.copy(mod, jar);
				}
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

		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.CONFIG,
				"environment DefaultEnvironment \\[config=.*\\]; Component \\[.*\\]");

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

				jar.putNextEntry(new JarEntry("iu/type/container/spi/IuEnvironment.class"));
				try (final var mod = Files
						.newInputStream(Path.of("target/classes/iu/type/container/spi/IuEnvironment.class"))) {
					IuStream.copy(mod, jar);
				}
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

				jar.putNextEntry(new JarEntry("iu/type/container/spi/IuEnvironment.class"));
				try (final var mod = Files
						.newInputStream(Path.of("target/classes/iu/type/container/spi/IuEnvironment.class"))) {
					IuStream.copy(mod, jar);
				}
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

				jar.putNextEntry(new JarEntry("iu/type/container/spi/IuEnvironment.class"));
				try (final var mod = Files
						.newInputStream(Path.of("target/classes/iu/type/container/spi/IuEnvironment.class"))) {
					IuStream.copy(mod, jar);
				}
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

		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.CONFIG,
				"environment DefaultEnvironment \\[config=.*\\]; Component \\[.*\\]");

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
		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.CONFIG,
				"environment DefaultEnvironment [config=" + config + "]; " + component);

		when(component.classLoader()).thenReturn(ClassLoader.getSystemClassLoader());
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
		when(component.classLoader()).thenReturn(ClassLoader.getSystemClassLoader());
		when(component.resources()).thenReturn((Iterable) IuIterable.iter(aResource, bResource));
		final var componentsToInitialize = (Iterable) IuIterable.iter(component);

		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.CONFIG,
				"environment DefaultEnvironment [config=" + config + "]; " + component);

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
		when(component.classLoader()).thenReturn(ClassLoader.getSystemClassLoader());
		when(component.resources()).thenReturn((Iterable) IuIterable.iter(aResource, bResource));
		final var componentsToInitialize = IuIterable.iter(component);

		IuTestLogger.expect(TypeContainerBootstrap.class.getName(), Level.CONFIG,
				"environment DefaultEnvironment [config=" + config + "]; " + component);

		assertSame(error, assertThrows(IllegalStateException.class,
				() -> TypeContainerBootstrap.initializeComponents(componentsToInitialize)));
	}

	@Test
	public void testInitEnvironment() throws IOException {
		final var propBuffer = new ByteArrayOutputStream();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var props = new Properties();
		props.setProperty("application", application);
		props.setProperty("environment", environment);
		props.store(propBuffer, null);

		final var env = mock(IuEnvironment.class);
		final var serviceIter = mock(ServiceLoader.class);
		when(serviceIter.iterator()).thenReturn(IuIterable.iter(env).iterator());

		System.clearProperty("iu.application");
		System.setProperty("iu.environment", IdGenerator.generateId());
		try (final var loader = new URLClassLoader(new URL[0]) {
			@Override
			public InputStream getResourceAsStream(String name) {
				if ("META-INF/iu-type-container.properties".equals(name))
					return new ByteArrayInputStream(propBuffer.toByteArray());
				return super.getResourceAsStream(name);
			}
		}; final var mockServiceLoader = mockStatic(ServiceLoader.class)) {
			mockServiceLoader.when(() -> ServiceLoader.load(IuEnvironment.class)).thenReturn(serviceIter);
			final var component = mock(IuComponent.class);
			when(component.classLoader()).thenReturn(loader);
			TypeContainerBootstrap.initEnvironment(component);
		} finally {
			System.clearProperty("iu.environment");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testResolveResource() {
		Map<IuResourceKey<?>, IuResource<?>> boundResources = new LinkedHashMap<>();
		Map<IuType<?, ?>, Object> refInstance = new LinkedHashMap<>();
		Map<IuComponent, IuEnvironment> envByComp = new LinkedHashMap<>();

		final var component = mock(IuComponent.class);
		final var env = mock(IuEnvironment.class);
		envByComp.put(component, env);

		final var name = IdGenerator.generateId();
		final var resource = mock(IuResource.class);
		final var resourceRef = mock(IuResourceReference.class);
		when(resourceRef.name()).thenReturn(name);
		when(resourceRef.type()).thenReturn(IuType.of(String.class));
		final var key = IuResourceKey.from(resourceRef);
		boundResources.put(key, resource);

		assertSame(resource, TypeContainerBootstrap.resolveResource(boundResources, refInstance, envByComp, component,
				resourceRef, key));
	}

	static class ResolvedResource {
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testResolveEnvResource() {
		getClass().getModule().addOpens(getClass().getPackageName(), IuTypeBundle.getModule());

		Map<IuResourceKey<?>, IuResource<?>> boundResources = new LinkedHashMap<>();
		Map<IuType<?, ?>, Object> refInstance = new LinkedHashMap<>();
		Map<IuComponent, IuEnvironment> envByComp = new LinkedHashMap<>();

		final var name = IdGenerator.generateId();
		final var value = IdGenerator.generateId();

		final var component = mock(IuComponent.class);
		final var env = mock(IuEnvironment.class);
		when(env.resolve(name, String.class)).thenReturn(value);
		envByComp.put(component, env);

		final var refType = IuType.of(ResolvedResource.class);
		final var resourceRef = mock(IuResourceReference.class);
		when(resourceRef.name()).thenReturn(name);
		when(resourceRef.type()).thenReturn(IuType.of(String.class));
		when(resourceRef.referrerType()).thenReturn(refType);
		final var key = IuResourceKey.from(resourceRef);

		final var resource = assertInstanceOf(EnvironmentResource.class, TypeContainerBootstrap
				.resolveResource(boundResources, refInstance, envByComp, component, resourceRef, key));
		assertEquals(value, resource.get());

		final var instance = assertInstanceOf(ResolvedResource.class, refInstance.get(refType));

		final var resource2 = assertInstanceOf(EnvironmentResource.class, TypeContainerBootstrap
				.resolveResource(boundResources, refInstance, envByComp, component, resourceRef, key));
		assertEquals(value, resource2.get());
		assertSame(instance, refInstance.get(refType));
	}

	static class CorruptResource {
		CorruptResource(boolean isCorrupt) {
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testResolveCorruptResource() {
		getClass().getModule().addOpens(getClass().getPackageName(), IuTypeBundle.getModule());

		Map<IuResourceKey<?>, IuResource<?>> boundResources = new LinkedHashMap<>();
		Map<IuType<?, ?>, Object> refInstance = new LinkedHashMap<>();
		Map<IuComponent, IuEnvironment> envByComp = new LinkedHashMap<>();

		final var name = IdGenerator.generateId();

		final var component = mock(IuComponent.class);
		final var env = mock(IuEnvironment.class);
		envByComp.put(component, env);

		final var refType = IuType.of(CorruptResource.class);
		final var resourceRef = mock(IuResourceReference.class);
		when(resourceRef.name()).thenReturn(name);
		when(resourceRef.type()).thenReturn(IuType.of(String.class));
		when(resourceRef.referrerType()).thenReturn(refType);
		final var key = IuResourceKey.from(resourceRef);

		final var error = assertThrows(NullPointerException.class, () -> TypeContainerBootstrap
				.resolveResource(boundResources, refInstance, envByComp, component, resourceRef, key));
		assertEquals("Missing resource binding " + key, error.getMessage());
		assertInstanceOf(IllegalArgumentException.class, error.getCause());
		assertEquals("IuType[CorruptResource] missing constructor <init>()", error.getCause().getMessage());
	}

}
