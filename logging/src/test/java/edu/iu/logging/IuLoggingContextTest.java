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

import iu.logging.TestIuLoggingContextImpl;

/**
 * Test class for IuLoggingContext.
 */
public class IuLoggingContextTest {

	/**
	 * Test default methods.
	 */
	@Test
	public void testDefaults() {
		IuLoggingContext context = new IuLoggingContext() {

		};
		assertNull(context.getAuthenticatedPrincipal());
		assertNull(context.getCalledUrl());
		assertNull(context.getRemoteAddr());
		assertNull(context.getReqNum());
		assertNull(context.getUserPrincipal());
	}

	/**
	 * Test default methods overridden.
	 */
	@Test
	public void testOverridden() {
		IuLoggingContext context = new TestIuLoggingContextImpl();
		assertEquals("Test Authenticated Principal", context.getAuthenticatedPrincipal(),
				"Incorrect Overridden Authenticated Princpal");
		assertEquals("Test Called URL", context.getCalledUrl(), "Incorrect Overridden Called URL");
		assertEquals("Test Remote Address", context.getRemoteAddr(), "Incorrect Overridden Remote Address");
		assertEquals("Test Request Number", context.getReqNum(), "Incorrect Overridden Request Number");
		assertEquals("Test User Principal", context.getUserPrincipal(), "Incorrect Overridden User Principal");
	}

	/**
	 * Test getCurrentContext.
	 */
	@Test
	public void testGetCurrentContext() {
		IuLoggingContext context = IuLoggingContext.getCurrentContext();
		assertNull(context.getAuthenticatedPrincipal());
		assertNull(context.getCalledUrl());
		assertNull(context.getRemoteAddr());
		assertNull(context.getReqNum());
		assertNull(context.getUserPrincipal());
		assertEquals(context, IuLoggingContext.getCurrentContext());
	}

	/**
	 * Test bound.
	 */
	@Test
	public void testBound() {
		IuLoggingContext context = new TestIuLoggingContextImpl();
		IuLoggingContext.bound(context, () -> {
			assertEquals(context, IuLoggingContext.getCurrentContext());
		});
	}
}
