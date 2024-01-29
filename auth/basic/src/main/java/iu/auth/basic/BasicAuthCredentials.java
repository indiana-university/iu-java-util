package iu.auth.basic;

import java.net.http.HttpRequest.Builder;
import java.util.Base64;

import edu.iu.IuException;
import edu.iu.auth.basic.IuBasicAuthCredentials;

/**
 * Implementation of {@link IuBasicAuthCredentials}.
 */
public class BasicAuthCredentials implements IuBasicAuthCredentials {

	private final String name;
	private final String password;

	/**
	 * Constructor.
	 * 
	 * @param name     username
	 * @param password password
	 */
	public BasicAuthCredentials(String name, String password) {
		this.name = name;
		this.password = password;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public void applyTo(Builder httpRequestBuilder) {
		httpRequestBuilder.header("Authorization", "Basic " + Base64.getUrlEncoder()
				.encodeToString(IuException.unchecked(() -> (name + ':' + password).getBytes("UTF-8"))));
	}

}
