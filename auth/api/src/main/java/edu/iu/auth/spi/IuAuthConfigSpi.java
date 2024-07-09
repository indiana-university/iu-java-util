package edu.iu.auth.spi;

import java.io.Closeable;

import iu.auth.IuAuthSpiFactory;

/**
 * Bootstraps authentication and authorization configuration.
 */
public interface IuAuthConfigSpi extends Closeable {

	/**
	 * Bootstraps the authorization configuration layer.
	 * 
	 * <p>
	 * The controlling application should invoke this method with the implementation
	 * module as the context class loader as soon as the module is created.
	 * </p>
	 * 
	 * @return {@link IuAuthConfigSpi}
	 */
	static IuAuthConfigSpi configure() {
		return IuAuthSpiFactory.get(IuAuthConfigSpi.class);
	}

}
