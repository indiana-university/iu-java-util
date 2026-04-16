package iu.saml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
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
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
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
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.client.IuHttp;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.X500Utils;
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
		final var postAuth = mock(SamlPreAuthentication.class);
		when(session.getDetail(SamlPreAuthentication.class)).thenReturn(postAuth);

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
			verify(postAuth).setPostUri(postUri);
			verify(postAuth).setReturnUri(returnUri);

			final var sessionIdListener = new StringListener();
			verify(postAuth).setSessionId(argThat(sessionIdListener));
			final var relayStateListener = new StringListener();
			verify(postAuth).setRelayState(argThat(relayStateListener));

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

}
