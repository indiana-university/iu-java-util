package iu.auth.oidc;

import java.net.URI;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuOidcTestCase {

	protected static final URI ROOT_URI = URI.create("test://" + IdGenerator.generateId());

	static {
		System.setProperty("iu.http.allowedUri", ROOT_URI.toString());
	}

}
