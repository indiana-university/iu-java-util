package iu.auth.basic;

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

}
