package edu.iu.web.tomcat;

import org.apache.catalina.startup.ContextConfig;

public class IuTomcatContextConfig extends ContextConfig {

	@Override
	protected void applicationAnnotationsConfig() {
		// SIS-9303 skip Tomcat annotation binding
	}

}
