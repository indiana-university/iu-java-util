package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.function.Supplier;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IdGenerator;
import edu.iu.UnsafeSupplier;
import iu.logging.boot.LoggingBootstrap;

@SuppressWarnings("javadoc")
public class IuLogContextTest {

	static String lastMethod;
	static IuLogContext context;
	static String msg;
	static UnsafeSupplier<?> supplier;
	static Supplier<String> messageSupplier;

	public static class Impl {
		private static Level level;

		public static IuLogContext getActiveContext(Class<IuLogContext> c) {
			assertEquals(IuLogContext.class, c);
			lastMethod = "getActiveContext";
			return context = mock(IuLogContext.class);
		}

		public static Object follow(Object context, String msg, Object supplier) throws Throwable {
			lastMethod = "follow";
			IuLogContextTest.context = (IuLogContext) context;
			IuLogContextTest.msg = msg;
			return (IuLogContextTest.supplier = (UnsafeSupplier<?>) supplier).get();
		}

		public static void trace(Supplier<String> messageSupplier) {
			lastMethod = "trace";
			IuLogContextTest.messageSupplier = messageSupplier;
		}

		public static Level getLevel() {
			lastMethod = "getLevel";
			return level;
		}

		public static void setLevel(Level level) {
			lastMethod = "setLevel";
			Impl.level = level;
		}

		public static void flushLogFiles() {
			lastMethod = "flushLogFiles";
		}
	}

	private MockedStatic<LoggingBootstrap> mockLoggingBootstrap;

	@BeforeEach
	public void setup() {
		lastMethod = null;
		context = null;
		msg = null;
		supplier = null;
		messageSupplier = null;
		mockLoggingBootstrap = mockStatic(LoggingBootstrap.class);
		mockLoggingBootstrap.when(() -> LoggingBootstrap.impl()).thenReturn(Impl.class);
	}

	@AfterEach
	public void teardown() {
		mockLoggingBootstrap.close();
	}

	@Test
	public void testGetActiveContext() {
		final var activeContext = IuLogContext.getActiveContext();
		assertSame("getActiveContext", lastMethod);
		assertSame(activeContext, context);
	}

	@Test
	public void testFollow() {
		final var context = mock(IuLogContext.class);
		final var msg = IdGenerator.generateId();
		final var result = new Object();
		final UnsafeSupplier<Object> supplier = () -> result;
		assertSame(result, assertDoesNotThrow(() -> IuLogContext.follow(context, msg, supplier)));
		assertEquals("follow", lastMethod);
		assertSame(context, IuLogContextTest.context);
		assertSame(msg, IuLogContextTest.msg);
		assertSame(supplier, IuLogContextTest.supplier);
	}

	@Test
	public void testTrace() {
		final Supplier<String> messageSupplier = IdGenerator::generateId;
		assertDoesNotThrow(() -> IuLogContext.trace(messageSupplier));
		assertEquals("trace", lastMethod);
		assertSame(messageSupplier, IuLogContextTest.messageSupplier);
	}

	@Test
	public void testFlushLogFiles() {
		IuLogContext.flushLogFiles();
		assertEquals("flushLogFiles", lastMethod);
	}

}
