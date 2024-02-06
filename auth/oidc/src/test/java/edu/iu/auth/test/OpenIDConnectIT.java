package edu.iu.auth.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.IdGenerator;
import edu.iu.IuWebUtils;
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

		client.getAuthorizationEndpoint();

		final var nonce = IdGenerator.generateId();

		final var params = new LinkedHashMap<String, Iterable<String>>();
		params.put("client_id", List.of(clientId));
		params.put("nonce", List.of(nonce));
		params.put("response_type", List.of("code"));
		params.put("redirect_uri", List.of(redirectUri.toString()));
		params.put("scope", List.of("openid"));
		params.put("state", List.of(grant.getState()));

		final var authEndpoint = client.getAuthorizationEndpoint();
		final var cookieHandler = new CookieManager();
		final var http = HttpClient.newBuilder().cookieHandler(cookieHandler).build();

		final var initAuthUri = new URI(authEndpoint + "?" + IuWebUtils.createQueryString(params));
		System.out.println(initAuthUri);
		final var initAuthCodeRequest = HttpRequest.newBuilder(initAuthUri).build();
		final var initAuthCodeResponse = http.send(initAuthCodeRequest, BodyHandlers.ofString());
		assertEquals(302, initAuthCodeResponse.statusCode());

		final var firstRedirectLocation = initAuthCodeResponse.headers().firstValue("Location").get();
		assertTrue(firstRedirectLocation.startsWith(authEndpoint.getPath() + '?'), () -> firstRedirectLocation);

		final var loginRequestUri = new URI(
				authEndpoint + firstRedirectLocation.substring(authEndpoint.getPath().length()));
		final var firstLoginRequest = HttpRequest.newBuilder(loginRequestUri).build();
		final var firstLoginResponse = http.send(firstLoginRequest, BodyHandlers.ofString());
		assertEquals(200, firstLoginResponse.statusCode());
		assertEquals("text/html;charset=utf-8", firstLoginResponse.headers().firstValue("Content-Type").get());

		final var parsedSecondLoginRedirectForm = Jsoup.parse(firstLoginResponse.body()).selectFirst("form");
		assertEquals(firstRedirectLocation, parsedSecondLoginRedirectForm.attr("action"));
		assertEquals("POST", parsedSecondLoginRedirectForm.attr("method").toUpperCase());

		final var secondLoginRedirectParams = new LinkedHashMap<String, Iterable<String>>();
		for (final var i : parsedSecondLoginRedirectForm.select("input[type='hidden']"))
			secondLoginRedirectParams.put(i.attr("name"), List.of(i.attr("value")));
		final var secondLoginRedirectQuery = IuWebUtils.createQueryString(secondLoginRedirectParams);

		final var secondLoginRequest = HttpRequest.newBuilder(loginRequestUri)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(secondLoginRedirectQuery)).build();
		final var secondLoginResponse = http.send(secondLoginRequest, BodyHandlers.ofString());
		assertEquals(302, secondLoginResponse.statusCode());
		final var secondRedirectLocation = secondLoginResponse.headers().firstValue("Location").get();
		assertTrue(secondRedirectLocation.startsWith(authEndpoint.getPath() + '?'), () -> secondRedirectLocation);

		final var loginFormUri = new URI(
				authEndpoint + secondRedirectLocation.substring(authEndpoint.getPath().length()));

		final var loginFormRequest = HttpRequest.newBuilder(loginFormUri).build();
		final var loginFormResponse = http.send(loginFormRequest, BodyHandlers.ofString());

		assertEquals(200, loginFormResponse.statusCode());
		assertEquals("text/html;charset=utf-8", loginFormResponse.headers().firstValue("Content-Type").get());

		final var parsedLoginForm = Jsoup.parse(loginFormResponse.body()).getElementById("fm1");
		assertEquals(secondRedirectLocation, parsedLoginForm.attr("action"));
		assertEquals("POST", parsedLoginForm.attr("method").toUpperCase());

		final var loginFormParams = new LinkedHashMap<String, Iterable<String>>();
		loginFormParams.put("j_username", List.of(VaultProperties.getProperty("test.username")));
		loginFormParams.put("j_password", List.of(VaultProperties.getProperty("test.password")));
		loginFormParams.put("_eventId_proceed", List.of(""));
		final var loginFormQuery = IuWebUtils.createQueryString(loginFormParams);

		final var loginFormPost = HttpRequest.newBuilder(loginFormUri)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(loginFormQuery)).build();
		final var loginSuccessResponse = http.send(loginFormPost, BodyHandlers.ofString());

		assertEquals(302, loginSuccessResponse.statusCode());
		final var finalRedirectLocation = loginSuccessResponse.headers().firstValue("Location").get();
		assertTrue(finalRedirectLocation.startsWith(redirectUri.toString() + '?'), () -> finalRedirectLocation);
		final var authCodeParams = IuWebUtils
				.parseQueryString(finalRedirectLocation.substring(redirectUri.toString().length()));

		assertEquals(grant.getState(), authCodeParams.get("state").iterator().next()); 
		final var code = authCodeParams.get("code").iterator().next();

		final var authResponse = grant.authorize(code);
		System.out.println(authResponse.getAttributes().keySet());
		// TODO: implement / test token validation
		
		final var userInfo = HttpUtils.read(HttpRequest.newBuilder(provider.getUserInfoEndpoint())
				.header("Authorization", "Bearer " + authResponse.getAccessToken()).build());

		System.out.println(userInfo);
//		assertEquals(clientId, userInfo.asJsonObject().getString("sub"));
		
//		System.out.println(loginSuccessResponse.statusCode());
//		System.out.println(loginSuccessResponse.headers().map());
//		System.out.println(loginSuccessResponse.body());

	}

}
