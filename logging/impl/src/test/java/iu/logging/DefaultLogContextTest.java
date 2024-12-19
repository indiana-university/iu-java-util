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
