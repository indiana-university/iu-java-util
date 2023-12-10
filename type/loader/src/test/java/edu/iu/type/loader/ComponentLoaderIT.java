package edu.iu.type.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import edu.iu.UnsafeRunnable;
import edu.iu.test.IuTest;

@SuppressWarnings("javadoc")
public class ComponentLoaderIT {

	public static InputStream getComponentArchive(String componentName) throws IOException {
		return Files.newInputStream(Path.of(IuTest.getProperty(componentName + ".archive")));
	}

	public static InputStream[] getProvidedDependencyArchives(String componentName) throws IOException {
		Queue<InputStream> providedDependencyArchives = new ArrayDeque<>();
		var deps = IuTest.getProperty(componentName + ".deps");
		if (deps != null)
			for (var jar : Files.newDirectoryStream(Path.of(deps).toRealPath()))
				providedDependencyArchives.offer(Files.newInputStream(jar));
		return providedDependencyArchives.toArray(new InputStream[providedDependencyArchives.size()]);
	}

	@Test
	public void testLoadsRuntime() throws Exception {
		new IuComponentLoader(a -> {
			assertEquals("iu.util.type.impl", a.getTypeModule().getName());
			final var module = a.getComponentModule();
			assertEquals("iu.util.type.testruntime", module.getName());
			assertSame(module, a.getController().layer().findModule(module.getName()).get());
		}, getComponentArchive("testruntime"), getProvidedDependencyArchives("testruntime")).close();
	}

	@Test
	public void testLoadsRuntimeAndWorks() throws Exception {
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

		try (final var componentLoader = new IuComponentLoader(null, getComponentArchive("testruntime"),
				getProvidedDependencyArchives("testruntime"))) {

			var contextLoader = Thread.currentThread().getContextClassLoader();
			var loader = componentLoader.getLoader();
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
	public void testCloseError() throws Exception {
		final var e = new IOException();
		final var componentLoader = new IuComponentLoader(null, getComponentArchive("testruntime"),
				getProvidedDependencyArchives("testruntime"));
		final var destroyField = componentLoader.getClass().getDeclaredField("destroy");
		destroyField.setAccessible(true);
		final var originalDestroy = (UnsafeRunnable) destroyField.get(componentLoader);
		destroyField.set(componentLoader, (UnsafeRunnable) () -> {
			originalDestroy.run();
			throw e;
		});
		assertSame(e, assertThrows(IOException.class, componentLoader::close));
	}

}