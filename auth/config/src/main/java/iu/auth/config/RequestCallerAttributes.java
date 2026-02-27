package iu.auth.config;

import java.net.URI;

import edu.iu.auth.IuRequestAttributes;
import edu.iu.auth.oauth.IuCallerAttributes;

/**
 * {@link IuCallerAttributes} implementation backed by {@link IuRequestAttributes}.
 */
public class RequestCallerAttributes implements IuCallerAttributes {

	private final IuRequestAttributes requestAttributes;
	private final String authnPrincipal;
	private final String impersonatedPrincipal;

	/**
	 * Constructor.
	 * 
	 * @param requestAttributes     {@link IuRequestAttributes}
	 * @param authnPrincipal        authenticated principal name
	 * @param impersonatedPrincipal impersonated principal name
	 */
	public RequestCallerAttributes(IuRequestAttributes requestAttributes, String authnPrincipal,
			String impersonatedPrincipal) {
		this.requestAttributes = requestAttributes;
		this.authnPrincipal = authnPrincipal;
		this.impersonatedPrincipal = impersonatedPrincipal;
	}

	@Override
	public URI getRequestUri() {
		return requestAttributes.getRequestUri();
	}

	@Override
	public String getRemoteAddr() {
		return requestAttributes.getRemoteAddr();
	}

	@Override
	public String getUserAgent() {
		return requestAttributes.getUserAgent();
	}

	@Override
	public String getAuthnPrincipal() {
		return authnPrincipal;
	}

	@Override
	public String getImpersonatedPrincipal() {
		return impersonatedPrincipal;
	}

}
