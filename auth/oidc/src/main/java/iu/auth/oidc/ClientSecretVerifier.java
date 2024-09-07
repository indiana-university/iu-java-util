package iu.auth.oidc;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

import edu.iu.IuDigest;
import edu.iu.IuText;
import edu.iu.auth.config.IuAuthorizationClient;
import edu.iu.auth.config.IuAuthorizationClient.AuthMethod;
import edu.iu.auth.config.IuAuthorizationClient.Credentials;
import edu.iu.crypt.WebKey.Type;

/**
 * Verifies {@link IuAuthorizationCredentials} for authenticating an
 * {@link IuAuthorizationClient}.
 */
final class ClientSecretVerifier {

	private ClientSecretVerifier() {
	}

	/**
	 * Verifies client credentials.
	 * 
	 * <p>
	 * Tries each of {@link IuAuthorizationClient#getCredentials()}, returning the
	 * first that both matches requested authentication method and passes
	 * {@link #verify(String)} without error.
	 * </p>
	 * 
	 * @param client       {@link IuAuthorizationClient}
	 * @param authMethod   {@link AuthMethod}
	 * @param clientSecret externally supplied client secret
	 * @return First matching {@link IuAuthorizationCredentials} from
	 *         {@link IuAuthorizationClient#getCredentials()}
	 */
	static IuAuthorizationCredentials verify(IuAuthorizationClient client, AuthMethod authMethod, String clientSecret) {
		final Queue<Throwable> errors = new ArrayDeque<>();
		for (final var credentialsToTry : client.getCredentials())
			if (credentialsToTry.getTokenEndpointAuthMethod().equals(authMethod))
				try {
					verify(credentialsToTry, clientSecret);
					return credentialsToTry;
				} catch (Throwable e) {
					errors.offer(e);
				}

		final var error = new IllegalArgumentException(
				"Invalid client credentails for authentication method " + authMethod);
		errors.forEach(error::addSuppressed);
		throw error;
	}

	/**
	 * Matches client secret against the salted hash from
	 * {@link IuAuthorizationCredentials#getJwk()}.
	 * 
	 * @param credentials  {@link IuAuthorizationCredentials}
	 * @param clientSecret client secret or assertion value provided in response to
	 *                     an authentication challenge
	 */
	static void verify(IuAuthorizationCredentials credentials, String clientSecret) {
		final var jwk = credentials.getJwk();
		final var keyType = jwk.getType();
		if (!Type.RAW.equals(keyType))
			throw new IllegalStateException(
					"Invalid key type " + keyType + " for auth method " + credentials.getTokenEndpointAuthMethod());

		final var key = jwk.getKey();
		if (key.length != 36)
			throw new IllegalStateException("Invalid password hash");

		final var password = IuText.utf8(clientSecret);
		final var saltedPassword = new byte[password.length + 4];
		System.arraycopy(key, 0, saltedPassword, 0, 4);
		System.arraycopy(password, 0, saltedPassword, 4, password.length);

		final var digest = IuDigest.sha256(saltedPassword);
		if (!Arrays.equals(digest, 0, 32, key, 4, 36))
			throw new IllegalArgumentException("Invalid client secret");
	}

}
