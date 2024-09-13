package edu.iu.crypt;

import java.util.ServiceLoader;

import iu.crypt.spi.IuCryptSpi;

/**
 * Initialization stub to be <em>explicitly</em> loaded from the bootstrap
 * module in control of the implementation's {@link ModuleLayer}, before
 * attempting to use any crypto functions, while the implementation module's
 * {@link ClassLoader} is in control of the
 * {@link Thread#getContextClassLoader()} current thread's context.
 * 
 * <p>
 * Note: When both iu.util.crypt and iu.util.crypt.impl are in named modules
 * loaded by the {@link ClassLoader#getSystemClassLoader() System ClassLoader},
 * explicit initialization is not needed.
 * </p>
 */
public final class Init {

	private Init() {
	}

	/** {@link IuCryptSpi} instance */
	static final IuCryptSpi SPI = ServiceLoader.load(IuCryptSpi.class).findFirst().get();

}
