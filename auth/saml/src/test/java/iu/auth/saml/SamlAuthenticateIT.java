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
package iu.auth.saml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import edu.iu.IuWebUtils;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.auth.saml.IuSamlAssertion;
import edu.iu.auth.saml.IuSamlSession;
import edu.iu.client.IuHttp;
import edu.iu.client.IuVault;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.test.IuTestLogger;
import iu.auth.config.AuthConfig;
import iu.auth.pki.PkiVerifier;

@EnabledIf("edu.iu.client.IuVault#isConfigured")
@SuppressWarnings("javadoc")
public class SamlAuthenticateIT {

	private static final String REALM = "iu-saml-test";
	private static URI postUri;
	private static URI entryPointUri;

	@BeforeAll
	public static void setupClass() {
		AuthConfig.registerInterface("realm", IuSamlServiceProviderMetadata.class, IuVault.RUNTIME);
		final var realm = AuthConfig.load(IuSamlServiceProviderMetadata.class, REALM);
		postUri = realm.getAcsUris().iterator().next();
		entryPointUri = URI.create("test:" + IdGenerator.generateId());

		AuthConfig.register(new PkiVerifier(realm.getIdentity()));

		final var provider = new SamlServiceProvider(postUri, REALM, realm);
		AuthConfig.register(provider);
		AuthConfig.seal();

		final var identity = provider.serviceProviderIdentity(realm);
		System.out.println("Verified SAML Service Provider " + identity);
	}

	@BeforeEach
	public void setup() {
		IuTestLogger.allow(IuHttp.class.getName(), Level.FINE);
	}

	@Test
	public void testSamlAuthentication() throws Exception {
//		final var entityId = config.getIdentityProviderEntityId();
//		URI applicationUri = IuException.unchecked(() -> new URI(applicationUrl));
//		var sessionId = IdGenerator.generateId();
//		System.out.println("sessionId " + sessionId);
		final var secret = WebKey.ephemeral(Encryption.A256GCM).getKey();
		IuSamlSession samlSession = IuSamlSession.create(entryPointUri, postUri, () -> secret);

		final var location = samlSession.getRequestUri();
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
		assertDoesNotThrow(() -> samlSession.verifyResponse("127.0.0.1", samlResponse, relayState));

		final var activatedSession = IuSamlSession.activate(samlSession.toString(), () -> secret);
		final var iuSamlPrincipal = activatedSession.getPrincipalIdentity();
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

// TODO REVIEW AND REMOVE
//private static SamlServiceProviderConfig config;

//private static File metaData;
//private static String ldpMetaDataUrl;
//private static String providerEntityId = System.getenv("SERVICE_PROVIDER_ENTITY_ID");
//private static String postUrl = System.getenv("POST_URL");
//private static String applicationUrl = System.getenv("APPLICATION_URL");

//private final class TrustedId implements IuPrincipalIdentity {
//
//	private final IuPrivateKeyPrincipal pkp;
//	private final Instant issuedAt = Instant.now();
//	// private final Instant authTime =
//	// pkp.getJwk().getCertificateChain()[0].getNotBefore().toInstant();
//	// private final Instant expires =
//	// pkp.getJwk().getCertificateChain()[0].getNotAfter().toInstant();
//
//	private TrustedId(IuPrivateKeyPrincipal pkp) {
//		this.pkp = pkp;
//	}
//
//	@Override
//	public String getName() {
//		return pkp.getJwk().getCertificateChain()[0].getSubjectX500Principal().getName();
//	}
//
//	@Override
//	public Instant getIssuedAt() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Instant getAuthTime() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Instant getExpires() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Subject getSubject() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//}
//

//config = Realm.of(REALM);
//
//String samlCertificate = IuVault.RUNTIME.get("thirdparty.saml.certificate");
//String privateKey = IuVault.RUNTIME.get("thirdparty.saml.privateKey");
//ldpMetaDataUrl = "https://idp-stg.login.iu.edu/idp/shibboleth"; // IuVault.RUNTIME.get("iu.ldp.stg.metadata.url");
//HttpRequest request = IuException.unchecked(() -> HttpRequest.newBuilder().GET() //
//		.uri(new URI(ldpMetaDataUrl)) //
//		.build());
//
//final var response = IuException
//		.unchecked(() -> HttpClient.newHttpClient().send(request, BodyHandlers.ofInputStream()));
//int statusCode = response.statusCode();
//if (statusCode == 200) {
//	InputStream is = response.body();
//	String xml = XmlDomUtil.xmlToString(is);
//	metaData = IuException.unchecked(() -> File.createTempFile("idp-stg-metadta-test", ".xml"));
//	BufferedWriter bw = IuException.unchecked(() -> new BufferedWriter(new FileWriter(metaData, true)));
//	IuException.unchecked(() -> bw.write(xml));
//	IuException.unchecked(() -> bw.close());
//}
//
//IuSamlProvider provider = IuSamlProvider.from(new SamlServiceProviderConfig() {
//
//	@Override
//	public String getServiceProviderEntityId() {
//		return providerEntityId;
//	}
//
//	@Override
//	public List<URI> getMetaDataUris() {
//		URI entityId = IuException.unchecked(() -> new URI(ldpMetaDataUrl));
//		return Arrays.asList(entityId);
//	}
//
//	@Override
//	public X509Certificate getCertificate() {
//		return PemEncoded.parse(samlCertificate).next().asCertificate();
//	}
//
//	@Override
//	public List<URI> getAcsUris() {
//		return IuException.unchecked(() -> Arrays.asList(new URI(postUrl)));
//	}
//
//	@Override
//	public String getPrivateKey() {
//		return privateKey;
//	}
//
//	@Override
//	public List<String> getAllowedRange() {
//		return IuException.unchecked(() -> Arrays.asList("127.0.0.0"));
//	}
//
//	@Override
//	public Duration getAuthenticatedSessionTimeout() {
//		return Duration.ofMinutes(2L);
//	}
//
//	@Override
//	public URI getApplicationUri() {
//		return IuException.unchecked(() -> new URI(applicationUrl));
//	}
//
//});
// TODO: REMOVE or @AfterAll
//metaData.delete();

//IuTestLogger.allow("iu.auth.saml.SamlProvider", Level.FINE);
