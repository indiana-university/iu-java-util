package iu.auth.saml;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlPrincipal;
import edu.iu.auth.saml.IuSamlSession;

/**
 * SAML session implementation to support session management
 */
public class SamlSession implements IuSamlSession, Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * TODO
	 */
	private final Map<String, RelayState> grants = new HashMap<>();

	/**
	 * TODO
	 */
	private final String serviceProviderIdentityId;

	/**
	 * TODO
	 */
	private final URI entryPoint;

	/**
	 * TODO
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
	public URI getAuthenticationRequest(URI samlEntityId, URI postUri, URI resourceUri) {
		SamlProvider provider = SamlConnectSpi.getProvider(serviceProviderIdentityId);
		IuSamlClient client = provider.getClient();

		IuWebUtils.isRootOf(client.getApplicationUri(), resourceUri);

		var destinationLocation = provider.getSingleSignOnLocation(samlEntityId.toString());

		RelayState relayState = new RelayState(client.getApplicationUri());
		String state = IdGenerator.generateId();
		grants.put(state, relayState);

		ByteArrayOutputStream samlRequestBuffer = provider.authRequest(samlEntityId, postUri, relayState.getSession(),
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
		LocalDateTime currentTime = LocalDateTime.now();
		LocalDateTime totalSessiontime = currentTime.minus(duration);
		if(currentTime.isAfter(totalSessiontime))
        

        if (currentTime.isAfter(totalSessiontime)) {
        	final Map<String, String> challengeAttributes = new LinkedHashMap<>();
			challengeAttributes.put("error_description", "Authorized session has expired");
			final var challenge = new IuAuthenticationException( //
					IuWebUtils.createChallenge("saml", challengeAttributes), null);
			challenge.setLocation(client.getApplicationUri());
			throw challenge;
        }
		
		return id;
		
	}

	@Override
	public IuSamlPrincipal authorize(InetAddress address, URI postUri, String samlResponse, String relayState)
			throws IuAuthenticationException {
		SamlProvider provider = SamlConnectSpi.getProvider(serviceProviderIdentityId);

		IuSamlClient client = provider.getClient();

		var grant = grants.get(relayState);
		if (grant != null && id == null) {
			id = provider.authorize(address, postUri, samlResponse, grant.getSession());
		} else {
		final Map<String, String> challengeAttributes = new LinkedHashMap<>();
			challengeAttributes.put("relaySatate", relayState);
			challengeAttributes.put("error_description", "invalid relay state");
			final var challenge = new IuAuthenticationException( //
					IuWebUtils.createChallenge("saml", challengeAttributes), null);
			challenge.setLocation(client.getApplicationUri());
			throw challenge;

		}
		
		if (id != null ) {
			grants.remove(relayState);
			return id;
		}
		
		final Map<String, String> challengeAttributes = new LinkedHashMap<>();
		challengeAttributes.put("error_description", "authorization failed");
		final var challenge = new IuAuthenticationException( //
				IuWebUtils.createChallenge("saml", challengeAttributes), null);
		challenge.setLocation(client.getApplicationUri());
		throw challenge;
	
	}

}
