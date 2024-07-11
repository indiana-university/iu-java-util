/**
 * One-time number implementation module.
 * 
 * @provides edu.iu.auth.spi.IuNonceSpi SPI implementation
 */
module iu.util.auth.nonce {
	requires iu.util;
	requires iu.util.auth;
	requires java.logging;

	provides edu.iu.auth.spi.IuNonceSpi with iu.auth.nonce.NonceSpi;
}
