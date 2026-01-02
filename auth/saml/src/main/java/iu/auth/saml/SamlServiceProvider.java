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
package iu.auth.saml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import javax.security.auth.Subject;

import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.Response;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuSamlServiceProviderMetadata;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuAuthConfig;
import iu.auth.config.IuSamlServiceProvider;
import iu.auth.config.IuTrustedIssuer;
import iu.auth.principal.PrincipalVerifier;

/**
 * SAML Service Provider implementation class.
 */
public final class SamlServiceProvider implements IuSamlServiceProvider, PrincipalVerifier<SamlPrincipal> {
	static {
		IuObject.assertNotOpen(SamlServiceProvider.class);
	}

	private static final Logger LOG = Logger.getLogger(SamlServiceProvider.class.getName());

	/**
	 * Locates a SAML Service Provider configured with a given HTTP POST Binding URI
	 * as its {@link IuAuthConfig#getAuthenticationEndpoint() registered
	 * authentication endpoint}.
	 * 
	 * @param postUri HTTP POST Binding URI
	 * @return {@link SamlServiceProvider}
	 */
	static SamlServiceProvider withBinding(URI postUri) {
		Iterable<IuSamlServiceProvider> providers = AuthConfig.get(IuSamlServiceProvider.class);
		for (final var sp : providers) {
			final var samlServiceProvider = (SamlServiceProvider) sp;
			if (postUri.equals(samlServiceProvider.postUri))
				return samlServiceProvider;
		}
		throw new IllegalArgumentException();
	}

	private final String realm;
	private final URI postUri;
	private final SamlBuilder samlBuilder;

	/**
	 * Initialize SAML provider
	 * 
	 * @param postUri HTTP POST Binding URI
	 * @param realm   authentication realm to use for reloading the config as needed
	 *                for future operations.
	 * @param config  {@link IuSamlServiceProviderMetadata}
	 */
	public SamlServiceProvider(URI postUri, String realm, IuSamlServiceProviderMetadata config) {
		var matchAcs = false;
		for (final var acsUri : config.getAcsUris())
			if (acsUri.equals(postUri)) {
				matchAcs = true;
				break;
			}

		if (!matchAcs)
			throw new IllegalArgumentException(
					"Post URI doesn't match with allowed list of Assertion Consumer Service URLs");

		this.postUri = postUri;
		this.realm = realm;
		this.samlBuilder = new SamlBuilder(config);
	}

	@Override
	public String getRealm() {
		return realm;
	}

	@Override
	public String getAuthScheme() {
		return null;
	}

	@Override
	public URI getAuthenticationEndpoint() {
		return postUri;
	}

	@Override
	public Class<SamlPrincipal> getType() {
		return SamlPrincipal.class;
	}

	@Override
	public boolean isAuthoritative() {
		return true;
	}

	@Override
	public void verify(SamlPrincipal id) throws IuAuthenticationException {
		id.verify(realm);
		LOG.info(() -> "saml:verify:" + id.getName() + "; serviceProvider: " + realm);
	}

	/**
	 * Gets the service provider metadata for external hosting.
	 * 
	 * @return SP metadata
	 */
	public String getServiceProviderMetaData() {
		return samlBuilder.getServiceProviderMetadata();
	}

	/**
	 * Generate SAML authentication request use by client to redirect user to
	 * identity provider system for authentication.
	 * 
	 * @param relayState RelayState parameter value
	 * @param sessionId  SAML AuthnRequest ID
	 * @return SAML AuthnRequest Redirect URI
	 */
	URI getAuthnRequest(String relayState, String sessionId) {
		final var destinationLocation = samlBuilder.singleSignOnLocation.toString();

		final var authnRequest = (AuthnRequest) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME).buildObject(AuthnRequest.DEFAULT_ELEMENT_NAME);
		authnRequest.setAssertionConsumerServiceURL(postUri.toString());
		authnRequest.setDestination(destinationLocation);
		authnRequest.setID(sessionId);
		authnRequest.setIssueInstant(Instant.now());
		authnRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authnRequest.setVersion(SAMLVersion.VERSION_20);

		final var issuer = (Issuer) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(Issuer.DEFAULT_ELEMENT_NAME).buildObject(Issuer.DEFAULT_ELEMENT_NAME);
		issuer.setValue(samlBuilder.serviceProviderEntityId);
		authnRequest.setIssuer(issuer);

