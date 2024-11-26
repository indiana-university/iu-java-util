package iu.crypt.init;

import edu.iu.crypt.Init;
import jakarta.annotation.Resource;

/**
 * Bootstraps {@link Init}
 */
@Resource
class CryptImplResource {

	/**
	 * Default constructor.
	 */
	CryptImplResource() {
		Init.init();
	}
}
