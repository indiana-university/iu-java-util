package iu.auth.util;

import java.net.URI;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuAuthUtilTestCase {

	protected static final URI ROOT_URI = URI.create("test://" + IdGenerator.generateId());

	static {
		System.setProperty("iu.http.allowedUri", ROOT_URI.toString());
	}

}
