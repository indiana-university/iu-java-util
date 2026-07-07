/*
 * Copyright © 2026 Indiana University
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
package iu.saml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.KeyInfo;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.iu.IdGenerator;
import edu.iu.IuIterable;
import edu.iu.IuProcess;
import edu.iu.IuRequestAttributes;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.client.IuHttp;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.X500Utils;
import edu.iu.saml.IuSamlAssertion;
import edu.iu.session.IuSession;
import edu.iu.session.IuSessionHandler;
import edu.iu.test.IuTestLogger;
import iu.saml.config.IuSamlServiceProviderMetadata;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.Criterion;
import net.shibboleth.shared.xml.ParserPool;

@SuppressWarnings("javadoc")
@ExtendWith(IuHttpAware.class)
public class SamlServiceProviderTest {

	@BeforeEach
	void setup() {
		IuTestLogger.allow("org.apache.xml", Level.FINE);
		IuTestLogger.allow("org.opensaml", Level.FINE);
		IuTestLogger.allow("edu.iu.crypt", Level.CONFIG);
		IuTestLogger.allow("net.shibboleth", Level.FINE);
	}

	@SuppressWarnings("unchecked")
	@Test
	void testReadXml() {
		final var resp = mock(HttpResponse.class);
		when(resp.statusCode()).thenReturn(200);

		final var body = mock(InputStream.class);
		when(resp.body()).thenReturn(body);

		final var parserPool = mock(ParserPool.class);
		final var doc = mock(Document.class);
		assertDoesNotThrow(() -> when(parserPool.parse(body)).thenReturn(doc));

		try (final var mockXMLObjectProviderRegistrySupport = mockStatic(XMLObjectProviderRegistrySupport.class)) {
			mockXMLObjectProviderRegistrySupport.when(() -> XMLObjectProviderRegistrySupport.getParserPool())
					.thenReturn(parserPool);
			assertEquals(doc, assertDoesNotThrow(() -> SamlServiceProvider.READ_XML.apply(resp)));
		}
	}

	@Test
	void testReadMetadata() {
		final var config = mock(IuSamlServiceProviderMetadata.class);
		final var metadataUri = URI.create(IdGenerator.generateId());
		when(config.getMetadataUris()).thenReturn(IuIterable.iter(metadataUri));

		final var document = mock(Document.class);
		IuHttpAware.mock.when(() -> IuHttp.get(metadataUri, SamlServiceProvider.READ_XML)).thenReturn(document);

		final var element = mock(Element.class);
		when(document.getDocumentElement()).thenReturn(element);
		try (final var mockDOMMetadataResolver = mockConstruction(DOMMetadataResolver.class, (a, ctx) -> {
			assertEquals(element, ctx.arguments().get(0));
		}); final var mockChainingMetadataResolver = mockConstruction(ChainingMetadataResolver.class)) {
			final var metadata = (ChainingMetadataResolver) SamlServiceProvider.readMetadata(config);

			final var domResolver = mockDOMMetadataResolver.constructed().get(0);
			verify(domResolver).setId(any(String.class));
			verify(domResolver).setRequireValidMetadata(true);
			assertDoesNotThrow(() -> verify(domResolver).initialize());

			assertEquals(mockChainingMetadataResolver.constructed().get(0), metadata);
			verify(metadata).setId(any(String.class));
			assertDoesNotThrow(() -> verify(metadata).setResolvers(List.of(domResolver)));
			assertDoesNotThrow(() -> verify(metadata).initialize());
		}
	}

	@Test
	void testGetIdpSSO() {
		final var metadata = mock(MetadataResolver.class);
		final var entityId = IdGenerator.generateId();
		final var entity = mock(EntityDescriptor.class);
		final var idpSso = mock(IDPSSODescriptor.class);
		when(entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol")).thenReturn(idpSso);

		try (final var mockEntityIdCriterion = mockConstruction(EntityIdCriterion.class, (a, ctx) -> {
			assertEquals(entityId, ctx.arguments().get(0));
		}); final var mockCriteriaSet = mockConstruction(CriteriaSet.class, (a, ctx) -> {
			assertArrayEquals(new Criterion[] { mockEntityIdCriterion.constructed().get(0) },
					(Criterion[]) ctx.arguments().get(0));
		})) {
			assertDoesNotThrow(() -> when(metadata.resolveSingle(any())).thenReturn(entity));
			assertEquals(idpSso, SamlServiceProvider.getIdpSSO(entityId, metadata));
			assertDoesNotThrow(() -> verify(metadata).resolveSingle(mockCriteriaSet.constructed().get(0)));
		}
	}

	@Test
	void testCreateTrustEngine() {
		final var metadata = mock(MetadataResolver.class);
		final var entityId = IdGenerator.generateId();
		final var entity = mock(EntityDescriptor.class);
		final var idpSso = mock(IDPSSODescriptor.class);
		when(entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol")).thenReturn(idpSso);

		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(IdGenerator.generateId()).use(Use.SIGN).ephemeral()
				.build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
		IuProcess.deleteTempFiles();
		final var identity = WebKey.builder(Algorithm.EDDSA).keyId(IdGenerator.generateId()).use(Use.SIGN)
				.key(privateKey).key(jwk.getPublicKey()).pem(pemCert).build();

		final var keyDescriptor = mock(KeyDescriptor.class);
		when(idpSso.getKeyDescriptors()).thenReturn(List.of(keyDescriptor));
		final var keyInfo = mock(KeyInfo.class);
		when(keyDescriptor.getKeyInfo()).thenReturn(keyInfo);
		final var x509Data = mock(X509Data.class);
		when(keyInfo.getX509Datas()).thenReturn(List.of(x509Data));
		final var x509Cert = mock(X509Certificate.class);
		assertDoesNotThrow(() -> when(x509Cert.getValue())
				.thenReturn(IuText.base64(identity.getCertificateChain()[0].getEncoded())));
		when(x509Data.getX509Certificates()).thenReturn(List.of(x509Cert));

		try (final var mockEntityIdCriterion = mockConstruction(EntityIdCriterion.class, (a, ctx) -> {
			assertEquals(entityId, ctx.arguments().get(0));
		}); final var mockCriteriaSet = mockConstruction(CriteriaSet.class, (a, ctx) -> {
			assertArrayEquals(new Criterion[] { mockEntityIdCriterion.constructed().get(0) },
					(Criterion[]) ctx.arguments().get(0));
		}); final var mockBasicX509Credential = mockConstruction(BasicX509Credential.class, (a, ctx) -> {
			assertEquals(identity.getCertificateChain()[0], ctx.arguments().get(0));
		}); final var mockStaticCredentialResolver = mockConstruction(StaticCredentialResolver.class, (a, ctx) -> {
			assertEquals(List.of(mockBasicX509Credential.constructed().get(0)), ctx.arguments().get(0));
		});
				final var mockStaticKeyInfoCredentialResolver = mockConstruction(StaticKeyInfoCredentialResolver.class,
						(a, ctx) -> {
							assertEquals(List.of(mockBasicX509Credential.constructed().get(0)), ctx.arguments().get(0));
						});
				final var mockExplicitKeySignatureTrustEngine = mockConstruction(ExplicitKeySignatureTrustEngine.class,
						(a, ctx) -> {
							assertEquals(mockStaticCredentialResolver.constructed().get(0), ctx.arguments().get(0));
							assertEquals(mockStaticKeyInfoCredentialResolver.constructed().get(0),
									ctx.arguments().get(1));
						})) {
			assertDoesNotThrow(() -> when(metadata.resolveSingle(any())).thenReturn(entity));
			final var trustEngine = SamlServiceProvider.createTrustEngine(entityId, metadata);
			assertEquals(trustEngine, mockExplicitKeySignatureTrustEngine.constructed().get(0));
			assertDoesNotThrow(() -> verify(metadata).resolveSingle(mockCriteriaSet.constructed().get(0)));
		}
	}

	@Test
	void testSpMetadata() {
		final var jwk = WebKey.builder(Algorithm.EDDSA).keyId(IdGenerator.generateId()).use(Use.SIGN).ephemeral()
				.build();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(PemEncoded::print, privateKey);
		IuTestLogger.allow(IuProcess.class.getName(), Level.FINE);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", "1", //
				"-subj", "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + X500Utils.keyUsage(jwk) //
		);
		IuProcess.deleteTempFiles();
		final var identity = WebKey.builder(Algorithm.EDDSA).keyId(IdGenerator.generateId()).use(Use.SIGN)
				.key(privateKey).key(jwk.getPublicKey()).pem(pemCert).build();

		final var postUri = URI.create(IdGenerator.generateId());
		final var spEntityId = IdGenerator.generateId();
		final var config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getIdentity()).thenReturn(identity);
		when(config.getAcsUris()).thenReturn(IuIterable.iter(postUri));
		when(config.getServiceProviderEntityId()).thenReturn(spEntityId);

		assertDoesNotThrow(() -> XmlDomUtil.parse(SamlServiceProvider.getServiceProviderMetadata(config)));
	}

	@Test
	void testInstanceMetadata() {
		final var config = mock(IuSamlServiceProviderMetadata.class);
		final var sp = new SamlServiceProvider(() -> config, null);
		final var spMetadata = IdGenerator.generateId();
		try (final var mockSp = mockStatic(SamlServiceProvider.class)) {
			mockSp.when(() -> SamlServiceProvider.getServiceProviderMetadata(config)).thenReturn(spMetadata);
			assertEquals(spMetadata, sp.metadata());
		}
	}

	@Test
	void testGetMetadata() {
		final var config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getMetadataTtl()).thenReturn(Duration.ofSeconds(1L));

		final var sp = new SamlServiceProvider(() -> config, null);
		final var metadata = mock(MetadataResolver.class);
		try (final var mockSp = mockStatic(SamlServiceProvider.class)) {
			mockSp.when(() -> SamlServiceProvider.readMetadata(config)).thenReturn(metadata);
			assertEquals(metadata, sp.getMetadataResolver(config));
			mockSp.verify(() -> SamlServiceProvider.readMetadata(config));
			mockSp.clearInvocations();
			assertEquals(metadata, sp.getMetadataResolver(config));
			mockSp.verify(() -> SamlServiceProvider.readMetadata(config), times(0));
			assertDoesNotThrow(() -> Thread.sleep(1000L));
			assertEquals(metadata, sp.getMetadataResolver(config));
			mockSp.verify(() -> SamlServiceProvider.readMetadata(config));
		}
	}

	@Test
	void testInit() throws IOException, UnmarshallingException {
		final var entityId = IdGenerator.generateId();
		final var config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getIdentityProviderEntityIds()).thenReturn(IuIterable.iter(entityId));

		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		when(sessionHandler.create()).thenReturn(session);
		final var preAuth = mock(SamlPreAuthentication.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(preAuth);

		final var sp = new SamlServiceProvider(() -> config, sessionHandler);

		final var postUri = URI.create(IdGenerator.generateId());
		final var returnUri = URI.create(IdGenerator.generateId());
		final var ssoLocation = URI.create(IdGenerator.generateId());

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var metadata = mock(MetadataResolver.class);
		final var idpSso = mock(IDPSSODescriptor.class);
		final var sso = mock(SingleSignOnService.class);
		when(sso.getBinding()).thenReturn("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect");
		when(sso.getLocation()).thenReturn(ssoLocation.toString());
		when(idpSso.getSingleSignOnServices()).thenReturn(List.of(sso));

		try (final var mockSp = mockStatic(SamlServiceProvider.class)) {
			mockSp.when(() -> SamlServiceProvider.readMetadata(config)).thenReturn(metadata);
			mockSp.when(() -> SamlServiceProvider.getIdpSSO(entityId, metadata)).thenReturn(idpSso);
			final var redirect = sp.initRequest(postUri, returnUri);
			verify(session).setStrict(false);
			assertEquals(setCookie, redirect.getSetCookie());
			assertTrue(redirect.getLocation().toString().startsWith(ssoLocation + "?"),
					redirect.getLocation().toString());

			final var query = redirect.getLocation().getRawQuery();
			final var params = IuWebUtils.parseQueryString(query);

			class StringListener implements ArgumentMatcher<String> {
				String value;

				@Override
				public boolean matches(String argument) {
					value = argument;
					return true;
				}
			}
			verify(preAuth).setPostUri(postUri);
			verify(preAuth).setReturnUri(returnUri);

			final var sessionIdListener = new StringListener();
			verify(preAuth).setSessionId(argThat(sessionIdListener));
			final var relayStateListener = new StringListener();
			verify(preAuth).setRelayState(argThat(relayStateListener));

			assertEquals(relayStateListener.value, params.get("RelayState").iterator().next());

			final var inflater = new Inflater(true);
			final var samlRequestBuffer = new ByteArrayOutputStream();
			try (final var d = new InflaterOutputStream(samlRequestBuffer, inflater)) {
				d.write(IuText.base64(params.get("SAMLRequest").iterator().next()));
			}

			final var xml = XmlDomUtil.parse(samlRequestBuffer.toString()).getDocumentElement();
			assertEquals(sessionIdListener.value, xml.getAttribute("ID"));
			assertEquals(ssoLocation.toString(), xml.getAttribute("Destination"));
			assertEquals(postUri.toString(), xml.getAttribute("AssertionConsumerServiceURL"));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testVerifyResponseInvalid() {
		final var entityId = IdGenerator.generateId();
		final var config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getIdentityProviderEntityIds()).thenReturn(IuIterable.iter(entityId));

		final var postUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var cookies = mock(Iterable.class);
		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getRequestUri()).thenReturn(postUri);
		when(requestAttributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(SamlPreAuthentication.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(preAuth);

		final var returnUri = URI.create(IdGenerator.generateId());
		final var sessionId = IdGenerator.generateId();
		final var relayState = IdGenerator.generateId();
		when(preAuth.getPostUri()).thenReturn(postUri);
		when(preAuth.getReturnUri()).thenReturn(returnUri);
		when(preAuth.getSessionId()).thenReturn(sessionId);
		when(preAuth.getRelayState()).thenReturn(relayState);

		final var postAuth = mock(SamlPostAuthentication.class);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var sp = new SamlServiceProvider(() -> config, sessionHandler);

		IuTestLogger.expect(SamlServiceProvider.class.getName(), Level.INFO, "Invalid SAML Response",
				IllegalArgumentException.class, a -> a.getMessage().equals("RelayState mismatch"));
		final var redirect = sp.verifyResponse(requestAttributes, IdGenerator.generateId(), IdGenerator.generateId());

		verify(postAuth).setInvalid(true);

		assertEquals(returnUri, redirect.getLocation());
		assertEquals(setCookie, redirect.getSetCookie());
	}

	@SuppressWarnings("unchecked")
	@Test
	void testVerifyResponse() {
		final var entityId = IdGenerator.generateId();
		final var config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getIdentityProviderEntityIds()).thenReturn(IuIterable.iter(entityId));

		final var postUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var cookies = mock(Iterable.class);
		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getRequestUri()).thenReturn(postUri);
		when(requestAttributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(SamlPreAuthentication.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(preAuth);

		final var returnUri = URI.create(IdGenerator.generateId());
		final var sessionId = IdGenerator.generateId();
		final var relayState = IdGenerator.generateId();
		when(preAuth.getPostUri()).thenReturn(postUri);
		when(preAuth.getReturnUri()).thenReturn(returnUri);
		when(preAuth.getSessionId()).thenReturn(sessionId);
		when(preAuth.getRelayState()).thenReturn(relayState);

		final var postAuth = mock(SamlPostAuthentication.class);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var name = IdGenerator.generateId();
		final var authnAuthority = IdGenerator.generateId();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plusSeconds(15L);
		final var authnAssertion = mock(IuSamlAssertion.class);
		final var assertions = List.of(authnAssertion);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var samlResponseBytes = new byte[32];
		ThreadLocalRandom.current().nextBytes(samlResponseBytes);
		final var samlResponse = IuText.base64(samlResponseBytes);
		final var sp = new SamlServiceProvider(() -> config, sessionHandler);

		final var responseXml = "<Response>" + IdGenerator.generateId() + "</Response>";
		final var responseDom = XmlDomUtil.parse(responseXml).getDocumentElement();
		final var response = mock(Response.class);
		when(response.getDOM()).thenReturn(responseDom);
		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn(entityId);
		when(response.getIssuer()).thenReturn(issuer);

		final var address = mock(InetAddress.class);
		final var metadata = mock(MetadataResolver.class);
		final var trustEngine = mock(ExplicitKeySignatureTrustEngine.class);
		final var samlPrincipal = mock(SamlPrincipal.class);
		when(samlPrincipal.getName()).thenReturn(name);
		when(samlPrincipal.getAuthnAuthority()).thenReturn(authnAuthority);
		when(samlPrincipal.getAuthnInstant()).thenReturn(authnInstant);
		when(samlPrincipal.getExpires()).thenReturn(expires);
		when(samlPrincipal.getAssertions()).thenReturn(assertions);

		try (final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
				final var mockSamlServiceProvider = mockStatic(SamlServiceProvider.class);
				final var mockIuWebUtils = mockStatic(IuWebUtils.class);
				final var mockSamlResponseValidator = mockConstruction(SamlResponseValidator.class, (a, ctx) -> {
					assertEquals(postUri, ctx.arguments().get(0));
					assertEquals(entityId, ctx.arguments().get(1));
					assertEquals(sessionId, ctx.arguments().get(2));
					assertEquals(address, ctx.arguments().get(3));
					assertEquals(config, ctx.arguments().get(4));
					assertEquals(trustEngine, ctx.arguments().get(5));
					when(a.validate(response)).thenReturn(samlPrincipal);
				})) {
			mockXMLObjectSupport.when(() -> XMLObjectSupport
					.unmarshallFromInputStream(eq(XMLObjectProviderRegistrySupport.getParserPool()), argThat(a -> {
						assertArrayEquals(samlResponseBytes, assertDoesNotThrow(() -> IuStream.read(a)));
						return true;
					}))).thenReturn(response);
			mockIuWebUtils.when(() -> IuWebUtils.getInetAddress(remoteAddr)).thenReturn(address);
			mockSamlServiceProvider.when(() -> SamlServiceProvider.readMetadata(config)).thenReturn(metadata);
			mockSamlServiceProvider.when(() -> SamlServiceProvider.createTrustEngine(entityId, metadata))
					.thenReturn(trustEngine);

			IuTestLogger.expect(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 authentication response\n"
					+ "Entity ID: " + entityId + "\n" + "POST URL: " + postUri + "\n" + responseXml);
			final var redirect = sp.verifyResponse(requestAttributes, samlResponse, relayState);

			verify(postAuth).setName(name);
			verify(postAuth).setAuthnAuthority(authnAuthority);
			verify(postAuth).setAuthnInstant(authnInstant);
			verify(postAuth).setExpires(expires);
			verify(postAuth).setAssertions(assertions);

			assertEquals(returnUri, redirect.getLocation());
			assertEquals(setCookie, redirect.getSetCookie());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testVerifyResponseSocial() {
		final var entityId = IdGenerator.generateId();
		final var config = mock(IuSamlServiceProviderMetadata.class);
		when(config.getIdentityProviderEntityIds()).thenReturn(IuIterable.iter(entityId));

		final var postUri = URI.create(IdGenerator.generateId());
		final var remoteAddr = IdGenerator.generateId();
		final var cookies = mock(Iterable.class);
		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getRequestUri()).thenReturn(postUri);
		when(requestAttributes.getRemoteAddr()).thenReturn(remoteAddr);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var preAuth = mock(SamlPreAuthentication.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(preAuth);

		final var returnUri = URI.create(IdGenerator.generateId());
		final var sessionId = IdGenerator.generateId();
		final var relayState = IdGenerator.generateId();
		when(preAuth.getPostUri()).thenReturn(postUri);
		when(preAuth.getReturnUri()).thenReturn(returnUri);
		when(preAuth.getSessionId()).thenReturn(sessionId);
		when(preAuth.getRelayState()).thenReturn(relayState);

		final var postAuth = mock(SamlPostAuthentication.class);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var name = IdGenerator.generateId();
		final var authnAuthority = IdGenerator.generateId();
		final var authnInstant = Instant.now();
		final var expires = authnInstant.plusSeconds(15L);
		final var authnAssertion = mock(IuSamlAssertion.class);
		final var assertions = List.of(authnAssertion);

		final var setCookie = IdGenerator.generateId();
		when(sessionHandler.store(session)).thenReturn(setCookie);

		final var samlResponseBytes = new byte[32];
		ThreadLocalRandom.current().nextBytes(samlResponseBytes);
		final var samlResponse = IuText.base64(samlResponseBytes);
		final var sp = new SamlServiceProvider(() -> config, sessionHandler);

		final var responseXml = "<Response>" + IdGenerator.generateId() + "</Response>";
		final var responseDom = XmlDomUtil.parse(responseXml).getDocumentElement();
		final var response = mock(Response.class);
		when(response.getDOM()).thenReturn(responseDom);
		final var issuer = mock(Issuer.class);
		when(issuer.getValue()).thenReturn(entityId);
		when(response.getIssuer()).thenReturn(issuer);

		final var address = mock(InetAddress.class);
		final var metadata = mock(MetadataResolver.class);
		final var trustEngine = mock(ExplicitKeySignatureTrustEngine.class);
		final var samlPrincipal = mock(SamlPrincipal.class);
		when(samlPrincipal.getName()).thenReturn(name);
		when(samlPrincipal.getAuthnAuthority()).thenReturn(authnAuthority);
		when(samlPrincipal.getAuthnInstant()).thenReturn(authnInstant);
		when(samlPrincipal.getExpires()).thenReturn(expires);
		when(samlPrincipal.getAssertions()).thenReturn(assertions);

		try (final var mockXMLObjectSupport = mockStatic(XMLObjectSupport.class);
				final var mockSamlServiceProvider = mockStatic(SamlServiceProvider.class);
				final var mockIuWebUtils = mockStatic(IuWebUtils.class);
				final var mockSamlResponseValidator = mockConstruction(SamlResponseValidator.class, (a, ctx) -> {
					assertEquals(postUri, ctx.arguments().get(0));
					assertEquals(entityId, ctx.arguments().get(1));
					assertEquals(sessionId, ctx.arguments().get(2));
					assertEquals(address, ctx.arguments().get(3));
					assertEquals(config, ctx.arguments().get(4));
					assertEquals(trustEngine, ctx.arguments().get(5));
					when(a.validate(response)).thenReturn(samlPrincipal);
				})) {
			mockXMLObjectSupport.when(() -> XMLObjectSupport
					.unmarshallFromInputStream(eq(XMLObjectProviderRegistrySupport.getParserPool()), argThat(a -> {
						assertArrayEquals(samlResponseBytes, assertDoesNotThrow(() -> IuStream.read(a)));
						return true;
					}))).thenReturn(response);
			mockIuWebUtils.when(() -> IuWebUtils.getInetAddress(remoteAddr)).thenReturn(address);
			mockSamlServiceProvider.when(() -> SamlServiceProvider.readMetadata(config)).thenReturn(metadata);
			mockSamlServiceProvider.when(() -> SamlServiceProvider.createTrustEngine(entityId, metadata))
					.thenReturn(trustEngine);

			IuTestLogger.expect(SamlServiceProvider.class.getName(), Level.FINE, "SAML2 authentication response\n"
					+ "Entity ID: " + entityId + "\n" + "POST URL: " + postUri + "\n" + responseXml);
			final var redirect = sp.verifyResponse(requestAttributes, samlResponse, null);

			verify(postAuth).setName(name);
			verify(postAuth).setAuthnAuthority(authnAuthority);
			verify(postAuth).setAuthnInstant(authnInstant);
			verify(postAuth).setExpires(expires);
			verify(postAuth).setAssertions(assertions);

			assertEquals(returnUri, redirect.getLocation());
			assertEquals(setCookie, redirect.getSetCookie());
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testGetPrincipal() {
		final var cookies = mock(Iterable.class);
		final var requestAttributes = mock(IuRequestAttributes.class);
		when(requestAttributes.getCookies()).thenReturn(cookies);

		final var sessionHandler = mock(IuSessionHandler.class);
		final var session = mock(IuSession.class);
		when(sessionHandler.activate(cookies)).thenReturn(session);

		final var postAuth = mock(SamlPostAuthentication.class);
		when(session.getDetail(SamlPostAuthentication.class)).thenReturn(postAuth);

		final var sp = new SamlServiceProvider(null, sessionHandler);

		final var principal = mock(SamlPrincipal.class);
		try (final var mockSamlPrincipal = mockStatic(SamlPrincipal.class)) {
			mockSamlPrincipal.when(() -> SamlPrincipal.from(postAuth)).thenReturn(principal);
			assertEquals(principal, sp.getPrincipalIdentity(requestAttributes));
		}
	}

}
