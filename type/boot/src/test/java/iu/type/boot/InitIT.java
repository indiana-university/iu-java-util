package iu.type.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class InitIT {

	@Test
	public void testMain() {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "after init container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before destroy container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"before destroy loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		assertDoesNotThrow(() -> Init.main());
	}

	@Test
	public void testMainError() {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");

		System.setProperty("iu.boot.components", IdGenerator.generateId());
		try { // File not found
			assertThrows(IllegalStateException.class, () -> Init.main());
		} finally {
			System.getProperties().remove("iu.boot.components");
		}
	}

	@Test
	public void testDoubleClose() {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "after init container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before destroy container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"before destroy loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		assertDoesNotThrow(() -> {
			try (final var init = new Init()) {
				init.close();
			}
		});
	}

	@Test
	public void testCloseError() throws IOException {
		IuTestLogger.allow("iu.type.container", Level.CONFIG);

		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before init loader");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"after init loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "after init container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE, "before destroy container bootstrap .*");
		IuTestLogger.expect(Init.class.getName(), Level.FINE,
				"before destroy loader (iu\\.util\\.type(\\.container|\\.bundle|\\.base)?(, )?)+");

		final var error = new IllegalStateException();
		try (final var init = new Init()) {
			try (final var mockIuException = mockStatic(IuException.class)) {
				mockIuException.when(() -> IuException.suppress(any(), any())).thenReturn(error);
				mockIuException.when(() -> IuException.unchecked(error)).thenReturn(error);
				assertSame(error, assertThrows(IllegalStateException.class, init::close));
			}
		}
	}

}
