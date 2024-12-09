package iu.logging.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.ModuleLayer.Controller;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class LoggingBootstrapTest {

	public static class Impl {
		public static <T> T getActiveContext(Class<T> ifc) {
			return null;
		}

		public static <T> T follow(Object context, String msg, Object supplier) {
			return null;
		}

		public static Level getLevel() {
			return null;
		}

		public static void setLevel(Level level) {
		}
	}

	private MockedStatic<LoggingBootstrap> mockLoggingBootstrap;

	@BeforeEach
	public void setup() {
		mockLoggingBootstrap = mockStatic(LoggingBootstrap.class);
	}

	@AfterEach
	public void teardown() {
		mockLoggingBootstrap.close();
	}

	@Test
	public void testInit() {
		assertDoesNotThrow(() -> {
			final var current = Thread.currentThread();
			final var restore = current.getContextClassLoader();
			final var restoreApp = IdGenerator.generateId();
			System.setProperty("iu.logging.application", restoreApp);
			final var endpoint = IdGenerator.generateId();
			final var application = IdGenerator.generateId();
			final var environment = IdGenerator.generateId();
			try (final var impl = new URLClassLoader(new URL[0]); //
					final var ctx = new URLClassLoader(new URL[0])) {
				current.setContextClassLoader(ctx);
				mockLoggingBootstrap.when(() -> LoggingBootstrap.init(endpoint, application, environment))
						.thenCallRealMethod();
				mockLoggingBootstrap.when(() -> LoggingBootstrap.init()).then(a -> {
					assertEquals(endpoint, System.getProperty("iu.logging.endpoint"));
					assertEquals(application, System.getProperty("iu.logging.application"));
					assertEquals(environment, System.getProperty("iu.logging.environment"));
					return a.callRealMethod();
				});
				mockLoggingBootstrap.when(() -> LoggingBootstrap.implLoader()).thenReturn(impl);

				LoggingBootstrap.init(endpoint, application, environment);

				mockLoggingBootstrap.verify(() -> LoggingBootstrap.bootstrap(impl, ctx));
				assertEquals(restoreApp, System.getProperty("iu.logging.application"));
				System.getProperties().remove("iu.logging.application");
			} finally {
				current.setContextClassLoader(restore);
			}
		});
	}

	@Test
	public void testInitImplModule() {
		final var impl = mock(Module.class);
		final var clayer = mock(ModuleLayer.class);
		when(clayer.findModule("iu.util.logging.impl")).thenReturn(Optional.of(impl));

		final var c = mock(Controller.class);
		when(c.layer()).thenReturn(clayer);
		mockLoggingBootstrap.when(() -> LoggingBootstrap.initImplModule(c)).thenCallRealMethod();

		final var blayer = mock(ModuleLayer.class);
		final var base = mock(Module.class);
		when(blayer.findModule("iu.util")).thenReturn(Optional.of(base));

		try (final var mockModuleLayer = mockStatic(ModuleLayer.class)) {
			mockModuleLayer.when(() -> ModuleLayer.boot()).thenReturn(blayer);
			LoggingBootstrap.initImplModule(c);
			verify(c).addReads(impl, base);
		}
	}

	@Test
	public void testInitImplModuleNoBase() {
		final var impl = mock(Module.class);
		final var clayer = mock(ModuleLayer.class);
		when(clayer.findModule("iu.util.logging.impl")).thenReturn(Optional.of(impl));

		final var c = mock(Controller.class);
		when(c.layer()).thenReturn(clayer);
		mockLoggingBootstrap.when(() -> LoggingBootstrap.initImplModule(c)).thenCallRealMethod();

		final var blayer = mock(ModuleLayer.class);
		when(blayer.findModule("iu.util")).thenReturn(Optional.empty());

		try (final var mockModuleLayer = mockStatic(ModuleLayer.class)) {
			mockModuleLayer.when(() -> ModuleLayer.boot()).thenReturn(blayer);
			LoggingBootstrap.initImplModule(c);
		}
	}

}
