package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.UnsafeRunnable;

@SuppressWarnings("javadoc")
public class ModularClassLoaderTest extends IuTypeTestCase {

	private Deque<UnsafeRunnable> onTeardown = new ArrayDeque<>();

	private ModularClassLoader createModularLoader(String componentName, ClassLoader parent) throws IOException {
		final Queue<ComponentArchive> archives = new ArrayDeque<>();
		onTeardown.push(() -> {
			while (!archives.isEmpty())
				Files.delete(archives.poll().path());
		});

		final var primary = ComponentArchive.from(new ArchiveSource(TestArchives.getComponentArchive(componentName)));
		final Queue<Path> modulepath = new ArrayDeque<>();
		modulepath.offer(primary.path());

		final Queue<ComponentArchive> classpath = new ArrayDeque<>(archives.size());
		for (final var lib : primary.bundledDependencies()) {
			final var archive = ComponentArchive.from(lib);
			archives.offer(archive);
			if (archive.kind().isModular())
				modulepath.offer(archive.path());
			else
				classpath.offer(archive);
		}
		for (final var dep : TestArchives.getProvidedDependencyArchives(componentName)) {
			final var archive = ComponentArchive.from(new ArchiveSource(dep));
			archives.offer(archive);
			if (archive.kind().isModular())
				modulepath.offer(archive.path());
			else
				classpath.offer(archive);
		}

		final var moduleFinder = new ComponentModuleFinder(modulepath.toArray(new Path[modulepath.size()]));
		onTeardown.push(moduleFinder::close);

		return new ModularClassLoader(archives.iterator().next().kind().isWeb(), moduleFinder, classpath, parent);
	}

	@Test
	public void testNullModuleIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertTrue(loader.isOpen(null, null));
	}

	@Test
	public void testClassResourceIsNotEncapsulaed() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var moduleRef = mock(ModuleReference.class);
		final var desc = mock(ModuleDescriptor.class);
		when(desc.name()).thenReturn("foo.bar");
		when(moduleRef.descriptor()).thenReturn(desc);
		assertTrue(loader.isOpen(moduleRef, ".class"));
	}

	@Test
	public void testRootPackageIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var moduleRef = mock(ModuleReference.class);
		final var desc = mock(ModuleDescriptor.class);
		when(desc.name()).thenReturn("foo.bar");
		when(moduleRef.descriptor()).thenReturn(desc);
		assertTrue(loader.isOpen(moduleRef, "foo"));
		assertTrue(loader.isOpen(moduleRef, "/foo"));
	}

	@Test
	public void testMetaInfIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var moduleRef = mock(ModuleReference.class);
		final var desc = mock(ModuleDescriptor.class);
		when(desc.name()).thenReturn("foo.bar");
		when(moduleRef.descriptor()).thenReturn(desc);
		assertTrue(loader.isOpen(moduleRef, "META-INF/whatever"));
	}

	@Test
	public void testOpenModulePackagesAreOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var moduleRef = mock(ModuleReference.class);
		final var desc = mock(ModuleDescriptor.class);
		when(desc.name()).thenReturn("foo.bar");
		when(desc.packages()).thenReturn(Set.of("bar.foo"));
		when(desc.isOpen()).thenReturn(true);
		when(moduleRef.descriptor()).thenReturn(desc);
		assertFalse(loader.isOpen(moduleRef, "foo/bar/baz"));
		assertTrue(loader.isOpen(moduleRef, "bar/foo/baz"));
		assertTrue(loader.isOpen(moduleRef, "/bar/foo/baz"));
	}

	@Test
	public void testOpenPackagesAreOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var moduleRef = mock(ModuleReference.class);
		final var desc = mock(ModuleDescriptor.class);
		when(desc.name()).thenReturn("foo.bar");
		when(desc.packages()).thenReturn(Set.of("bar.foo", "foo.bar"));
		final var opens = mock(ModuleDescriptor.Opens.class);
		when(opens.source()).thenReturn("bar.foo");
		when(opens.isQualified()).thenReturn(false);
		final var opens2 = mock(ModuleDescriptor.Opens.class);
		when(opens2.source()).thenReturn("foo.bar");
		when(opens2.isQualified()).thenReturn(true);
		when(desc.opens()).thenReturn(Set.of(opens, opens2));
		when(moduleRef.descriptor()).thenReturn(desc);
		assertFalse(loader.isOpen(moduleRef, "foo/bar/baz"));
		assertTrue(loader.isOpen(moduleRef, "bar/foo/baz"));
		assertTrue(loader.isOpen(moduleRef, "/bar/foo/baz"));
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
	public void testFindResourceByModule() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var jsonLicense = loader.findResource("jakarta.json", "META-INF/LICENSE.md");
		assertNotNull(jsonLicense);
		assertNull(loader.findResource("iu.util.type.testruntime", "META-INF/LICENSE.md"));
		assertNull(loader.findResource("foo.bar", "META-INF/LICENSE.md"));
		assertNotNull(loader.findResource(null, "META-INF/LICENSE.md"));
		assertNull(loader.findResource(null, "foo.bar"));
	}

}
