package iu.esas.thirdparty.auth.oidc.client;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.time.Duration;

import edu.iu.IuException;
import edu.iu.client.RemoteInvocationHandler;
import edu.iu.thirdparty.auth.oidc.api.ThirdPartyAccess;
import iu.auth.config.AuthConfig;
import iu.auth.config.SelfIssuedAccessToken;
import jakarta.annotation.Resource;

/**
 * Handles remote calls to the downstream EJB services.
 */
@Resource(type = ThirdPartyAccess.class)
public class RemoteEjbHandler extends RemoteInvocationHandler {

	@Resource
	private String application;
	@Resource
	private URI resourceUri;
	@Resource
	private URI remoteEjbUri;
	@Resource
	private Duration tokenTtl = Duration.ofSeconds(15L);

	/**
	 * Default constructor
	 */
	RemoteEjbHandler() {
	}

	@Override
	protected void authorize(Builder requestBuilder) {
		final var issuer = AuthConfig.load(ThirdPartyIssuer.class, application);
		final var token = new SelfIssuedAccessToken(issuer.getKeys().iterator().next(), resourceUri, remoteEjbUri,
				tokenTtl, ThirdPartyAuth.getCaller());
		IuException.unchecked(() -> token.applyTo(requestBuilder));
	}

	@Override
	protected URI uri(Method method) {
		return URI.create(remoteEjbUri + "/" + method.getDeclaringClass().getSimpleName() + "/" + method.getName());
	}

}
