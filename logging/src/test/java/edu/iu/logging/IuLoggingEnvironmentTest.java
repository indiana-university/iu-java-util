/*
 * Copyright © 2024 Indiana University
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
package edu.iu.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import edu.iu.logging.IuLoggingEnvironment.RuntimeMode;
import iu.logging.TestIuLoggingEnvironmentImpl;

/**
 * Test class for IuLoggingEnvironment.
 */
public class IuLoggingEnvironmentTest {

	/**
	 * Test default methods.
	 */
	@Test
	public void testIuLoggingEnvironmentDefaults() {
		IuLoggingEnvironment environment = new IuLoggingEnvironment() {
		};

		assertNull(environment.getApplication());
		assertNull(environment.getComponent());
		assertNull(environment.getEnvironment());
		assertNull(environment.getHostname());
		assertNull(environment.getMode());
		assertNull(environment.getModule());
		assertNull(environment.getNodeId());
		assertNull(environment.getRuntime());
	}

	/**
	 * Test overridden methods.
	 */
	@Test
	public void testIuLoggingEnvironmentOverridden() {
		IuLoggingEnvironment environment = new TestIuLoggingEnvironmentImpl();

		assertEquals("Test Application", environment.getApplication(), "Incorrect Overridden Application");
		assertEquals("Test Component", environment.getComponent(), "Incorrect Overridden Component");
		assertEquals("Test Environment", environment.getEnvironment(), "Incorrect Overridden Environment");
		assertEquals("Test Hostname", environment.getHostname(), "Incorrect Overridden Hostname");
		assertEquals(RuntimeMode.TEST, environment.getMode(), "Incorrect Overridden Runtime Mode");
		assertEquals("Test Module", environment.getModule(), "Incorrect Overridden Module");
		assertEquals("Test Node Id", environment.getNodeId(), "Incorrect Overridden Node Id");
		assertEquals("Test Runtime", environment.getRuntime(), "Incorrect Overridden Runtime");
	}

	/**
	 * Test all RuntimeModes.
	 */
	@Test
	public void testRuntimeMode() {
		assertEquals(3, RuntimeMode.values().length);
		assertEquals(RuntimeMode.DEVELOPMENT, RuntimeMode.valueOf(RuntimeMode.class, "DEVELOPMENT"));
		assertEquals(RuntimeMode.TEST, RuntimeMode.valueOf(RuntimeMode.class, "TEST"));
		assertEquals(RuntimeMode.PRODUCTION, RuntimeMode.valueOf(RuntimeMode.class, "PRODUCTION"));
	}
	
	/**
	 * Test bootstrap. Just a pass-through to LogEventFactory.bootstrap, so not checking boostrap results.
	 */
	@Test
	public void testBootstrap() {
		IuLoggingEnvironment.bootstrap(Thread.currentThread().getContextClassLoader());
	}
}
