package edu.iu.web.tomcat;

import org.apache.catalina.startup.ContextConfig;

/**
 * IU-specific Tomcat context configuration.
 */
public class IuTomcatContextConfig extends ContextConfig {

	/**
	 * Default constructor.
	 */
	public IuTomcatContextConfig() {
		// Default constructor
	}

	@Override
	protected void applicationAnnotationsConfig() {
		// SIS-9303 skip Tomcat annotation binding
	}

}
