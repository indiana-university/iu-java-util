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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeRunnable;
import edu.iu.type.IuComponent;
import edu.iu.type.IuComponent.Kind;
import edu.iu.type.base.ModularClassLoader;
import edu.iu.type.base.TemporaryFile;
import jakarta.annotation.Resource;

@SuppressWarnings("javadoc")
public class ComponentTest extends IuTypeTestCase {

	@Test
	public void testAssertModuleName() {
		assertEquals("iu.util.type.impl", Component.class.getModule().getName());
	}

	@Test
	public void testClosed() throws Throwable {
		var archive = mock(ComponentArchive.class);
		when(archive.kind()).thenReturn(Kind.LEGACY_JAR);

		var loader = new URLClassLoader(new URL[0]);
		var onClose = mock(UnsafeRunnable.class);
		var component = new Component(null, loader, ModuleLayer.boot(), new ArrayDeque<>(List.of(archive)), onClose);
		component.close();

		final var beforeExtraClose = System.nanoTime();
		component.close(); // no-op does not throw
		final var sinceBeforeExtraClose = System.nanoTime() - beforeExtraClose;
		assertTrue(sinceBeforeExtraClose <= 1_000L);
		verify(onClose).run();

		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.parent()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.properties()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.classLoader()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.kind()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.version()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.interfaces()).getMessage());
		assertEquals("closed",
				assertThrows(IllegalStateException.class, () -> component.annotatedAttributes(null)).getMessage());
		assertEquals("closed",
				assertThrows(IllegalStateException.class, () -> component.annotatedTypes(null)).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.resources()).getMessage());
	}

	@Test
	public void testScannedComponentGracefullyHandlesMissingResourceAnnotation() throws Exception {
		var error = new Error();
		var path = mock(Path.class);
		var archive = mock(ComponentArchive.class);
		when(archive.kind()).thenReturn(Kind.LEGACY_JAR);
		when(archive.path()).thenReturn(path);

		var loader = spy(new URLClassLoader(new URL[0]));
		doThrow(error).when(loader).close();

		final var temp = Files.createTempDirectory(Path.of("target"), "iu-type-ComponentTest");
		try (final var component = new Component(loader, ModuleLayer.boot(), temp)) {
			assertFalse(component.annotatedAttributes(Resource.class).iterator().hasNext());
			assertFalse(component.annotatedTypes(Resource.class).iterator().hasNext());
			assertFalse(component.annotatedTypes(Documented.class).iterator().hasNext());
		}
		Files.delete(temp);
	}

	@Test
	public void testControllerCanOpenPackages() throws Exception {
		Queue<ComponentArchive> archives = new ArrayDeque<>();
		final var onClose = TemporaryFile.init(() -> {
			var runtimeArchive = ComponentArchive
					.from(new ArchiveSource(TestArchives.getComponentArchive("testruntime")));
			archives.offer(runtimeArchive);
			for (var providedDependencyArchiveSource : TestArchives.getProvidedDependencyArchives("testruntime"))
				archives.offer(ComponentArchive.from(new ArchiveSource(providedDependencyArchiveSource)));
			for (var bundledDependency : runtimeArchive.bundledDependencies())
				archives.offer(ComponentArchive.from(bundledDependency));

		});

		Queue<Path> path = new ArrayDeque<>();
		archives.forEach(a -> path.offer(a.path()));
		final var loader = new ModularClassLoader(false, path, ModuleLayer.boot(), null, controller -> {
			controller.addOpens(controller.layer().findModule("jakarta.json").get(), "jakarta.json",
					getClass().getModule());
		});

		try (var component = new Component(null, loader, ModuleLayer.boot(), archives, () -> {
			loader.close();
			onClose.run();
		})) {
			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			assertEquals("edu.iu.type.testruntime.TestRuntime", interfaces.next().name());
		}
	}

	@Test
	public void testLegacyLoadsModule() throws Exception {
		Queue<ComponentArchive> archives = new ArrayDeque<>();
		final var destroy = TemporaryFile.init(() -> {
			var runtimeArchive = ComponentArchive
					.from(new ArchiveSource(TestArchives.getComponentArchive("testruntime")));
			archives.offer(runtimeArchive);
			for (var providedDependencyArchiveSource : TestArchives.getProvidedDependencyArchives("testruntime"))
				archives.offer(ComponentArchive.from(new ArchiveSource(providedDependencyArchiveSource)));
			for (var bundledDependency : runtimeArchive.bundledDependencies())
				archives.offer(ComponentArchive.from(bundledDependency));
		});

		var path = new URL[archives.size()];
		{
			var i = 0;
			for (var archive : archives)
				path[i++] = archive.path().toUri().toURL();
		}

		final var loader = new LegacyClassLoader(false, path, ClassLoader.getSystemClassLoader());
		try (var component = new Component(null, loader, ModuleLayer.boot(), archives, () -> {
			loader.close();
			destroy.run();
		})) {
			var interfaces = component.interfaces().iterator();
			assertTrue(interfaces.hasNext());
			var next = interfaces.next();
			assertEquals("edu.iu.type.testruntime.TestRuntime", next.name());
		}
	}

	@Test
	public void testCantExtendWithWebAsDependency() throws IOException {
		var rd = TestArchives.getProvidedDependencyArchives("testruntime");
		var wd = TestArchives.getProvidedDependencyArchives("testweb");
		var deps = Arrays.copyOf(rd, rd.length + wd.length + 1);
		System.arraycopy(wd, 0, deps, rd.length, wd.length);
		deps[deps.length - 1] = TestArchives.getComponentArchive("testweb");
		assertEquals("Component must not include a web component as a dependency",
				assertThrows(IllegalArgumentException.class,
						() -> IuComponent.of(TestArchives.getComponentArchive("testruntime"), deps)).getMessage());
	}

	@Test
	public void testCantExtendWeb() throws Exception {
		try (var parent = IuComponent.of(TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"));
				var component = parent.extend(TestArchives.getComponentArchive("testweb"),
						TestArchives.getProvidedDependencyArchives("testweb"))) {
			assertEquals("Component must not extend a web component", assertThrows(IllegalArgumentException.class,
					() -> component.extend(TestArchives.getComponentArchive("testruntime"))).getMessage());
		}
	}

	@Test
	public void testParentAccessor() throws Exception {
		try (var parent = ComponentFactory.createComponent(null, null, ModuleLayer.boot(), null,
				TestArchives.getComponentArchive("testruntime"),
				TestArchives.getProvidedDependencyArchives("testruntime"));
				var component = parent.extend(TestArchives.getComponentArchive("testweb"),
						TestArchives.getProvidedDependencyArchives("testweb"))) {
			assertSame(parent, component.parent());
			assertEquals("true", component.properties().get("sample.type.property"));
		}
	}

}
