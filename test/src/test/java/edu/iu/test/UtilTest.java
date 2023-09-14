package edu.iu.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class UtilTest {

	@Test
	public void testMockWithDefaults() {
		var hasDefaults = IuTest.mockWithDefaults(InterfaceWithDefaults.class);
		assertNull(hasDefaults.getAbstractString());
		assertEquals("foobar", hasDefaults.getDefaultString());
	}

	@Test
	public void testMockWithDefaultsHandlesUnsupportedOperationException() {
		var hasDefaults = IuTest.mockWithDefaults(InterfaceWithDefaults.class);
		assertDoesNotThrow(() -> hasDefaults.throwsUnsupportedOperationException());
		
		var exception = new RuntimeException();
		doThrow(exception).when(hasDefaults).throwsUnsupportedOperationException();
		try {
			hasDefaults.throwsUnsupportedOperationException();
		} catch (RuntimeException e) {
			assertSame(exception, e);
		}
	}

	@Test
	public void testMockWithDefaultsHandlesOtherExceptions() {
		var hasDefaults = IuTest.mockWithDefaults(InterfaceWithDefaults.class);
		when(hasDefaults.getAbstractString()).thenThrow(IllegalStateException.class);
		assertThrows(IllegalStateException.class, () -> hasDefaults.getAbstractString());
	}

}
