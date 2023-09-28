package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuComponent.Kind;
import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class ComponentTest {

	@Test
	public void testAssertModuleName() {
		assertEquals("iu.util.type.impl", Component.class.getModule().getName());
	}

	@Test
	public void testClosed() throws IOException {
		var path = mock(Path.class);
		var archive = mock(ComponentArchive.class);
		when(archive.kind()).thenReturn(Kind.LEGACY_JAR);
		when(archive.path()).thenReturn(path);

		var loader = spy(new URLClassLoader(new URL[0]));
		var finder = mock(ComponentModuleFinder.class);
		var component = new Component(null, null, loader, finder, new ArrayDeque<>(List.of(archive)));
		try (var mockFiles = mockStatic(Files.class)) {
			component.close();
			verify(finder).close();
			verify(loader).close();
			mockFiles.verify(() -> Files.delete(path));
		}
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.parent()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.controller()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.properties()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.classLoader()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.kind()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.version()).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.interfaces()).getMessage());
		assertEquals("closed",
				assertThrows(IllegalStateException.class, () -> component.annotatedTypes(null)).getMessage());
		assertEquals("closed", assertThrows(IllegalStateException.class, () -> component.resources()).getMessage());
	}

	@Test
	public void testHandlesCloseErrors() throws IOException {
		var error = new Error();
		var path = mock(Path.class);
		var archive = mock(ComponentArchive.class);
		when(archive.kind()).thenReturn(Kind.LEGACY_JAR);
		when(archive.path()).thenReturn(path);

		var loader = spy(new URLClassLoader(new URL[0]));
		doThrow(error).when(loader).close();

		var finder = mock(ComponentModuleFinder.class);
		doThrow(error).when(finder).close();

		var component = new Component(null, null, loader, finder, new ArrayDeque<>(List.of(archive)));
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.delete(path)).thenThrow(error);
			component.close();
		}
	}

	@Test
	public void testControllerCanOpenPackages() throws IOException {
		try (var mockIuType = mockStatic(IuType.class)) {
			mockIuType.when(() -> IuType.of(any(Class.class))).then(a -> {
				var c = (Class<?>) a.getArgument(0);
				var type = mock(IuType.class);
				when(type.name()).thenReturn(c.getName());
				return type;
			});
			Queue<ComponentArchive> archives = new ArrayDeque<>();
			var runtimeArchive = ComponentArchive
					.from(new ArchiveSource(TestArchives.getComponentArchive("testruntime")));
			archives.offer(runtimeArchive);
			for (var providedDependencyArchiveSource : TestArchives.getProvidedDependencyArchives("testruntime"))
				archives.offer(ComponentArchive.from(new ArchiveSource(providedDependencyArchiveSource)));
			for (var bundledDependency : runtimeArchive.bundledDependencies())
				archives.offer(ComponentArchive.from(bundledDependency));

			var path = new Path[archives.size()];
			{
				var i = 0;
				for (var archive : archives)
					path[i++] = archive.path();
			}

			var moduleFinder = new ComponentModuleFinder(path);
			var moduleNames = moduleFinder.findAll().stream().map(ref -> ref.descriptor().name())
					.collect(Collectors.toList());

			var configuration = Configuration.resolveAndBind( //
					moduleFinder, List.of(ModuleLayer.boot().configuration()), ModuleFinder.of(), moduleNames);

			var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(ModuleLayer.boot()), null);
			controller.addOpens(controller.layer().findModule("jakarta.json").get(), "jakarta.json",
					getClass().getModule());

			try (var component = new Component(null, controller,
					controller.layer().findLoader(moduleNames.iterator().next()), moduleFinder, archives)) {

				var interfaces = component.interfaces().iterator();
				assertTrue(interfaces.hasNext());
				assertEquals("edu.iu.type.testruntime.TestRuntime", interfaces.next().name());
				assertTrue(interfaces.hasNext());
				assertTrue(interfaces.next().name().startsWith("jakarta.json"));
			}
		}
	}

	@Test
	public void testLegacyDoesntOpenPackages() throws IOException {
		try (var mockIuType = mockStatic(IuType.class)) {
			mockIuType.when(() -> IuType.of(any(Class.class))).then(a -> {
				var c = (Class<?>) a.getArgument(0);
				var type = mock(IuType.class);
				when(type.name()).thenReturn(c.getName());
				return type;
			});
			Queue<ComponentArchive> archives = new ArrayDeque<>();
			var runtimeArchive = ComponentArchive
					.from(new ArchiveSource(TestArchives.getComponentArchive("testruntime")));
			archives.offer(runtimeArchive);
			for (var providedDependencyArchiveSource : TestArchives.getProvidedDependencyArchives("testruntime"))
				archives.offer(ComponentArchive.from(new ArchiveSource(providedDependencyArchiveSource)));
			for (var bundledDependency : runtimeArchive.bundledDependencies())
				archives.offer(ComponentArchive.from(bundledDependency));

			var path = new URL[archives.size()];
			{
				var i = 0;
				for (var archive : archives)
					path[i++] = archive.path().toUri().toURL();
			}

			try (var loader = new LegacyClassLoader(Set.of(), path, null)) {
				try (var component = new Component(null, null, loader, null, archives)) {
					var interfaces = component.interfaces().iterator();
					assertFalse(interfaces.hasNext(), () -> interfaces.next().name());
				}
			}
		}
	}

}
