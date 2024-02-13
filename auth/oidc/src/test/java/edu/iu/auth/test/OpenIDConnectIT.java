/*
 * Copyright © 2024 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.iu.auth.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.IuWebUtils;
import edu.iu.auth.IuApiCredentials;
import edu.iu.auth.oauth.IuAuthorizationClient;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;
import edu.iu.auth.oidc.IuOpenIdClient;
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
	private static URI resourceUri;
	private static IuOpenIdProvider provider;
	private static IuAuthorizationGrant clientCredentials;
	private static IuAuthorizationClient client;
	private static IuAuthorizationSession session;

	@BeforeAll
	public static void setupClass() throws URISyntaxException {
		configUri = new URI(VaultProperties.getProperty("iu.auth.oidc.configUrl"));
		clientId = VaultProperties.getProperty("iu.auth.oidc.clientId");
		clientSecret = VaultProperties.getProperty("iu.auth.oidc.clientSecret");
		redirectUri = new URI(VaultProperties.getProperty("iu.auth.oidc.redirectUri"));
		resourceUri = new URI(VaultProperties.getProperty("iu.auth.oidc.resourceUri"));
		provider = IuOpenIdProvider.from(configUri, new IuOpenIdClient() {
			@Override
			public Duration getTrustRefreshInterval() {
				return Duration.ofSeconds(15L);
			}

			@Override
			public URI getResourceUri() {
				return resourceUri;
			}

			@Override
			public URI getRedirectUri() {
				return redirectUri;
			}

			@Override
			public IuApiCredentials getCredentials() {
				return IuApiCredentials.basic(clientId, clientSecret);
			}

			@Override
			public Duration getAuthenticationTimeout() {
				return Duration.ofSeconds(15L);
			}

			@Override
			public Map<String, String> getClientCredentialsAttributes() {
				final Map<String, String> attributes = new LinkedHashMap<>();
				attributes.put("resource", redirectUri.toString());
				attributes.put("audience", redirectUri.toString());
				return Collections.unmodifiableMap(attributes);
			}

		});
		client = provider.createAuthorizationClient(redirectUri);
		IuAuthorizationClient.initialize(client);
		session = IuAuthorizationSession.create(provider.getIssuer(), resourceUri);
	}

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("iu.auth.util.HttpUtils", Level.FINE);
		IuTestLogger.allow("iu.auth.oidc.OpenIdProvider", Level.INFO);
	}

	@Test
	public void testClientCredentials() throws Exception {
		IuTestLogger.allow("iu.auth.oauth.ClientCredentialsGrant", Level.FINE);
		final var credneitals = clientCredentials.authorize(resourceUri);

		final var req = HttpRequest.newBuilder(provider.getUserInfoEndpoint());
		credneitals.applyTo(req);
		final var userInfo = HttpUtils.read(req.build());

		assertEquals(clientId, userInfo.asJsonObject().getString("sub"));
	}

	@Test
	public void testAuthCode() throws Exception {
		IuTestLogger.allow("iu.auth.oauth.AuthorizationCodeGrant", Level.FINE);
		final var grant = session.createAuthorizationCodeGrant("openid");
		final var location = assertThrows(IuAuthenticationRedirectException.class, grant::authorize).getMessage();
		final var authEndpoint = client.getAuthorizationEndpoint();
		assertTrue(location.startsWith(authEndpoint.toString()));

		// Emulates browser interactions with Shibboleth authorization code flow
		// for a user that doesn't require MFA
		final var cookieHandler = new CookieManager();
		final var http = HttpClient.newBuilder().cookieHandler(cookieHandler).build();

		final var initAuthUri = new URI(location);
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
		provider.verifyAuthentication(authResponse);
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
