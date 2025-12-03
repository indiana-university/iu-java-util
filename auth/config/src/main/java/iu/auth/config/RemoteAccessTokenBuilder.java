package iu.auth.config;

import java.lang.reflect.Type;

import edu.iu.IuObject;
import edu.iu.auth.oauth.IuAuthorizationDetails;
import edu.iu.auth.oauth.IuCallerAttributes;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import iu.crypt.JwtBuilder;
import jakarta.json.JsonArrayBuilder;

/**
 * Builds {@link RemoteAccessToken} instances.
 * 
 * @param <B> builder type
 */
public class RemoteAccessTokenBuilder<B extends RemoteAccessTokenBuilder<B>> extends JwtBuilder<B> {

	private JsonArrayBuilder authorizationDetails = IuJson.array();

	/**
	 * Default constructor.
	 */
	protected RemoteAccessTokenBuilder() {
	}

	/**
	 * Adapts types related to the authorization_details claim.
	 * 
	 * @param <T>  adapted type
	 * @param type details interface
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	static protected <T> IuJsonAdapter<T> adaptAuthorizationDetails(Type type) {
		if (type instanceof Class) {
			final var c = (Class<?>) type;
			if (!IuObject.isPlatformName(c.getName()) //
					&& c.isInterface())
				return (IuJsonAdapter<T>) IuJsonAdapter.from(c, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES,
						RemoteAccessTokenBuilder::adaptAuthorizationDetails);
		}

		return IuJsonAdapter.of(type);
	}

	/**
	 * Sets the scope granted with this token.
	 * 
	 * @param scope scope
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	public B scope(String scope) {
		param("scope", scope);
		return (B) this;
	}

	/**
	 * Provides authorization details.
	 * 
	 * @param <T>                  details type
	 * @param type                 details interface class
	 * @param authorizationDetails authorization details
	 * @return this
	 */
	@SuppressWarnings("unchecked")
	protected <T extends IuAuthorizationDetails> B authorizationDetails(Class<T> type, T authorizationDetails) {
		this.authorizationDetails.add(adaptAuthorizationDetails(type).toJson(authorizationDetails));
		return (B) this;
	}

	/**
	 * Adds caller attributes as authorization details.
	 * 
	 * @param callerAttributes {@link IuCallerAttributes}
	 * @return this
	 */
	public B caller(IuCallerAttributes callerAttributes) {
		return authorizationDetails(IuCallerAttributes.class, callerAttributes);
	}

	@Override
	protected void prepare() {
		super.prepare();
		final var authorizationDetails = this.authorizationDetails.build();
		if (!authorizationDetails.isEmpty())
			param("authorization_details", authorizationDetails);
	}

	@Override
	public RemoteAccessToken build() {
		prepare();
		return new RemoteAccessToken(toJson());
	}

}
