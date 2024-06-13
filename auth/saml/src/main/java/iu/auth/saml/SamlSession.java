package iu.auth.saml;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.config.IuSamlClient;
import edu.iu.auth.saml.IuSamlPrincipal;
import edu.iu.auth.saml.IuSamlSession;

/**
 * SAML session implementation to support session management
 */
public class SamlSession implements IuSamlSession, Serializable {

	/**
	 * SAML session logger
	 */
	private final Logger LOG = Logger.getLogger(SamlSession.class.getName());

	private static final long serialVersionUID = 1L;

	/**
	 * grants
	 */
	private final Map<String, RelayState> grants = new HashMap<>();

	/**
	 * service provider identity id
	 */
	private final String serviceProviderIdentityId;

	/**
	 * entry point
	 */
	private final URI entryPoint;

	/**
	 * principal
	 */
	private SamlPrincipal id;

	/**
	 * Constructor.
	 * 
	 * @param serviceProviderIdentityId authentication serviceProviderIdentityId
	 * @param entryPoint                {@link URI} to redirect the user agent to in
	 *                                  order restart the authentication process.
	 */
	public SamlSession(String serviceProviderIdentityId, URI entryPoint) {
		this.serviceProviderIdentityId = serviceProviderIdentityId;
		this.entryPoint = entryPoint;

	}

	@Override
	public URI getAuthenticationRequest(URI entityId, URI postUri, URI resourceUri) {
		SamlProvider provider = SamlConnectSpi.getProvider(serviceProviderIdentityId);
		IuSamlClient client = provider.getClient();

		if (!IuWebUtils.isRootOf(client.getApplicationUri(), resourceUri)) {
			throw new IllegalArgumentException("Invalid resource URI for this client");
		}

		var validEntityId = false;
		for (URI metaDataUri : client.getMetaDataUris()) {
			if (metaDataUri.compareTo(entityId) == 0)
				validEntityId = true;
		}
		if (!validEntityId) {
			throw new IllegalArgumentException(
					"Entity Id URI doesn't match with meta data URLs configured for this client" + entityId);
		}

		var destinationLocation = provider.getSingleSignOnLocation(entityId.toString());

		RelayState relayState = new RelayState(client.getApplicationUri());
		String state = IdGenerator.generateId();
		grants.put(state, relayState);

		ByteArrayOutputStream samlRequestBuffer = provider.getAuthRequest(postUri, relayState.getSession(),
				destinationLocation);

		Map<String, Iterable<String>> idpParams = new LinkedHashMap<>();
		idpParams.put("SAMLRequest",
				Collections.singleton(Base64.getEncoder().encodeToString(samlRequestBuffer.toByteArray())));

		idpParams.put("RelayState", Collections.singleton(state));

		URI redirectUri = IuException
				.unchecked(() -> new URI(destinationLocation + '?' + IuWebUtils.createQueryString(idpParams)));
		return redirectUri;
	}

	@Override
	public SamlPrincipal getPrincipalIdentity() throws IuAuthenticationException {
		SamlProvider provider = SamlConnectSpi.getProvider(serviceProviderIdentityId);

		IuSamlClient client = provider.getClient();
		Duration duration = client.getAuthenticatedSessionTimeout();
		Instant currentTime = Instant.now();
		Instant authnInstant = (Instant) id.getClaims().get("authnInstant");
		Instant totalSessiontime = authnInstant.plus(duration);
		if (currentTime.isBefore(totalSessiontime) && currentTime.isAfter(authnInstant)) {
			return id;
		}
		LOG.fine("Authorized session has expired, require reauthentication");
		id = null;
		final var challenge = new IuAuthenticationException( //
				null, new IllegalStateException("Authorization failed"));
		challenge.setLocation(entryPoint);
		throw challenge;

	}

	@Override
	public IuSamlPrincipal authorize(String remoteAddr, URI postUri, String samlResponse, String relayState)
			throws IuAuthenticationException {
		SamlProvider provider = SamlConnectSpi.getProvider(serviceProviderIdentityId);
		IuObject.require(id, Objects::isNull);
		var grant = grants.remove(relayState);
		if (grant != null) {
			id = provider.authorize(IuWebUtils.getInetAddress(remoteAddr), postUri, samlResponse, grant.getSession());
		} else {
			LOG.fine("Invalid relay state " + relayState);
			final var challenge = new IuAuthenticationException( //
					null, new IllegalArgumentException("Invalid relay state"));
			challenge.setLocation(entryPoint);
			throw challenge;

		}

		if (id != null) {
			return id;
		}

		final var challenge = new IuAuthenticationException( //
				null, new IllegalStateException("Authorization failed"));
		challenge.setLocation(entryPoint);
		throw challenge;

	}

}
