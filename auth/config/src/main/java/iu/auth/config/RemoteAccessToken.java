package iu.auth.config;

import java.net.URI;

import edu.iu.auth.oauth.IuAuthorizationDetails;
import edu.iu.auth.oauth.IuCallerAttributes;
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
	public RemoteAccessToken(JsonObject claims) {
		super(claims);
	}

	/**
	 * Gets a builder.
	 * 
	 * @return {@link RemoteAccessTokenBuilder}
	 */
	public static RemoteAccessTokenBuilder<?> builder() {
		return new RemoteAccessTokenBuilder<>();
	}

	/**
	 * Gets the called URL.
	 *
	 * @param <T>             details interface type
	 * @param type            authorization details type
	 * @param detailInterface authorization details interface
	 * @return authorization details
	 */
	protected <T extends IuAuthorizationDetails> T getAuthorizationDetails(String type, Class<T> detailInterface) {
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
	public IuCallerAttributes getCallerAttributes() {
		return getAuthorizationDetails(IuCallerAttributes.TYPE, IuCallerAttributes.class);
	}

}
