package edu.iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuObject;

@SuppressWarnings("javadoc")
public class IuHttpTest {

	private static final URI TEST_URI = URI.create("test:" + IdGenerator.generateId());
	private static HttpClient http;
	private static Handler logHandler;

	@BeforeAll
	public static void setupClass() throws ClassNotFoundException {
		try {
			System.setProperty("iu-client.allowedUri", TEST_URI.toString());

			http = mock(HttpClient.class);
			try (final var mockHttpClient = mockStatic(HttpClient.class)) {
				mockHttpClient.when(() -> HttpClient.newHttpClient()).thenReturn(http);
				Class.forName(IuHttp.class.getName());
			}

			final var log = LogManager.getLogManager().getLogger(IuHttp.class.getName());
			log.setLevel(Level.FINE);
			logHandler = mock(Handler.class);

			log.addHandler(logHandler);
		} finally {
			System.getProperties().remove("iu-client.allowedUri");
		}
	}

	@Test
	public void testRequiresModule() throws Exception {
		try (final var c = new URLClassLoader(new URL[] { Path.of("target", "classes").toRealPath().toUri().toURL() }) {
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				if (IuObject.isPlatformName(name))
					return super.loadClass(name);
				else
					return findClass(name);
			}

		}) {
			final var uc = c.loadClass(IuHttp.class.getName());
			assertNotSame(IuHttp.class, uc);
			final var e = assertThrows(ExceptionInInitializerError.class,
					() -> uc.getMethod("get", URI.class).invoke(null, TEST_URI));
			assertInstanceOf(IllegalStateException.class, e.getCause());
			assertEquals("Must be in a named module and not open", e.getCause().getMessage());
		}
	}

	@Test
	public void testRequiresNonOpenModule() throws Exception {
		final var path = Path.of("target", "classes").toRealPath();
		try (final var c = new URLClassLoader(new URL[] { path.toUri().toURL() }) {
			{
				registerAsParallelCapable();
			}

			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				if (!name.startsWith("edu.iu.client."))
					return super.loadClass(name);
				else
					return findClass(name);
			}
		}) {
			final var desc = ModuleDescriptor //
					.newOpenModule("iu.util.client") //
					.exports("edu.iu.client") //
					.requires("java.logging") //
					.requires("java.net.http") //
					.build();
			final var ref = mock(ModuleReference.class);
			when(ref.descriptor()).thenReturn(desc);

			final var finder = mock(ModuleFinder.class);
			when(finder.find("iu.util.client")).thenReturn(Optional.of(ref));

			ModuleLayer.defineModules(Configuration.resolve(finder, List.of(ModuleLayer.boot().configuration()),
					ModuleFinder.of(), Set.of("iu.util.client")), List.of(ModuleLayer.boot()), a -> c);

			final var uc = c.loadClass(IuHttp.class.getName());
			assertNotSame(IuHttp.class, uc);
			assertEquals("iu.util.client", uc.getModule().getName());
			assertTrue(uc.getModule().isOpen("edu.iu.client"));
			final var e = assertThrows(ExceptionInInitializerError.class,
					() -> uc.getMethod("get", URI.class).invoke(null, TEST_URI));
			assertInstanceOf(IllegalStateException.class, e.getCause());
			assertEquals("Must be in a named module and not open", e.getCause().getMessage());
		}
	}

	@Test
	public void testGetCallsSend() throws Exception {
		try (final var mockHttp = mockStatic(IuHttp.class)) {
			mockHttp.when(() -> IuHttp.get(TEST_URI)).thenCallRealMethod();
			IuHttp.get(TEST_URI);
			mockHttp.verify(() -> IuHttp.send(TEST_URI, null));
		}
	}

	@Test
	public void testDenyByDefault() {
		assertThrows(IllegalArgumentException.class, () -> IuHttp.get(mock(URI.class)));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testPublicGet() throws Exception {
		try (final var mockRequest = mockStatic(HttpRequest.class)) {
			final var request = mock(HttpRequest.class);
			when(request.method()).thenReturn("GET");
			when(request.uri()).thenReturn(TEST_URI);

			final var mockBuilder = mock(HttpRequest.Builder.class);
			when(mockBuilder.build()).thenReturn(request);
			mockRequest.when(() -> HttpRequest.newBuilder(TEST_URI)).thenReturn(mockBuilder);

			final var response = mock(HttpResponse.class);
			when(response.statusCode()).thenReturn(200);
			when(http.send(eq(request), any(BodyHandler.class))).thenReturn(response);

			assertSame(response, IuHttp.get(TEST_URI));
			verify(logHandler).publish(argThat(r -> {
				assertEquals(Level.FINE, r.getLevel());
				assertEquals("GET " + TEST_URI + " 200 OK", r.getMessage());
				return true;
			}));
		}
	}

}
