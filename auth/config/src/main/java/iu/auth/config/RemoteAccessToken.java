package iu.auth.config;

import java.net.URI;

import edu.iu.auth.config.AuthorizationDetails;
import edu.iu.auth.config.CallerAttributes;
import edu.iu.client.IuJson;
import iu.crypt.Jwt;
import jakarta.json.JsonObject;

/**
 * Exposes claims specific to authorizing EJB invocation.
 */
public class RemoteAccessToken extends Jwt {

	/**
	 * Constructor.
	 * 
	 * @param claims Parsed JSON claims
	 */
	protected RemoteAccessToken(JsonObject claims) {
		super(claims);
	}

	/**
	 * Gets the called URL.
	 *
	 * @param type            authorization details type
	 * @param detailInterface authorization details interface
	 * @return authorization details
	 */
	protected <T extends AuthorizationDetails> T getAuthorizationDetails(String type, Class<T> detailInterface) {
		return detailInterface.cast(RemoteAccessTokenBuilder.adaptAuthorizationDetails(detailInterface)
				.fromJson(claims.getJsonArray("authorization_details").stream()
						.filter(a -> type.equals(IuJson.get(a.asJsonObject(), "type"))).findFirst().get()));
	}

	/**
	 * Gets the authorized scope.
	 * 
	 * @return scope
	 */
	public String getScope() {
		return IuJson.get(claims, "scope");
	}

	/**
	 * Gets the called URL.
	 * 
	 * @return {@link URI}
	 */
	public CallerAttributes getCallerAttributes() {
		return getAuthorizationDetails(CallerAttributes.TYPE, CallerAttributes.class);
	}

}
