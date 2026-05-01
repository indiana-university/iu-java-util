package iu.oidc.client;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.function.Function;

import edu.iu.client.IuJsonAdapter;
import edu.iu.jwt.WebToken;
import edu.iu.oidc.IuOidcPrincipal;
import jakarta.json.JsonObject;

/**
 * {@link IuOidcPrincipal} implementation class.
 */
public class OidcPrincipal implements IuOidcPrincipal {

	private final WebToken idToken;
	private final JsonObject userinfoClaims;
	private final String setCookie;
	private final Function<URI, String> accessTokenLookup;
	private final Function<Type, IuJsonAdapter<?>> adapterFactory;

	/**
	 * Constructor.
	 * 
	 * @param idToken           ID token
	 * @param userinfoClaims    Claims provided by the userinfo endpoint
	 * @param setCookie         set-cookie header value to pass back to the user
	 *                          agent if session state changed assembling the
	 *                          principal
	 * @param accessTokenLookup finds access tokens by URI
	 * @param adapterFactory    JSON type adapter factory
	 */
	public OidcPrincipal(WebToken idToken, JsonObject userinfoClaims, String setCookie,
			Function<URI, String> accessTokenLookup, Function<Type, IuJsonAdapter<?>> adapterFactory) {
		this.idToken = idToken;

		if (!userinfoClaims.containsKey("sub"))
			throw new IllegalArgumentException("userinfo missing sub claim");
		if (!userinfoClaims.getString("sub").equals(idToken.getSubject()))
			throw new IllegalArgumentException("userinfo sub claim doesn't match id token");
		this.userinfoClaims = userinfoClaims;

		this.setCookie = setCookie;

		this.accessTokenLookup = accessTokenLookup;
		this.adapterFactory = adapterFactory;
	}

	@Override
	public String getName() {
		return idToken.getSubject();
	}

	@Override
	public String getSetCookie() {
		return setCookie;
	}

	@Override
	public WebToken getIdToken() {
		return idToken;
	}

	@Override
	public <T> T getClaim(String name, Class<T> type) {
		final var userinfoClaimValue = userinfoClaims.get(name);
		if (userinfoClaimValue == null)
			return idToken.getClaim(name, type);
		else
			return type.cast(adapterFactory.apply(type).fromJson(userinfoClaimValue));
	}

	@Override
	public String getAccessToken(URI resourceUri) {
		return accessTokenLookup.apply(resourceUri);
	}

}
