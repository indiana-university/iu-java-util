package iu.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Test class for LoggingFilters.
 */
public class LoggingFiltersTest {

	/**
	 * Test isLocal when the current thread's class loader is null.
	 */
	@Test
	public void testIsLocalNullClassLoader() {
		Thread.currentThread().setContextClassLoader(null);
		assertTrue(LoggingFilters.isLocal());
	}

	/**
	 * Test isLocal when the current thread's class loader is null.
	 */
	@Test
	public void testIsLocal() {
		assertTrue(LoggingFilters.isLocal());
	}

	/**
	 * Test isLocal when the current thread's class loader is null.
	 */
	@Test
	public void testIsLocalClassLoaderThrowsException() {
		ClassLoader cl = mock(ClassLoader.class);
		Thread.currentThread().setContextClassLoader(cl);
		try {
			Mockito.when(cl.loadClass(LoggingFilters.class.getName())).thenThrow(new ClassNotFoundException());
			assertFalse(LoggingFilters.isLocal());
		} catch (ClassNotFoundException e) {
		}
	}
}
