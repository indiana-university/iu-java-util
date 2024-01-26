package iu.auth.oauth;

import java.net.URI;
import java.security.Principal;

public class OAuthResourceGrant {

	private String state; // Generated unique Id
	private String nonce; // unique id to verify token
	private URI applicationUrl; // application Url
	private String idToken; // OIDC idtoken that is valid for atleast 15 mins
	private Principal principal;

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public URI getApplicationUrl() {
		return applicationUrl;
	}

	public void setApplicationUrl(URI applicationUrl) {
		this.applicationUrl = applicationUrl;
	}

	public String getIdToken() {
		return idToken;
	}

	public void setIdToken(String idToken) {
		this.idToken = idToken;
	}

	public Principal getPrincipal() {
		return principal;
	}

	public void setPrincipal(Principal principal) {
		this.principal = principal;
	}

	public String getNonce() {
		return nonce;
	}

	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

}
