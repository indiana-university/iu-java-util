package iu.auth.config;

import java.net.URI;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import jakarta.json.JsonValue;

/**
 * Encapsulates the response from an OAuth Token Server.
 * 
 * @see <a href=
 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-5.1">RFC-6749
 *      OAuth 2.0 Section 5.1</a>
 */
public interface OAuthTokenResponse {

	/**
	 * Derives {@link OAuthTokenResponse} from a parsed JSON value.
	 * 
	 * @param json parsed JSON value
	 * @return {@link OAuthTokenResponse}
	 */
	static OAuthTokenResponse from(JsonValue json) {
		return IuJsonAdapter
				.from(OAuthTokenResponse.class, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, IuJsonAdapter::of)
				.fromJson(json);
	}

	/**
	 * Gets the token type.
	 * 
	 * @return token type; i.e. "bearer"
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-7.1">RFC-6749
	 *      OAuth 2.0 Section 7.1</a>
	 */
	String getTokenType();

	/**
	 * Gets the access token.
	 * 
	 * @return access token
	 */
	String getAccessToken();

	/**
	 * Gets the OIDC ID token.
	 * 
	 * @return OIDC ID token
	 */
	String getIdToken();

	/**
	 * Gets the refresh token.
	 * 
	 * @return refresh token
	 */
	String getRefreshToken();

	/**
	 * Gets the number of seconds until the token expires.
	 * 
	 * @return number of seconds until the token expires
	 */
	int getExpiresIn();

	/**
	 * Gets the error; SHOULD be non-null if {@link #getAccessToken()} is null.
	 * 
	 * @return error code
	 */
	String getError();

	/**
	 * Gets the OPTIONAL error description.
	 * 
	 * @return error description
	 */
	String getErrorDescription();

	/**
	 * Gets the OPTIONAL error URI.
	 * 
	 * @return error URI
	 */
	URI getErrorUri();

}
