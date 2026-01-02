package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class OAuthTokenResponseTest {

	@Test
	void testFrom() {
		final var tokenType = IdGenerator.generateId();
		final var accessToken = IdGenerator.generateId();
		final var idToken = IdGenerator.generateId();
		final var refreshToken = IdGenerator.generateId();
		final var expiresIn = ThreadLocalRandom.current().nextInt();
		final var error = IdGenerator.generateId();
		final var errorDescription = IdGenerator.generateId();
		final var errorUri = IdGenerator.generateId();

		final var tokenResponse = OAuthTokenResponse.from(IuJson.object() //
				.add("token_type", tokenType) //
				.add("access_token", accessToken) //
				.add("id_token", idToken) //
				.add("refresh_token", refreshToken) //
				.add("expires_in", expiresIn) //
				.add("error", error) //
				.add("error_description", errorDescription) //
				.add("error_uri", errorUri) //
				.build());

		assertEquals(tokenType, tokenResponse.getTokenType());
		assertEquals(accessToken, tokenResponse.getAccessToken());
		assertEquals(idToken, tokenResponse.getIdToken());
		assertEquals(refreshToken, tokenResponse.getRefreshToken());
		assertEquals(expiresIn, tokenResponse.getExpiresIn());
		assertEquals(error, tokenResponse.getError());
		assertEquals(errorDescription, tokenResponse.getErrorDescription());
		assertEquals(URI.create(errorUri)
				, tokenResponse.getErrorUri());
	}

}
