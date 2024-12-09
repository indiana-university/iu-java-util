/**
 * Configures logging.
 */
module iu.util.logging {
	exports edu.iu.logging;
	opens edu.iu.logging to iu.util.logging.impl;
	
	requires iu.util;
	requires iu.util.type.base;
	requires transitive java.logging;
}
