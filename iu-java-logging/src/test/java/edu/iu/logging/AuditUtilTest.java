package edu.iu.logging;

import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

public class AuditUtilTest {

	private static final Logger LOG;
	static {
//		LoggingEnvironment.bootstrap(AuditUtilTest.class.getClassLoader());
		LOG = Logger.getLogger(AuditUtilTest.class.getName());
	}

	@Test
	public void testLogLevels() {
		LOG.finest("Test finest 1");
		LOG.finer("Test finer 1");
		LOG.fine("Test fine 1");
		LOG.config("Test config 1");
		LOG.info("Test info 1");
		LOG.warning("Test warning 1");
		LOG.severe("Test severe 1");
		LOG.finest("Test finest 2");
		LOG.finer("Test finer 2");
		LOG.fine("Test fine 2");
		LOG.config("Test config 2");
		LOG.info("Test info 2");
		LOG.warning("Test warning 2");
		LOG.severe("Test severe 2");
	}

}
