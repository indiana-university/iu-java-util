package iu.auth.oidc;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.oidc.IuOpenIdClient;

@SuppressWarnings("javadoc")
public class OpenIdConnectSpiTest extends IuOidcTestCase {

	@Test
	public void testProviderRegistration() {
		final var spi = new OpenIdConnectSpi();
		final var client = mock(IuOpenIdClient.class);
		final var realm = IdGenerator.generateId();
		when(client.getRealm()).thenReturn(realm);
		final var provider = spi.getOpenIdProvider(client);
		assertSame(provider, OpenIdConnectSpi.getProvider(realm));
		assertThrows(IllegalArgumentException.class, () -> spi.getOpenIdProvider(client));
	}

}
