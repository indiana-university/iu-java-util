package edu.iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuSessionTest {

	@Test
	public void testDefault() {
		final var iuSessionMock = mock(IuSession.class, CALLS_REAL_METHODS);
		assertFalse(iuSessionMock.isChanged());
	}
}
