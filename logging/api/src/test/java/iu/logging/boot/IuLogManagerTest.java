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
package iu.logging.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.UnsafeRunnable;

@SuppressWarnings("javadoc")
public class IuLogManagerTest {

	@Test
	public void testConfigurationBlock() {
		final var logManager = new IuLogManager();
		assertThrows(UnsupportedOperationException.class, () -> logManager.addConfigurationListener(null));
		assertThrows(UnsupportedOperationException.class, () -> logManager.addLogger(null));
		assertThrows(UnsupportedOperationException.class, () -> logManager.readConfiguration());
		assertThrows(UnsupportedOperationException.class, () -> logManager.readConfiguration(null));
		assertThrows(UnsupportedOperationException.class, () -> logManager.removeConfigurationListener(null));
		assertThrows(UnsupportedOperationException.class, () -> logManager.reset());
		assertThrows(UnsupportedOperationException.class, () -> logManager.updateConfiguration(null));
		assertThrows(UnsupportedOperationException.class, () -> logManager.updateConfiguration(null, null));
	}

	@Test
	public void testBoundNested() throws Throwable {
		IuLogManager.bound(() -> {
			final var r = mock(UnsafeRunnable.class);
			IuLogManager.bound(r);
			verify(r).run();
		});
	}

	@Test
	public void testBoundedAllowsAddConfigurationListener() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			final var listener = mock(Runnable.class);
			assertDoesNotThrow(() -> logManager.addConfigurationListener(listener));
		});
	}

	@Test
	public void testBoundedAllowsAddLogger() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			final var name = IdGenerator.generateId();
			final var logger = mock(Logger.class);
			when(logger.getName()).thenReturn(name);
			when(logger.getHandlers()).thenReturn(new Handler[0]);
			assertDoesNotThrow(() -> logManager.addLogger(logger));
			assertSame(logger, logManager.getLogger(name));
		});

		assertNotNull(Logger.getLogger(IdGenerator.generateId()));
	}

	@Test
	public void testBoundedAllowsReadConfiguration() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			assertDoesNotThrow(() -> logManager.readConfiguration());
		});
	}

	@Test
	public void testBoundedAllowsReadConfigurationFromInput() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			final var in = mock(InputStream.class);
			assertDoesNotThrow(() -> logManager.readConfiguration(in));
		});
	}

	@Test
	public void testBoundedAllowsRemoveConfigurationListener() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			final var listener = mock(Runnable.class);
			assertDoesNotThrow(() -> logManager.removeConfigurationListener(listener));
		});
	}

	@Test
	public void testBoundedAllowsReset() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			assertDoesNotThrow(() -> logManager.reset());
		});
	}

	@Test
	public void testBoundedAllowsUpdateConfiguration() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			assertDoesNotThrow(() -> logManager.updateConfiguration(null));
		});
	}

	@Test
	public void testBoundedAllowsUpdateConfigurationFromInput() throws Throwable {
		final var logManager = new IuLogManager();
		IuLogManager.bound(() -> {
			final var in = mock(InputStream.class);
			assertDoesNotThrow(() -> logManager.updateConfiguration(in, null));
		});
	}

}
