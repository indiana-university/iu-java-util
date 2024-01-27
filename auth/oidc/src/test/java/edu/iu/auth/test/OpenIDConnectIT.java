package edu.iu.auth.test;

import java.net.URI;
import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.test.VaultProperties;

@SuppressWarnings("javadoc")
public class OpenIDConnectIT {

	@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
	@Test
	public void testEndToEnd() throws Exception {
		final var configUri = new URI(VaultProperties.getProperty("iu.auth.oidc.configUrl"));
		final var clientId = VaultProperties.getProperty("iu.auth.oidc.clientId");
		final var clientSecret = VaultProperties.getProperty("iu.auth.oidc.clientSecret");

		final var provider = IuOpenIdProvider.from(configUri);
		final var config = provider.getConfiguration();
		final var session = IuAuthorizationSession.create();

		final var authCodeGrant = session.createAuthorizationCodeGrant(config.getIssuer());

		HttpRequest.Builder authCodeRequestBuilder = HttpRequest.newBuilder(config.getAuthorizationEndpoint());
		
	}

}
