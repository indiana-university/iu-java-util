package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import edu.iu.logging.LoggingEnvironment;

public class AuditUtilTest {

	private static final Logger LOG;
	static {
		LoggingEnvironment.bootstrap(AuditUtilTest.class.getClassLoader());
		LOG = Logger.getLogger(AuditUtilTest.class.getName());
	}

	@Test
	public void testLogLevels() {
		System.err.println("before log calls");
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
		System.err.println("after log calls");

		Iterable<IuLogEvent> events = IuLogHandler.getLogEvents();
		List<IuLogEvent> eventsList = new ArrayList<>();
		events.forEach(eventsList::add);
		for (IuLogEvent e : eventsList) {
			System.err.println(e.getMessage());
		}
		for (int i = 0; i < eventsList.size(); i++) {
			System.err.println("i: " + i + eventsList.get(i).getMessage());
		}
		assertEquals(15, eventsList.size());
	}

}
