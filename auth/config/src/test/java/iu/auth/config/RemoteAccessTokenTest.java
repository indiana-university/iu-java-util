package iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mockConstruction;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class RemoteAccessTokenTest {

	@Test
	public void testBuilder() {
		try (final var mockRemoteAccessTokenBuilder = mockConstruction(RemoteAccessTokenBuilder.class)) {
			final var b = RemoteAccessToken.builder();
			assertSame(b, mockRemoteAccessTokenBuilder.constructed().get(0));
		}
	}
	
	@Test
	public void testScope() {
		final var scope = IdGenerator.generateId();

		final var t = new RemoteAccessToken(IuJson.object().add("scope", scope) //
				.build());

		assertEquals(scope, t.getScope());
	}

	@Test
	public void testCallerAttrbutes() {
		final var requestUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var userAgent = IdGenerator.generateId();
		final var authnPrincipal = IdGenerator.generateId();

		final var t = new RemoteAccessToken(IuJson.object()
				.add("authorization_details", IuJson.array().add(IuJson.object() //
						.add("type", IuCallerAttributes.TYPE) //
						.add("request_uri", requestUri.toString()) //
						.add("remote_addr", remoteAddr) //
						.add("user_agent", userAgent) //
						.add("authn_principal", authnPrincipal) //
						.build()))
				.build());

		final var callerAttributes = t.getCallerAttributes();
		assertEquals(requestUri, callerAttributes.getRequestUri());
		assertEquals(remoteAddr, callerAttributes.getRemoteAddr());
		assertEquals(userAgent, callerAttributes.getUserAgent());
		assertEquals(authnPrincipal, callerAttributes.getAuthnPrincipal());
	}

}
