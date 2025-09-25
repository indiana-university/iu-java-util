package iu.auth.config;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import edu.iu.IuWebUtils;
import edu.iu.auth.oauth.OAuthAuthorizationClient;
import edu.iu.crypt.WebToken;

/**
 * Authenticates to an OAuth 2.0 Token endpoint, verifies and holds a JWT access
 * token until expired.
 */
public class OidcAuthorizationGrant extends OAuthAccessTokenGrant {

	/**
	 * Constructor.
	 * 
	 * @param credentialSupplier Supplies client credentials
	 */
	public OidcAuthorizationGrant(Supplier<OAuthAuthorizationClient> credentialSupplier) {
		super(credentialSupplier);
	}

	@Override
	protected void verifyToken(WebToken jwt) {
		// validity of non-null nbf and exp are handled by super -> WebToken.verify
		Objects.requireNonNull(jwt.getNotBefore(), "nbf");
		Objects.requireNonNull(jwt.getExpires(), "exp");
	}

	@Override
	protected OAuthAuthorizationClient getClient() {
		return (OAuthAuthorizationClient) super.getClient();
	}

	@Override
	protected void tokenAuth(HttpRequest.Builder requestBuilder) {
		final var client = getClient();
		requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
//		requestBuilder.header("Authorization", "Basic " + IuText
//				.base64(IuText.utf8(client.getClientId() + ":" + client.getClientSecret())));
		requestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(Map.of( //
				"grant_type", List.of("authorization_code"), //
				"redirect_uri", List.of(client.getRedirectUri().toString())
		// , //
//				"code", List.of(code) //
		))));
	}

}
