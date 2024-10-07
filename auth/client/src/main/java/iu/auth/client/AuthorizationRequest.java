package iu.auth.client;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuWebUtils;
import edu.iu.auth.client.IuAuthorizationRequest;
import iu.auth.config.AuthConfig;
import iu.auth.config.IuClientResource;

/**
 * {@IuAuthorizationRequest} hash key implementation.
 */
public class AuthorizationRequest implements IuAuthorizationRequest {
	static {
		IuObject.assertNotOpen(AuthorizationRequest.class);
	}

	private final URI endpointUri;
	private final String[] scope;

	/**
	 * Constructor.
	 * 
	 * @param resourceUri {@link URI}
	 * @param scope       scope
	 */
	public AuthorizationRequest(URI resourceUri, String... scope) {
		Objects.requireNonNull(resourceUri, "Missing resourceUri");

		URI endpointUri = null;
		for (final var client : AuthConfig.get(IuClientResource.class))
			if (IuWebUtils.isRootOf(client.getEndpointUri(), resourceUri)) {
				endpointUri = client.getEndpointUri();
				break;
			}
		this.endpointUri = Objects.requireNonNull(endpointUri, "No client resource registered for resource URI");
		this.scope = scope == null || scope.length == 0 ? null : scope;
	}

	/**
	 * Creates an {@link AuthorizationRequest} from an
	 * {@link IuAuthorizationRequest}.
	 * 
	 * @param request {@link IuAuthorizationRequest}
	 * @return {@link AuthorizationRequest}
	 */
	public static AuthorizationRequest from(IuAuthorizationRequest request) {
		if (request instanceof AuthorizationRequest)
			return (AuthorizationRequest) request;

		return new AuthorizationRequest(request.getResourceUri(),
				IuObject.convert(request.getScope(), a -> IuIterable.stream(a).toArray(String[]::new)));
	}

	@Override
	public URI getResourceUri() {
		return endpointUri;
	}

	@Override
	public Iterable<String> getScope() {
		return scope == null ? null : IuIterable.iter(scope);
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(endpointUri, scope);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;

		AuthorizationRequest other = (AuthorizationRequest) obj;
		return IuObject.equals(endpointUri, other.endpointUri) //
				&& IuObject.equals(scope, other.scope);
	}

	@Override
	public String toString() {
		return "AuthorizationRequest [endpointUri=" + endpointUri + ", scope=" + Arrays.toString(scope) + "]";
	}

}
