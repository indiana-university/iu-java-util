package iu.auth.config;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuHttpAware implements BeforeAllCallback {

	public static final String HOST = IdGenerator.generateId();

	@Override
	public void beforeAll(@SuppressWarnings("exports") ExtensionContext context) throws Exception {
		System.setProperty("iu.http.allowedUri", "http://" + HOST);
	}

}
