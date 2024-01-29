package edu.iu.auth.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.test.IuTestLogger;
import edu.iu.test.VaultProperties;
import iu.auth.util.HttpUtils;

@SuppressWarnings("javadoc")
public class OpenIDConnectIT {

	@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
	@Test
	public void testClientCredentials() throws Exception {
		IuTestLogger.allow("iu.auth.util.HttpUtils", Level.FINE);
		IuTestLogger.allow("iu.auth.oidc.OpenIdProvider", Level.INFO);

		final var configUri = new URI(VaultProperties.getProperty("iu.auth.oidc.configUrl"));
		final var clientId = VaultProperties.getProperty("iu.auth.oidc.clientId");
		final var clientSecret = VaultProperties.getProperty("iu.auth.oidc.clientSecret");
		final var resourceUri = new URI(VaultProperties.getProperty("iu.auth.oidc.redirectUri"));

		final var provider = IuOpenIdProvider.from(configUri);
		final var client = provider.createAuthorizationClient(resourceUri,
				IuApiCredentials.basic(clientId, clientSecret));
		IuAuthorizationClient.initialize(provider.getIssuer(), client);

		final var session = IuAuthorizationSession.create();
		final var grant = session.getClientCredentialsGrant(provider.getIssuer(), "openid");
		final var authResponse = grant.authorize();
		
		final var userInfo = HttpUtils.read(HttpRequest.newBuilder(provider.getUserInfoEndpoint())
				.header("Authorization", "Bearer " + authResponse.getAccessToken()).build());

		System.out.println(userInfo);
	}

}
