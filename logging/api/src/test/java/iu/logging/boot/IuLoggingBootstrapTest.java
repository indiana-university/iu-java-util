/*
 * Copyright © 2026 Indiana University
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
package iu.logging.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;
import edu.iu.type.base.TemporaryFile;

@SuppressWarnings("javadoc")
public class IuLoggingBootstrapTest {

	@Test
	public void testInitAnyDestroy() {
		try (final var mockTemporaryFile = mockStatic(TemporaryFile.class)) {
			final IuLoggingBootstrap bootstrap = assertDoesNotThrow(() -> new IuLoggingBootstrap(true));
			assertFalse(bootstrap.isDestroyed());
			assertDoesNotThrow(bootstrap::destroy);
			assertTrue(bootstrap.isDestroyed());
			assertDoesNotThrow(bootstrap::destroy);
		}
	}

	@Test
	public void testInitDestroysPrevious() {
		try (final var mockTemporaryFile = mockStatic(TemporaryFile.class)) {
			final IuLoggingBootstrap bootstrap = assertDoesNotThrow(() -> new IuLoggingBootstrap(true));
			assertDoesNotThrow(() -> new IuLoggingBootstrap(true));
			assertTrue(bootstrap.isDestroyed());
		}
	}

	@Test
	public void testInitDestroyPreviousError() {
		final var error = new IllegalStateException();
		final IuLoggingBootstrap bootstrap = mock(IuLoggingBootstrap.class);
		assertDoesNotThrow(() -> {
			doThrow(error).when(bootstrap).destroy();
			final var f = IuLoggingBootstrap.class.getDeclaredField("initialized");
			f.setAccessible(true);
			f.set(null, bootstrap);
		});

		IuTestLogger.expect(IuLoggingBootstrap.class.getName(), Level.WARNING,
				"Failed to destroy logging implementation module after hot replace", IllegalStateException.class,
				e -> e == error);

		try (final var mockTemporaryFile = mockStatic(TemporaryFile.class)) {
			final var newBootstrap = assertDoesNotThrow(() -> new IuLoggingBootstrap());
			assertDoesNotThrow(newBootstrap::destroy);
		}
	}
}
