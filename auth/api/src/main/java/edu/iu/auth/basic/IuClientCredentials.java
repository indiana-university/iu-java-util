package edu.iu.auth.basic;

import java.time.Instant;
import java.time.temporal.TemporalAmount;

import edu.iu.IdGenerator;
import edu.iu.auth.spi.IuBasicAuthSpi;
import iu.auth.IuAuthSpiFactory;

/**
 * Provides externally defined client credentials suitable for authenticating
 * OAuth 2 clients.
 */
public interface IuClientCredentials {

	/**
	 * Registers Basic authentication principals for verifying external OAuth 2
	 * client credentials.
	 * 
	 * <p>
	 * Client ID values provided via {@link #getId()} <em>must</em> be printable
	 * ASCII with no whitespace, and start with a letter.
	 * </p>
	 * 
	 * <p>
	 * Client secret values provided via {@link #getSecret()} <em>must</em> be
	 * printable ASCII, at least 12 characters in length. Implementations
	 * <em>should</em> use {@link IdGenerator#generateId()} to create passwords.
	 * </p>
	 * 
	 * <p>
	 * {@link IuClientCredentials#getNotBefore()} and {@link #getExpires()}
	 * <em>must</em> be non-null for all entries. Entries <em>may</em> be expired;
	 * expired entries <em>may</em> be changed. <em>May</em> include multiple
	 * entries with the same name but different passwords and expiration times.
	 * </p>
	 * 
	 * <p>
	 * This method <em>may</em> be called no more than once per realm.
	 * </p>
	 * 
	 * <p>
	 * <em>Implementation Note:</em> The {@link Iterable} provided to this method is
	 * controlled externally. {@link Iterable#iterator()} is invoked each time an
	 * {@link IuClientCredentials} principal is verified to discover externally
	 * controlled metadata. Implementors <em>should</em> avoid passing
	 * invalid/expires credentials.
	 * </p>
	 * 
	 * @param clientCredentials Basic authentication client credential principals
	 * @param realm             Authentication realm
	 * @param expirationPolicy  Maximum length of time to allow passwords to remain
	 *                          valid
	 * @see <a href=
	 *      "https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1">OAuth 2.0
	 *      Client Password</a>
	 * @see <a href=
	 *      "https://github.com/OWASP/ASVS/raw/v4.0.3/4.0/OWASP%20Application%20Security%20Verification%20Standard%204.0.3-en.pdf">ASVS
	 *      4.0: 2.1 and 2.4</a>
	 */
	static void register(Iterable<? extends IuClientCredentials> clientCredentials, String realm,
			TemporalAmount expirationPolicy) {
		IuAuthSpiFactory.get(IuBasicAuthSpi.class).register(clientCredentials, realm, expirationPolicy);
	}

	/**
	 * Gets the client id.
	 * 
	 * @return client id
	 */
	String getId();

	/**
	 * Gets the client secret.
	 * 
	 * @return client secret
	 */
	String getSecret();

	/**
	 * Time before which the password <em>should</em> be considered invalid.
	 * 
	 * @return expiration time
	 */
	Instant getNotBefore();

	/**
	 * Time after which the password <em>should</em> be considered invalid.
	 * 
	 * @return expiration time
	 */
	Instant getExpires();

}
