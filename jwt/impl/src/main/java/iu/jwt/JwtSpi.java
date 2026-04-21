package iu.jwt;

import edu.iu.crypt.WebKey;
import edu.iu.jwt.WebToken;
import edu.iu.jwt.WebTokenBuilder;
import iu.jwt.spi.IuJwtSpi;

/**
 * Provides static SPI access to implementation resources.
 */
public class JwtSpi implements IuJwtSpi {

	@Override
	public WebTokenBuilder getJwtBuilder() {
		return new JwtBuilder<>();
	}

	@Override
	public WebToken verifyJwt(String jwt, WebKey issuerKey) {
		return Jwt.verify(jwt, issuerKey);
	}

	@Override
	public WebToken decryptAndVerifyJwt(String jwt, WebKey issuerKey, WebKey decryptKey) {
		return Jwt.decryptAndVerify(jwt, issuerKey, decryptKey);
	}

}
