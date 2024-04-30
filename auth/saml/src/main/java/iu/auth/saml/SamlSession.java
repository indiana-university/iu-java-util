package iu.auth.saml;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.URI;
import java.security.Principal;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlSession;
import iu.auth.util.HttpUtils;

/**
 * SAML session implementation
 */
public class SamlSession implements IuSamlSession, Serializable {

	private static final long serialVersionUID = 1L;
	private final Logger LOG = Logger.getLogger(SamlSession.class.getName());
	//TODO this map will handle partial authorization logic
	// key is state
	private final Map<String, RelayState> grants = new HashMap<>();

	/**
	 * TODO
	 */
	private final String serviceProviderIdentityId;

	/**
	 * TODO
	 */
	private final URI entryPoint;

	private Id id;

	// TODO implement IuPrincipalId instead of principal
	private static class Id implements Principal, Serializable {
		private static final long serialVersionUID = 1L;

		private final String name;
		// private String activationCode = IdGenerator.generateId();

		private Id(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public int hashCode() {
			return IuObject.hashCode(name);
		}

		@Override
		public boolean equals(Object obj) {
			if (!IuObject.typeCheck(this, obj))
				return false;
			Id other = (Id) obj;
			return IuObject.equals(name, other.name);
		}

		@Override
		public String toString() {
			return "SAML Principal ID [name=" + name + "]";
		}
	}

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
	public URI authRequest(URI samlEntityId, URI postURI, URI resourceUri) {
		SamlProvider provider = SamlConnectSpi.getProvider(serviceProviderIdentityId);
		IuSamlClient client = provider.getClient();
		
		IuWebUtils.isRootOf(client.getApplicationUri(), resourceUri);
		
		var destinationLocation = provider.getSingleSignOnLocation(samlEntityId.toString());


		RelayState relayState = new RelayState(client.getApplicationUri());
		String state = IdGenerator.generateId();
		grants.put(state, relayState);

		ByteArrayOutputStream samlRequestBuffer = provider.authRequest(samlEntityId, postURI, relayState.getSession(),
				destinationLocation);

		Map<String, Iterable<String>> idpParams = new LinkedHashMap<>();
		idpParams.put("SAMLRequest",
				Collections.singleton(Base64.getEncoder().encodeToString(samlRequestBuffer.toByteArray())));


		idpParams.put("RelayState", Collections.singleton(state));

		URI redirectUri = IuException
				.unchecked(() -> new URI(destinationLocation + '?' + IuWebUtils.createQueryString(idpParams)));


		/*
		 * final Map<String, String> challengeAttributes = new LinkedHashMap<>();
		 * challengeAttributes.put("relaySatate", relayState);
		 * 
		 * final var challenge = new IuAuthenticationException( //
		 * HttpUtils.createChallenge("Bearer", challengeAttributes), refreshFailure);
		 * challenge.setLocation(IuException.unchecked(() -> new URI(
		 * client.getApplicationUri() + "?" +
		 * IuWebUtils.createQueryString(authRequestParams)))); throw challenge;
		 */
		return redirectUri;

	}

	/*
	 * TODO need IuPrincipalIdentity to support this method
	 * 
	 */
	/*public //IuPrincipalIdentitty  getPrincipalIdentity() throws IuAuthenticationException {

		/*
	 * SamlProvider provider =
	 * SamlConnectSpi.getProvider(serviceProviderIdentityId);
	 * 
	 * if (!IuWebUtils.isRoot(provider.getClient().getApplicationUri(),
	 * resourceUri)) { throw new
	 * IllegalArgumentException("Invalid resource URI for this grant"); }
	 */

	//if (id != null) {
	//	return id;
	//}

	//throw new IuAuthenticationException(ch);
	//}

	@Override
	public void authorize(InetAddress address, String acsUrl, String samlResponse, String relayState) throws IuAuthenticationException {
		SamlProvider provider = SamlConnectSpi.getProvider(serviceProviderIdentityId);

		IuSamlClient client = provider.getClient();

		var grant = grants.get(relayState);
		if (grant != null && id == null ) {
			SamlAttributes attributes = provider.authorize(address, acsUrl, samlResponse, grant.getSession());
			id = new Id(attributes.getEduPersonPrincipalName());

		}
		else {

			final Map<String, String> challengeAttributes = new LinkedHashMap<>();
			challengeAttributes.put("relaySatate", relayState);
			challengeAttributes.put("error_description", "invalid relay state");
			final var challenge = new IuAuthenticationException( //
					HttpUtils.createChallenge("saml", challengeAttributes), null);
			challenge.setLocation(client.getApplicationUri()); 

			throw challenge;

		}

	}


}
