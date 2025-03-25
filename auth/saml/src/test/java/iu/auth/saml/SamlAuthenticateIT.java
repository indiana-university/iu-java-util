/*
 * Copyright © 2025 Indiana University
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
package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.auth.saml.IuSamlAssertion;
import edu.iu.auth.saml.IuSamlSessionVerifier;
import edu.iu.auth.session.IuSession;
import edu.iu.client.IuVault;
import edu.iu.test.IuTestLogger;
import iu.auth.config.AuthConfig;
import iu.auth.pki.PkiVerifier;

@EnabledIf("edu.iu.client.IuVault#isConfigured")
@SuppressWarnings("javadoc")
public class SamlAuthenticateIT {

	private static final String REALM = "iu-saml-test";
	private static URI postUri;

	@BeforeAll
	public static void setupClass() {
		AuthConfig.registerInterface("realm", IuSamlServiceProviderMetadata.class, IuVault.RUNTIME);
		AuthConfig.registerInterface(IuPrivateKeyPrincipal.class);
		final var realm = AuthConfig.load(IuSamlServiceProviderMetadata.class, REALM);
		postUri = realm.getAcsUris().iterator().next();

		AuthConfig.register(new PkiVerifier(realm.getIdentity()));

		final var provider = new SamlServiceProvider(postUri, REALM, realm);
		AuthConfig.register(provider);
		AuthConfig.seal();

		final var identity = SamlServiceProvider.serviceProviderIdentity(realm);
		System.out.println("Verified SAML Service Provider " + identity);
	}

	@BeforeEach
	public void setup() {
		IuTestLogger.allow("", Level.FINE);
		IuTestLogger.allow("iu.auth.pki.PkiVerifier", Level.INFO);
		IuTestLogger.allow("iu.auth.saml.SamlResponseValidator", Level.INFO);
		IuTestLogger.allow("iu.auth.saml.SamlSessionVerifier", Level.INFO);
		IuTestLogger.allow("iu.auth.saml.SamlServiceProvider", Level.INFO);
	}

	@Test
	public void testSamlAuthentication() throws Exception {
		IuSession preAuthSession = mock(IuSession.class);
		final var preAuthDetail = mock(SamlPreAuthentication.class);
		final var prePostAuthDetail = mock(SamlPostAuthentication.class);
		when(preAuthSession.getDetail(SamlPreAuthentication.class)).thenReturn(preAuthDetail);
		when(preAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(prePostAuthDetail);

		URI entryPointUri = URI.create(IdGenerator.generateId());
		IuSamlSessionVerifier samlSessionVerifier = IuSamlSessionVerifier.create(postUri);

		final var location = samlSessionVerifier.initRequest(preAuthSession, entryPointUri);
		verify(preAuthDetail).setReturnUri(entryPointUri);
		when(preAuthDetail.getReturnUri()).thenReturn(entryPointUri);
		verify(preAuthDetail).setSessionId(argThat(s -> {
			when(preAuthDetail.getSessionId()).thenReturn(s);
			return true;
		}));
		verify(preAuthDetail).setRelayState(argThat(s -> {
			when(preAuthDetail.getRelayState()).thenReturn(s);
			return true;
		}));

		final var relayState = IuWebUtils.parseQueryString(location.getQuery()).get("RelayState").iterator().next();
		IdGenerator.verifyId(relayState, 2000L);

		System.out.println("Location: " + location);
		final var cookieHandler = new CookieManager();
		final var http = HttpClient.newBuilder().cookieHandler(cookieHandler).build();
		final var initRequest = HttpRequest.newBuilder(location).build();
		final var initResponse = http.send(initRequest, BodyHandlers.ofString());
		assertEquals(302, initResponse.statusCode());

		final var firstRedirectLocation = initResponse.headers().firstValue("Location").get();
		assertTrue(firstRedirectLocation.startsWith(location.getPath() + '?'), () -> firstRedirectLocation);
		System.out.println("Location: " + firstRedirectLocation);

		final var loginRequestUri = new URI(location.getScheme() + "://" + location.getHost() + firstRedirectLocation);
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
		assertTrue(secondRedirectLocation.startsWith(location.getPath() + '?'), () -> secondRedirectLocation);

		final var loginFormUri = new URI(location.getScheme() + "://" + location.getHost() + secondRedirectLocation);

		final var loginFormRequest = HttpRequest.newBuilder(loginFormUri).build();
		final var loginFormResponse = http.send(loginFormRequest, BodyHandlers.ofString());

		assertEquals(200, loginFormResponse.statusCode());
		assertEquals("text/html;charset=utf-8", loginFormResponse.headers().firstValue("Content-Type").get());

		final var parsedLoginForm = Jsoup.parse(loginFormResponse.body()).getElementById("fm1");
		assertEquals(secondRedirectLocation, parsedLoginForm.attr("action"));
		assertEquals("POST", parsedLoginForm.attr("method").toUpperCase());

		final var loginFormParams = new LinkedHashMap<String, Iterable<String>>();
		loginFormParams.put("j_username", List.of(IuVault.RUNTIME.get("test.username").getValue()));
		loginFormParams.put("j_password", List.of(IuVault.RUNTIME.get("test.password").getValue()));
		loginFormParams.put("_eventId_proceed", List.of(""));
		final var loginFormQuery = IuWebUtils.createQueryString(loginFormParams);

		final var loginFormPost = HttpRequest.newBuilder(loginFormUri)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(BodyPublishers.ofString(loginFormQuery)).build();
		final var loginSuccessResponse = http.send(loginFormPost, BodyHandlers.ofString());
		assertEquals(200, loginSuccessResponse.statusCode());
		final var parsedLoginSuccessForm = Jsoup.parse(loginSuccessResponse.body()).selectFirst("form");
		assertEquals(postUri.toString(), parsedLoginSuccessForm.attr("action"));
		assertEquals("POST", parsedSecondLoginRedirectForm.attr("method").toUpperCase());

		final var loginSuccessParams = new LinkedHashMap<String, String>();
		for (final var i : parsedLoginSuccessForm.select("input[type='hidden']"))
			loginSuccessParams.put(i.attr("name"), i.attr("value"));

		// verify relay state and SAML response values
		assertEquals(relayState, loginSuccessParams.get("RelayState"));
		final var samlResponse = loginSuccessParams.get("SAMLResponse");

		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		IuTestLogger.allow(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 .*");
		IuTestLogger.expect(IuSubjectConfirmationValidator.class.getName(), Level.INFO,
				"IP address mismatch in SAML subject confirmation; remote address = .*");
		assertDoesNotThrow(
				() -> samlSessionVerifier.verifyResponse(preAuthSession, "127.0.0.1", samlResponse, relayState));
		verify(prePostAuthDetail).setName(argThat(a -> {
			when(prePostAuthDetail.getName()).thenReturn(a);
			return true;
		}));
		verify(prePostAuthDetail).setRealm(argThat(a -> {
			when(prePostAuthDetail.getRealm()).thenReturn(a);
			return true;
		}));
		verify(prePostAuthDetail).setIssueTime(argThat(a -> {
			when(prePostAuthDetail.getIssueTime()).thenReturn(a);
			return true;
		}));
		verify(prePostAuthDetail).setExpires(argThat(a -> {
			when(prePostAuthDetail.getExpires()).thenReturn(a);
			return true;
		}));
		verify(prePostAuthDetail).setAuthTime(argThat(a -> {
			when(prePostAuthDetail.getAuthTime()).thenReturn(a);
			return true;
		}));
		verify(prePostAuthDetail).setAssertions(argThat(a -> {
			when(prePostAuthDetail.getAssertions()).thenReturn(a);
			return true;
		}));

		final var postAuthSession = mock(IuSession.class);
		final var postAuthDetail = mock(SamlPostAuthentication.class);
		when(postAuthSession.getDetail(SamlPostAuthentication.class)).thenReturn(postAuthDetail);

		final var iuSamlPrincipal = samlSessionVerifier.getPrincipalIdentity(preAuthSession, postAuthSession);
		verify(postAuthDetail).setName(argThat(a -> {
			when(postAuthDetail.getName()).thenReturn(a);
			return a.equals(prePostAuthDetail.getName());
		}));
		verify(postAuthDetail).setRealm(argThat(a -> {
			when(postAuthDetail.getRealm()).thenReturn(a);
			return a.equals(prePostAuthDetail.getRealm());
		}));
		verify(postAuthDetail).setIssueTime(argThat(a -> {
			when(postAuthDetail.getIssueTime()).thenReturn(a);
			return a.equals(prePostAuthDetail.getIssueTime());
		}));
		verify(postAuthDetail).setExpires(argThat(a -> {
			when(postAuthDetail.getExpires()).thenReturn(a);
			return a.equals(prePostAuthDetail.getExpires());
		}));
		verify(postAuthDetail).setAuthTime(argThat(a -> {
			when(postAuthDetail.getAuthTime()).thenReturn(a);
			return a.equals(prePostAuthDetail.getAuthTime());
		}));
		verify(postAuthDetail).setAssertions(argThat(a -> {
			when(postAuthDetail.getAssertions()).thenReturn(a);
			return IuIterable.remaindersAreEqual(a.iterator(), prePostAuthDetail.getAssertions().iterator());
		}));

		IuPrincipalIdentity.verify(iuSamlPrincipal, REALM);

		final var subject = iuSamlPrincipal.getSubject();
		final var assertions = subject.getPublicCredentials(IuSamlAssertion.class);
		assertEquals(1, assertions.size());

		final var assertion = assertions.iterator().next();
		assertEquals(iuSamlPrincipal.getName(),
				assertion.getAttributes().get(IuSamlAssertion.EDU_PERSON_PRINCIPAL_NAME_OID));

		assertNotNull(assertion.getAttributes().get(IuSamlAssertion.DISPLAY_NAME_OID));
		assertNotNull(assertion.getAttributes().get(IuSamlAssertion.MAIL_OID));

		final Instant now = Instant.now();
		final Instant latestValid = now.plus(Duration.ofMinutes(5));
		Instant issueInstant = iuSamlPrincipal.getIssuedAt();
		Instant notBefore = assertion.getNotBefore();
		Instant notOnOrAfter = assertion.getNotOnOrAfter();

		assertFalse(issueInstant.isAfter(latestValid));
		assertFalse(notBefore.isAfter(latestValid));
		assertFalse(notOnOrAfter.isBefore(now.minus(Duration.ofMinutes(5))));
	}
}
