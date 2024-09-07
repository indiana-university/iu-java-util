package iu.auth.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;

import edu.iu.auth.config.IuOpenIdProviderMetadata;

/**
 * Provides minimal {@link IuOpenIdProviderMetadata} for an OAuth server that
 * issues JWT access and OPTIONAL refresh tokens with a well-known keyset, but
 * doesn't support the full OpenID Connect Core self-issued provider.
 */
public final class OAuthProviderMetadata implements InvocationHandler {

	private final URI issuer;
	private final URI tokenEndpoint;
	private final URI jwksUri;

	private OAuthProviderMetadata(URI issuer, URI tokenEndpoint, URI jwksUri) {
		this.issuer = issuer;
		this.tokenEndpoint = tokenEndpoint;
		this.jwksUri = jwksUri;
	}

	/**
	 * Gets {@link IuOpenIdProviderMetadata} from minimum attributes required to
	 * handle access and refresh tokens.
	 * 
	 * @param issuer        Token issuer identifying URI
	 * @param tokenEndpoint Token endpoint URI
	 * @param jwksUri       Well-known JWKS keyset URI
	 * @return {@link IuOpenIdProviderMetadata}
	 */
	public static IuOpenIdProviderMetadata getInstance(URI issuer, URI tokenEndpoint, URI jwksUri) {
		return (IuOpenIdProviderMetadata) Proxy.newProxyInstance(IuOpenIdProviderMetadata.class.getClassLoader(),
				new Class<?>[] { IuOpenIdProviderMetadata.class },
				new OAuthProviderMetadata(issuer, tokenEndpoint, jwksUri));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		switch (method.getName()) {
		case "getIssuer":
			return issuer;
		case "getTokenEndpoint":
			return tokenEndpoint;
		case "getJwksUri":
			return jwksUri;

		case "equals":
			return proxy == args[0];
		case "hashCode":
			return System.identityHashCode(proxy);
		case "toString":
			return this.toString();

		default:
			return method.getReturnType() == Boolean.TYPE ? false : null;
		}
	}

	@Override
	public String toString() {
		return "OAuthProviderMetadata [issuer=" + issuer + ", tokenEndpoint=" + tokenEndpoint + ", jwksUri=" + jwksUri
				+ "]";
	}

}
