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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Test class for LoggingFilters.
 */
public class LoggingFiltersTest {

	/**
	 * Test isLocal when the current thread's class loader is null.
	 */
	@Test
	public void testIsLocalNullClassLoader() {
		Thread.currentThread().setContextClassLoader(null);
		assertTrue(LoggingFilters.isLocal());
	}

	/**
	 * Test isLocal when the current thread's class loader is null.
	 */
	@Test
	public void testIsLocal() {
		assertTrue(LoggingFilters.isLocal());
	}

	/**
	 * Test isLocal when the current thread's class loader is null.
	 */
	@Test
	public void testIsLocalClassLoaderThrowsException() {
		ClassLoader cl = mock(ClassLoader.class);
		Thread.currentThread().setContextClassLoader(cl);
		try {
			Mockito.when(cl.loadClass(LoggingFilters.class.getName())).thenThrow(new ClassNotFoundException());
			assertFalse(LoggingFilters.isLocal());
		} catch (ClassNotFoundException e) {
		}
	}
}
