package iu.esas.thirdparty.auth.oidc.client;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.client.RemoteInvocationHandler;
import edu.iu.oidc.IuOidcClientEndpoint;
import edu.iu.thirdparty.auth.oidc.ThirdPartyAuthorizationDetailsResource;
import iu.auth.config.AuthConfig;
import iu.auth.config.SelfIssuedAccessToken;
import jakarta.annotation.Resource;

/**
 * {@link ThirdPartyAuthorizationDetailsResource} remote call handler.
 */
@Resource(type = ThirdPartyAuthorizationDetailsResource.class)
class ThirdPartyAuthorizationDetailsResourceHandler extends RemoteInvocationHandler {

	@Resource
	private String application;
	@Resource
	private URI resourceUri;
	@Resource
	private Duration tokenTtl = Duration.ofSeconds(15L);

	/**
	 * Default constructor
	 */
	ThirdPartyAuthorizationDetailsResourceHandler() {
	}

	@Override
	protected URI uri(Method method) {
		return IuObject.convert(ThirdPartyAuth.getClientEndpoint(),
				IuOidcClientEndpoint::getAuthorizationDetailsUri);
	}

	@Override
	protected void authorize(Builder requestBuilder) {
		final var clientEndpoint = ThirdPartyAuth.getClientEndpoint();
		final var issuer = AuthConfig.load(ThirdPartyIssuer.class, application);
		final var issuerKey = IuIterable.select(issuer.getKeys(),
				jwk -> clientEndpoint.getAlg().equals(jwk.getAlgorithm()));
		final var token = new SelfIssuedAccessToken(issuerKey, resourceUri, clientEndpoint.getAuthorizationDetailsResourceUri(),
				tokenTtl, ThirdPartyAuth.getCaller());
		IuException.unchecked(() -> token.applyTo(requestBuilder));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		{
			Object rv = invokeObjectMethod(proxy, method, args);
			if (rv != null)
				return rv;
		}

		if (uri(method) == null)
			return null;
		else
			return super.invoke(proxy, method, args);
	}

}