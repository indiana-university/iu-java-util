package iu.auth.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuException;

public class OAuthResourceOwnerSession {
	private static final Logger LOG = Logger.getLogger(OAuthResourceOwnerSession.class.getName());
	// key is state
	private final Map<String, OAuthResourceGrant> grants = new ConcurrentHashMap<>();

	public void logout() {
		// remove session values
	}

	/**
	 * This method is initiate OIDC authentication workflow and forward user to IU
	 * Login Page When it get get valid auth token from OIDC, it get UserInfo from
	 * OIDC by generating token using OIDC token endpoin
	 * 
	 * @param applicationUrl
	 * @return OAuthResourceGrant
	 * @throws URISyntaxException
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public OAuthResourceGrant initiateLogin(String applicationUrl) {
		// TODO add logic to check token and principal in grant map
		if (grants != null) {
			for (OAuthResourceGrant grant : grants.values()) {
				// TODO we need to look root url from meta data and match against it instead of
				// matching against complete url
				if (grant.getApplicationUrl().toString().equals(applicationUrl)) {
					return grant;
				}
			}
		}
		var state = IdGenerator.generateId();
		var nonce = IdGenerator.generateId();
		OAuthResourceGrant grant = new OAuthResourceGrant();
		try {
			grant.setApplicationUrl(new URI(applicationUrl));
			grant.setState(state);
			grant.setNonce(nonce);
			grants.put(state, grant);

		} catch (URISyntaxException e) {
			IuException.unchecked(e);
		}

		return grant;
	}

	public Map<String, OAuthResourceGrant> getGrants() {
		return grants;
	}

}
