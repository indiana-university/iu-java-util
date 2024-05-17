package edu.iu.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class AuthConfigTest {

	private static final class Config implements IuAuthConfig {
		private final String realm;

		private Config(String realm) {
			this.realm = realm;
		}

		@Override
		public String getRealm() {
			return realm;
		}

		@Override
		public String getAuthScheme() {
			return null;
		}

		@Override
		public URI getAuthenticationEndpoint() {
			return null;
		}

	}

	@Test
	public void testSealed() {
		final var realm = IdGenerator.generateId();
		assertThrows(IllegalStateException.class, () -> AuthConfig.get(realm));

		final var config = new Config(realm);
		assertDoesNotThrow(() -> AuthConfig.register(config));
		assertThrows(IllegalArgumentException.class, () -> AuthConfig.register(config));

		AuthConfig.seal();
		assertSame(config, AuthConfig.get(realm));
		assertThrows(IllegalStateException.class, () -> AuthConfig.register(config));
	}

}
