package iu.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Supplier;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.UnsafeSupplier;
import edu.iu.test.IuTestLogger;
import iu.logging.internal.IuLogHandler;
import iu.logging.internal.LogContext;
import iu.logging.internal.ProcessLogger;

@SuppressWarnings("javadoc")
public class BootstrapTest {

	private static String nodeId = IuException.unchecked(() -> InetAddress.getLocalHost().getHostName());
	private static String defaultEndpoint;
	private static String defaultApp;
	private static String defaultEnv;
	private String endpoint;
	private String application;
	private String environment;

	@BeforeAll
	public static void setupClass() {
		defaultEndpoint = IdGenerator.generateId();
		defaultApp = IdGenerator.generateId();
		defaultEnv = IdGenerator.generateId();
		System.setProperty("iu.logging.endpoint", defaultEndpoint);
		System.setProperty("iu.logging.application", defaultApp);
		System.setProperty("iu.logging.environment", defaultEnv);

		final var root = mock(Logger.class);
		when(root.getHandlers()).thenReturn(new Handler[] { new ConsoleHandler() });

		final var logManager = mock(LogManager.class);
		when(logManager.getLogger("")).thenReturn(root);

		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
			Bootstrap.init(ClassLoader.getPlatformClassLoader());

			verify(root).addHandler(Bootstrap.HANDLER);
			verify(root).config("IU Logging Bootstrap initialized " + Bootstrap.HANDLER + " DefaultLogContext [nodeId="
					+ nodeId + ", endpoint=" + defaultEndpoint + ", application=" + defaultApp + ", environment="
					+ defaultEnv + "]; context: " + ClassLoader.getPlatformClassLoader());
		}
	}

	@BeforeEach
	public void setup() {
		endpoint = IdGenerator.generateId();
		application = IdGenerator.generateId();
		environment = IdGenerator.generateId();
		System.setProperty("iu.logging.endpoint", endpoint);
		System.setProperty("iu.logging.application", application);
		System.setProperty("iu.logging.environment", environment);
		System.setProperty("iu.logging.logPath", "target/logs");
	}

	@AfterEach
	public void teardown() {
		System.getProperties().remove("iu.logging.logPath");
	}

	@Test
	public void testInitNoConfig() {
		System.setProperty("iu.config", "target/" + IdGenerator.generateId());
		try {
			final var root = mock(Logger.class);
			when(root.getHandlers()).thenReturn(new Handler[] { new ConsoleHandler() });

			final var logManager = mock(LogManager.class);
			when(logManager.getLogger("")).thenReturn(root);

			try (final var mockLogManager = mockStatic(LogManager.class)) {
				mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
				Bootstrap.init(ClassLoader.getSystemClassLoader());

				verify(root).addHandler(Bootstrap.HANDLER);
				verify(root)
						.config("IU Logging Bootstrap initialized " + Bootstrap.HANDLER + " DefaultLogContext [nodeId="
								+ nodeId + ", endpoint=" + endpoint + ", application=" + application + ", environment="
								+ environment + "]; context: " + ClassLoader.getSystemClassLoader());
			}
		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testInitWithConfig() {
		System.setProperty("iu.config", "target/test-classes");
		try {
			final var root = mock(Logger.class);
			when(root.getHandlers()).thenReturn(new Handler[] { new ConsoleHandler() });

			final var logManager = mock(LogManager.class);
			when(logManager.getLogger("")).thenReturn(root);

			try (final var mockLogManager = mockStatic(LogManager.class)) {
				mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
				Bootstrap.init(ClassLoader.getSystemClassLoader());

				verify(root).addHandler(Bootstrap.HANDLER);
				verify(root)
						.config("IU Logging Bootstrap initialized " + Bootstrap.HANDLER + " DefaultLogContext [nodeId="
								+ nodeId + ", endpoint=" + endpoint + ", application=" + application + ", environment="
								+ environment + "]; context: " + ClassLoader.getSystemClassLoader());
			}
		} finally {
			System.clearProperty("iu.config");
		}
	}

	@Test
	public void testInitAreadyInitialized() {
		final var root = mock(Logger.class);
		final var handler = mock(IuLogHandler.class);
		when(root.getHandlers()).thenReturn(new Handler[] { handler });

		final var logManager = mock(LogManager.class);
		when(logManager.getLogger("")).thenReturn(root);

		try (final var mockLogManager = mockStatic(LogManager.class)) {
			mockLogManager.when(() -> LogManager.getLogManager()).thenReturn(logManager);
			Bootstrap.init(ClassLoader.getSystemClassLoader());
			verify(root, never()).addHandler(any());
			verify(root, never()).config(any(String.class));
		}
	}

	@Test
	public void testDefaultContextInit() throws IOException {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try (final var loader = new URLClassLoader(new URL[0])) {
			IuTestLogger.expect("", Level.CONFIG,
					"IU Logging Bootstrap initialized " + Bootstrap.HANDLER + " DefaultLogContext [nodeId=" + nodeId
							+ ", endpoint=" + endpoint + ", application=" + application + ", environment=" + environment
							+ "]; context: " + loader);
			IuTestLogger.allow("", Level.CONFIG, "Logging configuration updated from .*logging.properties");
			Bootstrap.init(loader);

			current.setContextClassLoader(loader);

			final var context = Bootstrap.getDefaultContext();
			assertEquals(nodeId, context.getNodeId());
			assertEquals(endpoint, context.getEndpoint());
			assertEquals(application, context.getApplication());
			assertEquals(environment, context.getEnvironment());
		} finally {
			current.setContextClassLoader(restore);
		}
	}

	@Test
	public void testDefaultContextNotInit() throws IOException {
		final var current = Thread.currentThread();
		final var restore = current.getContextClassLoader();
		try (final var loader = new URLClassLoader(new URL[0])) {
			current.setContextClassLoader(loader);

			final var context = Bootstrap.getDefaultContext();
			assertEquals(nodeId, context.getNodeId());
			assertEquals(defaultEndpoint, context.getEndpoint());
			assertEquals(defaultApp, context.getApplication());
			assertEquals(defaultEnv, context.getEnvironment());
		} finally {
			current.setContextClassLoader(restore);
		}
	}

	@Test
	public void testActiveContext() {
		final var context = mock(LogContext.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class)) {
			mockProcessLogger.when(() -> ProcessLogger.getActiveContext()).thenReturn(context);
			assertSame(context, Bootstrap.getActiveContext(LogContext.class));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFollow() {
		final var context = mock(LogContext.class);
		final var supplier = mock(UnsafeSupplier.class);
		final var header = IdGenerator.generateId();
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class)) {
			assertDoesNotThrow(() -> Bootstrap.follow(context, header, supplier));
			mockProcessLogger.verify(() -> ProcessLogger.follow(context, header, supplier));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testTrace() {
		final var supplier = mock(Supplier.class);
		try (final var mockProcessLogger = mockStatic(ProcessLogger.class)) {
			assertDoesNotThrow(() -> Bootstrap.trace(supplier));
			mockProcessLogger.verify(() -> ProcessLogger.trace(supplier));
		}
	}

}
