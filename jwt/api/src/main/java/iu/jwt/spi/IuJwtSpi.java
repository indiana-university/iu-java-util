package iu.jwt.spi;

import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;

/**
 * JWT SPI interface.
 */
public interface IuJwtSpi {

	/**
	 * Creates a new {@link WebTokenBuilder} instance.
	 * 
	 * @return {@link WebTokenBuilder}
	 */
	WebTokenBuilder getJwtBuilder();

	/**
	 * Verifies the signature on a JWT and returns parsed claim values.
	 * 
	 * @param jwt       compact JWT serialization
	 * @param issuerKey key to use for verifying the token signature
	 * @return parsed claim values
	 */
	WebToken verifyJwt(String jwt, WebKey issuerKey);

	/**
	 * Decrypts and verifies the signature on am encrypted JWT, and returns parsed
	 * claim values.
	 * 
	 * @param jwt        compact JWT serialization
	 * @param issuerKey  key to use for verifying the token signature
	 * @param decryptKey key to use for decrypting the token
	 * @return parsed claim values
	 */
	WebToken decryptAndVerifyJwt(String jwt, WebKey issuerKey, WebKey decryptKey);

}