		final var nameIdPolicy = (NameIDPolicy) XMLObjectProviderRegistrySupport.getBuilderFactory()
				.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME).buildObject(NameIDPolicy.DEFAULT_ELEMENT_NAME);
		nameIdPolicy.setAllowCreate(true);
		authnRequest.setNameIDPolicy(nameIdPolicy);

		final var samlRequest = IuException.unchecked(() -> {
			var s = XmlDomUtil.getContent(XMLObjectProviderRegistrySupport.getMarshallerFactory()
					.getMarshaller(authnRequest).marshall(authnRequest));
			if (s.startsWith("<?xml")) {
				StringBuilder sb = new StringBuilder(s);
				int i = sb.indexOf("?>\n", 4);
				if (i != -1)
					sb.delete(0, i + 3);
				s = sb.toString();
			}

			final var deflater = new Deflater(Deflater.DEFLATED, true);
			final var samlRequestBuffer = new ByteArrayOutputStream();
			try (DeflaterOutputStream d = new DeflaterOutputStream(samlRequestBuffer, deflater)) {
				d.write(IuText.utf8(s));
			}

			return Base64.getEncoder().encodeToString(samlRequestBuffer.toByteArray());
		});

		final Map<String, Iterable<String>> idpParams = new LinkedHashMap<>();
		idpParams.put("SAMLRequest", Collections.singleton(samlRequest));
		idpParams.put("RelayState", Collections.singleton(relayState));
		return URI.create(destinationLocation + '?' + IuWebUtils.createQueryString(idpParams));
	}

	/**
	 * Gets the token signature verification algorithm.
	 * 
	 * @return {@link Algorithm}
	 */
	Algorithm getVerifyAlg() {
		return samlBuilder.verifyAlg;
	}

	/**
	 * Gets the token signature verification key.
	 * 
	 * @return {@link WebKey}
	 */
	WebKey getVerifyKey() {
		final var config = AuthConfig.load(IuSamlServiceProviderMetadata.class, realm);
		final var identity = serviceProviderIdentity(config);
		return identity.getSubject().getPrivateCredentials(WebKey.class).stream()
				.filter(a -> "verify".equals(a.getKeyId())).findFirst().get();
	}

	/**
	 * Looks for an authoritative trusted issuer identity matching the SAML Service
	 * Provider configuration and returns its principal identity.
	 * 
	 * <p>
	 * This identity will have {@link Subject#getPrivateCredentials(Class) private}
	 * {@link WebKey} {@link Subject#getPrivateCredentials(Class) credentials} with
	 * kid values {@code verify} and {@code encrypt} suitable for use with
	 * SP-related crypto operations.
	 * </p>
	 * 
	 * @param config {@link IuSamlServiceProviderMetadata}
	 * @return {@link IuPrincipalIdentity}
	 */
	static IuPrincipalIdentity serviceProviderIdentity(IuSamlServiceProviderMetadata config) {
		final var identity = IuIterable.stream(AuthConfig.get(IuTrustedIssuer.class)) //
				.map(a -> a.getPrincipal(config.getIdentity())) //
				.filter(Objects::nonNull).findFirst() //
				.orElseThrow(() -> new IllegalStateException("service provider is not trusted"));

		if (!IuException.unchecked(() -> IuPrincipalIdentity.verify(identity, identity.getName())))
			throw new IllegalStateException("service provider is not authoritative");
		else
			return identity;
	}

	/**
	 * Authorize SAML response return back from IDP
	 * 
	 * @param address      IP address use by user to authenticate
	 * @param samlResponse xml SAML response back from identity provider
	 * @param sessionId    SAML session ID from original AuthnRequest
	 * @return {@link SamlPrincipal}
	 */
	SamlPrincipal verifyResponse(InetAddress address, String samlResponse, String sessionId) {
		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		try {
			current.setContextClassLoader(XMLObjectSupport.class.getClassLoader());

			final var response = IuException.unchecked(() -> {
				try (InputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(samlResponse))) {
					return (Response) XMLObjectSupport
							.unmarshallFromInputStream(XMLObjectProviderRegistrySupport.getParserPool(), in);
				}
			}, "Invalid SAMLResponse");

			String entityId = response.getIssuer().getValue();
			LOG.fine(() -> "SAML2 authentication response\nEntity ID: " + entityId + "\nPOST URL: " + postUri.toString()
					+ "\n" + XmlDomUtil.getContent(response.getDOM()));

			final var samlResponseValidator = new SamlResponseValidator(realm, postUri, entityId, sessionId, address, samlBuilder);
			return IuException.unchecked(() -> samlResponseValidator.validate(response));

		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}
}
