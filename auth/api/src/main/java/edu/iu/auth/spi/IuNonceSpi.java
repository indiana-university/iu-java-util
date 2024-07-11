package edu.iu.auth.spi;

import edu.iu.auth.IuOneTimeNumber;
import edu.iu.auth.IuOneTimeNumberConfig;

/**
 * One-time number (nonce) service provider interface.
 */
public interface IuNonceSpi {

	/**
	 * Initializes a new one-time number generator.
	 * 
	 * @param config configuration properties
	 * @return One-time number generator
	 */
	IuOneTimeNumber initialize(IuOneTimeNumberConfig config);

}
