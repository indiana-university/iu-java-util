package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class SessionTokenTest {

	@Test
	void testCreateSessionTokenWithValidParameters() {
		String token = "testToken";
		Instant inactivePurgeTime = Instant.now();
		SessionToken sessionToken = new SessionToken(token, inactivePurgeTime);

		assertEquals(token, sessionToken.token());
		assertEquals(inactivePurgeTime, sessionToken.inactivePurgeTime());
	}

	@Test
	void testCreateSessionTokenWithNullToken() {
		Instant inactivePurgeTime = Instant.now();

		assertThrows(NullPointerException.class, () -> new SessionToken(null, inactivePurgeTime));
	}

	@Test
	void testCreateSessionTokenWithNullInactivePurgeTime() {
		String token = "testToken";

		assertThrows(NullPointerException.class, () -> new SessionToken(token, null));
	}
}
