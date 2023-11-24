package iu.type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
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
		archives.offer(primary);
		for (final var lib : primary.bundledDependencies())
			archives.offer(ComponentArchive.from(lib));

		for (final var dep : TestArchives.getProvidedDependencyArchives(componentName))
			archives.offer(ComponentArchive.from(new ArchiveSource(dep)));

		final var loader = new ModularClassLoader(archives, parent);
		onTeardown.push(loader::close);
		return loader;
	}

	@AfterEach
	public void teardown() throws Throwable {
		while (!onTeardown.isEmpty())
			onTeardown.pop().run();
	}

	@Test
	public void testNullModuleIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertTrue(loader.isOpen(null, null));
	}

	@Test
	public void testClassResourceIsNotEncapsulaed() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		assertTrue(loader.isOpen(module, ".class"));
	}

	@Test
	public void testRootPackageIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		assertTrue(loader.isOpen(module, "foo"));
		assertTrue(loader.isOpen(module, "/foo"));
	}

	@Test
	public void testMetaInfIsOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		assertTrue(loader.isOpen(module, "META-INF/whatever"));
	}

	@Test
	public void testOpenPackagesAreOpen() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var module = mock(Module.class);
		when(module.getName()).thenReturn("foo.bar");
		when(module.isExported("bar.foo")).thenReturn(true);
		when(module.isExported("foo.bar")).thenReturn(true);
		when(module.isOpen("bar.foo")).thenReturn(true);
		assertFalse(loader.isOpen(module, "foo/bar/baz"));
		assertTrue(loader.isOpen(module, "bar/foo/baz"));
		assertTrue(loader.isOpen(module, "/bar/foo/baz"));
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
		assertEquals(3, expected.size());
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
	public void testFindsClassInModule() throws IOException, ClassNotFoundException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "edu.iu.type.testruntime.TestRuntime";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testFindsClassInClasspath() throws IOException, ClassNotFoundException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "jakarta.ejb.EJB";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testFindsClassInBaseModule() throws IOException, ClassNotFoundException {
		final var loader = createModularLoader("testruntime", null);
		final var className = "java.net.http.HttpClient";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testFindsClassInWebModule() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "edu.iu.type.testweb.TestServlet";
		final var loaded = loader.loadClass(className, true);
		assertSame(loader.loadClass(className), loaded);
	}

	@Test
	public void testFindsClassInParentModule() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "edu.iu.type.testruntime.TestRuntime";
		assertSame(loader.findClass(className), loader.loadClass(className));
	}

	@Test
	public void testDoesntFindClassDelegatedToParent() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "jakarta.ejb.EJB";
		assertThrows(ClassNotFoundException.class, () -> loader.findClass(className));
	}

	@Test
	public void testLoadsClassDelegatedToParent() throws IOException, ClassNotFoundException {
		final var parent = createModularLoader("testruntime", null);
		final var loader = createModularLoader("testweb", parent);
		final var className = "org.apache.commons.lang.StringEscapeUtils";
		final var ejb = loader.loadClass(className, true);
		assertSame(ejb, parent.loadClass(className));
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
	public void testFindModuleResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertNotNull(loader.findResource("module-info.class"));
	}

	@Test
	public void testFindNoResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertNull(loader.findResource("foo/bar"));
	}

	@Test
	public void testFindClasspathResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		assertNotNull(loader.findResource("META-INF/LICENSE.txt"));
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

	@Test
	public void testDoesntFindEncapsulatedResource() throws IOException {
		final var loader = createModularLoader("testruntime", null);
		final var resources = loader.findResources("org/eclipse/parsson/messages.properties");
		assertFalse(resources.hasMoreElements());
		assertNull(loader.findResource("org/eclipse/parsson/messages.properties"));
	}

}
