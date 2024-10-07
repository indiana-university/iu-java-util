package iu.auth.client;

import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuText;
import edu.iu.IuWebUtils;
import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuBearerToken;
import edu.iu.auth.config.GrantType;
import edu.iu.auth.config.IuAuthorizationResource;
import edu.iu.client.HttpException;
import edu.iu.client.IuHttp;
import edu.iu.crypt.WebToken;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuClientResource;

/**
 * {@link IuAuthorizationGrant} implementation for client credentials flow.
 */
class ClientCredentialsGrant implements IuAuthorizationGrant {

	private final IuClientResource client;

	private String accessToken;
	private Instant expiresAt;
	private String[] scope;

	/**
	 * Constructor.
	 * 
	 * @param request {@link AuthorizationRequest}
	 */
	ClientCredentialsGrant(AuthorizationRequest request) {
		this.client = getResource(request.getResourceUri());
	}

	/**
	 * Returns resource authorization metadata for a given endpoint.
	 * 
	 * @param resourceUri resource {@link URI}
	 * @return {@link IuAuthorizationResource} associated with the first
	 *         {@link IuClientResource client resource} registered with an
	 *         {@link IuClientResource#getEndpointUri() endpoint URI} that
	 *         {@code resourceUri} is relative to.
	 */
	static IuClientResource getResource(URI resourceUri) {
		for (final var clientResource : AuthConfig.get(IuClientResource.class))
			if (IuWebUtils.isRootOf(clientResource.getEndpointUri(), resourceUri))
				return clientResource;
		throw new IllegalArgumentException("No resource found for endpoint: " + resourceUri);
	}

	@Override
	public IuBearerToken authorize() {
		if (accessToken != null && expiresAt.isAfter(Instant.now().plusSeconds(2L)))
			return new BearerToken(accessToken);

		final var resourceName = client.getResourceName();
		final var resource = AuthConfig.load(IuAuthorizationResource.class, resourceName);
		final var providerMetadata = resource.getProviderMetadata();
		final var credentials = Objects.requireNonNull(resource.getCredentials(), "Missing credentials");
		if (!credentials.getGrantTypes().contains(GrantType.CLIENT_CREDENTIALS))
			throw new IllegalArgumentException("Client credentials grant not allowed for resource " + resourceName);

		final Map<String, Iterable<String>> tokenRequestParams = new LinkedHashMap<>();
		tokenRequestParams.put("grant_type", IuIterable.iter("client_credentials"));

		final URI realm;
		final String nonce;
		if (resource.isNonceChallenge()) {
			try {
				final var response = IuHttp.send(client.getEndpointUri(), rb -> rb.POST(BodyPublishers.noBody()));
				throw new IllegalStateException(
						"unexpected challenge response " + IuWebUtils.describeStatus(response.statusCode()));
			} catch (HttpException e) {
				final var response = e.getResponse();
				if (response.statusCode() != 401)
					throw new IllegalStateException(
							"unexpected challenge response " + IuWebUtils.describeStatus(response.statusCode()));

				final var challenge = IuWebUtils
						.parseAuthenticateHeader(response.headers().firstValue("WWW-Authenticate").get()).next();
				if (!challenge.getAuthScheme().equals("Bearer"))
					throw new IllegalStateException("unexpected challenge " + challenge);
				realm = URI.create(Objects.requireNonNull(challenge.getRealm(), "unexpected challenge " + challenge));
				nonce = Objects.requireNonNull(challenge.getParameters().get("nonce"),
						"unexpected challenge " + challenge);
			}
		}

		final String authorization;
		switch (credentials.getTokenEndpointAuthMethod()) {
		case CLIENT_SECRET_BASIC:
			authorization = IuText
					.base64(IuText.utf8(resource.getClientId() + ":" + IuText.base64(credentials.getJwk().getKey())));
			break;

		case CLIENT_SECRET_POST:
			tokenRequestParams.put("client_id", IuIterable.iter(resource.getClientId()));
			tokenRequestParams.put("client_secret", IuIterable.iter(IuText.base64(credentials.getJwk().getKey())));
			break;

		case CLIENT_SECRET_JWT:
		case PRIVATE_KEY_JWT:
			final var assertionBuilder = WebToken.builder() //
					.jti().iat() //
					.iss(URI.create(resource.getClientId())) //
					.aud(providerMetadata.getTokenEndpoint()) //
					.sub(resource.getClientId()) //
					.exp(Instant.now().plus(credentials.getAssertionTtl()));
			assertionBuilder.build() //
					.sign("JWT", credentials.getAlg(), credentials.getJwk());
			break;

			break;

		default:
			throw new IllegalArgumentException("Auth method " + credentials.getTokenEndpointAuthMethod()
					+ " not supported for client credentials");
		}

		final var scope = request.getScope();
		if (scope != null)
			tokenRequestParams.put("scope", IuIterable.iter(String.join(" ", scope)));

		final var clientAttributes = resource.getAdditionalParameters();
		if (clientAttributes != null)
			for (final var clientAttributeEntry : clientAttributes.entrySet()) {
				final var name = clientAttributeEntry.getKey();
				if (name.equals("grant_type") || name.equals("scope"))
					throw new IllegalArgumentException(
							"Illegal attempt to override standard client credentials attribute " + name);
				else
					tokenRequestParams.put(name, IuIterable.iter(clientAttributeEntry.getValue()));
			}

		return IuException.unchecked(() -> authorize(new TokenResponse(client.getScope(), clientAttributes,
				IuHttp.send(IuAuthenticationException.class, client.getTokenEndpoint(), tokenRequestBuilder -> {
					tokenRequestBuilder.POST(BodyPublishers.ofString(IuWebUtils.createQueryString(tokenRequestParams)));
					tokenRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
					client.getCredentials().applyTo(tokenRequestBuilder);
				}, JSON_OBJECT_NOCACHE))));
		// TODO Auto-generated method stub
		return null;
	}

}
