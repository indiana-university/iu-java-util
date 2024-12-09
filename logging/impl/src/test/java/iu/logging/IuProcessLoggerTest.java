package iu.logging;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuProcessLoggerTest {

	@Test
	public void testGetActiveContext() {
		assertThrows(UnsupportedOperationException.class,
				() -> IuProcessLogger.getActiveContext(IuProcessLogger.class));
	}

}
