/*
 * Copyright © 2025 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.net.InetAddress;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuRuntimeEnvironment;
import iu.logging.internal.LogEnvironmentImpl;

@SuppressWarnings("javadoc")
public class LogEnvironmentImplTest {

	@Test
	public void testNulls() {
		final var env = new LogEnvironmentImpl(null, null, false, null, null, null, null, null, null);
		assertNull(env.getNodeId());
		assertFalse(env.isDevelopment());
		assertNull(env.getEndpoint());
		assertNull(env.getApplication());
		assertNull(env.getEnvironment());
		assertNull(env.getModule());
		assertNull(env.getRuntime());
		assertNull(env.getComponent());
		final var env2 = new LogEnvironmentImpl(env, null, false, null, null, null, null, null, null);
		assertNull(env2.getNodeId());
		assertFalse(env2.isDevelopment());
		assertNull(env2.getEndpoint());
		assertNull(env2.getApplication());
		assertNull(env2.getEnvironment());
		assertNull(env2.getModule());
		assertNull(env2.getRuntime());
		assertNull(env2.getComponent());
	}

	@Test
	public void testDefaults() {
		final var nodeId = IdGenerator.generateId();
		final var development = ThreadLocalRandom.current().nextBoolean();
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();

		final var env = new LogEnvironmentImpl(null, nodeId, development, endpoint, application, environment, module,
				runtime, component);

		assertEquals(nodeId, env.getNodeId());
		assertEquals(development, env.isDevelopment());
		assertEquals(endpoint, env.getEndpoint());
		assertEquals(application, env.getApplication());
		assertEquals(environment, env.getEnvironment());
		assertEquals(module, env.getModule());
		assertEquals(runtime, env.getRuntime());
		assertEquals(component, env.getComponent());

		assertEquals("LogEnvironment [nodeId=" + nodeId + ", development=" + development + ", endpoint=" + endpoint
				+ ", application=" + application + ", environment=" + environment + ", module=" + module + ", runtime="
				+ runtime + ", component=" + component + "]", env.toString());
	}

	@Test
	public void testSystemDefaults() {
		final var nodeId = IuException.unchecked(() -> InetAddress.getLocalHost().getHostName());
		final var development = ThreadLocalRandom.current().nextBoolean();
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();

		try (final var mockIuRuntimeEnvironment = mockStatic(IuRuntimeEnvironment.class)) {
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.nodeId")).thenReturn(nodeId);
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.development"))
					.thenReturn(Boolean.toString(development));
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.endpoint")).thenReturn(endpoint);
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.application"))
					.thenReturn(application);
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.environment"))
					.thenReturn(environment);
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.module")).thenReturn(module);
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.runtime")).thenReturn(runtime);
			mockIuRuntimeEnvironment.when(() -> IuRuntimeEnvironment.envOptional("iu.component")).thenReturn(component);

			final var env = new LogEnvironmentImpl();

			assertEquals(nodeId, env.getNodeId());
			assertEquals(development, env.isDevelopment());
			assertEquals(endpoint, env.getEndpoint());
			assertEquals(application, env.getApplication());
			assertEquals(environment, env.getEnvironment());
			assertEquals(module, env.getModule());
			assertEquals(runtime, env.getRuntime());
			assertEquals(component, env.getComponent());

			assertEquals("LogEnvironment [nodeId=" + nodeId + ", development=" + development + ", endpoint=" + endpoint
					+ ", application=" + application + ", environment=" + environment + ", module=" + module
					+ ", runtime=" + runtime + ", component=" + component + "]", env.toString());
		}
	}

	@Test
	public void testInherits() {
		final var nodeId = IdGenerator.generateId();
		final var development = ThreadLocalRandom.current().nextBoolean();
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();

		final var env = new LogEnvironmentImpl(null, nodeId, development, endpoint, application, environment, module,
				runtime, component);

		final var env2 = new LogEnvironmentImpl(env, null, false, null, null, null, null, null, null);

		assertEquals(nodeId, env2.getNodeId());
		assertEquals(development, env2.isDevelopment());
		assertEquals(endpoint, env2.getEndpoint());
		assertEquals(application, env2.getApplication());
		assertEquals(environment, env2.getEnvironment());
		assertEquals(module, env2.getModule());
		assertEquals(runtime, env2.getRuntime());
		assertEquals(component, env2.getComponent());

		assertEquals("LogEnvironment [development=false, defaults=" + env + "]", env2.toString());
	}

	@Test
	public void testOverrides() {
		final var nodeId = IdGenerator.generateId();
		final var development = ThreadLocalRandom.current().nextBoolean();
		final var endpoint = IdGenerator.generateId();
		final var application = IdGenerator.generateId();
		final var environment = IdGenerator.generateId();
		final var module = IdGenerator.generateId();
		final var runtime = IdGenerator.generateId();
		final var component = IdGenerator.generateId();

		final var env = new LogEnvironmentImpl(null, nodeId, development, endpoint, application, environment, module,
				runtime, component);

		final var nodeId2 = IdGenerator.generateId();
		final var development2 = ThreadLocalRandom.current().nextBoolean();
		final var endpoint2 = IdGenerator.generateId();
		final var application2 = IdGenerator.generateId();
		final var environment2 = IdGenerator.generateId();
		final var module2 = IdGenerator.generateId();
		final var runtime2 = IdGenerator.generateId();
		final var component2 = IdGenerator.generateId();

		final var env2 = new LogEnvironmentImpl(env, nodeId2, development2, endpoint2, application2, environment2,
				module2, runtime2, component2);

		assertEquals(nodeId2, env2.getNodeId());
		assertEquals(development || development2, env2.isDevelopment());
		assertEquals(endpoint2, env2.getEndpoint());
		assertEquals(application2, env2.getApplication());
		assertEquals(environment2, env2.getEnvironment());
		assertEquals(module2, env2.getModule());
		assertEquals(runtime2, env2.getRuntime());
		assertEquals(component2, env2.getComponent());

		assertEquals(
				"LogEnvironment [nodeId=" + nodeId2 + ", development=" + development2 + ", endpoint=" + endpoint2
						+ ", application=" + application2 + ", environment=" + environment2 + ", module=" + module2
						+ ", runtime=" + runtime2 + ", component=" + component2 + ", defaults=" + env + "]",
				env2.toString());
	}

}
