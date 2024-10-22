package iu.auth.config;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.client.IuHttp;

@SuppressWarnings("javadoc")
public class AuthConfigTestCase {

	static {
		final var props = System.getProperties();
		synchronized (props) {
			final var restore = (String) props.setProperty("iu.http.allowedUri", IdGenerator.generateId());
			IuException.unchecked(() -> Class.forName(IuHttp.class.getName()));
			if (restore == null)
				props.remove("iu.http.allowedUri");
			else
				props.setProperty("iu.http.allowedUri", restore);
		}
	}

}
