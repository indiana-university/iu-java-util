package iu.auth.client;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.client.IuHttp;

@SuppressWarnings("javadoc")
public class IuAuthClientTestCase {

	static {
		final var restore = System.getProperty("iu.http.allowedUri");
		try {
			System.setProperty("iu.http.allowedUri", IdGenerator.generateId());
			IuException.unchecked(() -> Class.forName(IuHttp.class.getName()));
		} finally {
			if (restore == null)
				System.getProperties().remove("iu.http.allowedUri");
			else
				System.setProperty("iu.http.allowedUri", restore);
		}
	}

}
