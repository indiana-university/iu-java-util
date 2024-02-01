package edu.iu.auth.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.oidc.IuOpenIdProvider;
import edu.iu.test.IuTestLogger;
import edu.iu.test.VaultProperties;
import iu.auth.util.HttpUtils;

@EnabledIf("edu.iu.test.VaultProperties#isConfigured")
@SuppressWarnings("javadoc")
public class OpenIDConnectIT {

	private static URI configUri;
	private static String clientId;
	private static String clientSecret;
	private static URI redirectUri;
	private static IuOpenIdProvider provider;
	private static IuAuthorizationClient client;
	private static IuAuthorizationSession session;

	@BeforeAll
	public static void setupClass() throws URISyntaxException {
		configUri = new URI(VaultProperties.getProperty("iu.auth.oidc.configUrl"));
		clientId = VaultProperties.getProperty("iu.auth.oidc.clientId");
		clientSecret = VaultProperties.getProperty("iu.auth.oidc.clientSecret");
		redirectUri = new URI(VaultProperties.getProperty("iu.auth.oidc.redirectUri"));

		provider = IuOpenIdProvider.from(configUri);
		client = provider.createAuthorizationClient(redirectUri, IuApiCredentials.basic(clientId, clientSecret));
		IuAuthorizationClient.initialize(provider.getIssuer(), client);
		session = IuAuthorizationSession.create(provider.getIssuer());
	}

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.auth.util.HttpUtils", Level.FINE);
		IuTestLogger.allow("iu.auth.oidc.OpenIdProvider", Level.INFO);
	}

	@Test
	public void testClientCredentials() throws Exception {
		final var grant = session.getClientCredentialsGrant("openid");
		final var authResponse = grant.authorize();

		final var userInfo = HttpUtils.read(HttpRequest.newBuilder(provider.getUserInfoEndpoint())
				.header("Authorization", "Bearer " + authResponse.getAccessToken()).build());

		assertEquals(clientId, userInfo.asJsonObject().getString("sub"));
	}

	@Test
	public void testAuthCode() throws Exception {
		final var grant = session.createAuthorizationCodeGrant("openid");
		assertNull(grant.authorize());

	}

}
