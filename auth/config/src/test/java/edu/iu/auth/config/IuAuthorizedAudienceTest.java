package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuAuthorizedAudienceTest {

	@Test
	public void testTokenTtl() {
		final var a = mock(IuAuthorizedAudience.class, CALLS_REAL_METHODS);
		assertEquals(Duration.ofSeconds(15L), a.getTokenTtl());
	}

}
