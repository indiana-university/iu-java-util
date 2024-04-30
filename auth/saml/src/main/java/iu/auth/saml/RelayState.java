package iu.auth.saml;

import java.net.URI;

import edu.iu.IdGenerator;

public class RelayState {
	private final String session;
	private final URI applicationUri;

	public RelayState(URI applicationUri) {
		this.session = IdGenerator.generateId();
		this.applicationUri = applicationUri;
	}

	public String getSession() {
		return session;
	}

}
