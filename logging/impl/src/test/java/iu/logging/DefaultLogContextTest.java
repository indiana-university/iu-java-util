package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import iu.logging.internal.DefaultLogContext;

@SuppressWarnings("javadoc")
public class DefaultLogContextTest {

	@Test
	public void testDefaults() {
		final var nodeId = IuException.unchecked(InetAddress::getLocalHost).getHostName();
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var context = new DefaultLogContext(endpoint, application, environment);
		assertEquals(nodeId, context.getNodeId());
		assertEquals(endpoint, context.getEndpoint());
		assertEquals(application, context.getApplication());
		assertEquals(environment, context.getEnvironment());
		assertNull(context.getCalledUrl());
		assertNull(context.getCallerIpAddress());
		assertNull(context.getCallerPrincipalName());
		assertNull(context.getComponent());
		assertNull(context.getImpersonatedPrincipalName());
		assertNull(context.getLevel());
		assertFalse(context.isDevelopment());
		assertNull(context.getModule());
		assertNull(context.getRequestId());
		assertEquals("DefaultLogContext [nodeId=" + nodeId + ", endpoint=" + endpoint + ", application=" + application
				+ ", environment=" + environment + "]", context.toString());
	}

}
