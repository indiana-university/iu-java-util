/**
 * JWT API module
 * 
 * @uses iu.jwt.spi.IuJwtSpi static access to implementation resources
 */
module iu.util.jwt.api {
	exports edu.iu.jwt;
	exports iu.jwt.spi;

	requires transitive iu.util;
	requires transitive iu.util.crypt;
	requires java.logging;

	uses iu.jwt.spi.IuJwtSpi;
}
