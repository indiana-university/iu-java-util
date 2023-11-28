/*
 * Copyright © 2023 Indiana University
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
package iu.type.bundle;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import edu.iu.IuStream;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class TypeBundleSpiTest {

	@Test
	public void testCopyHandlesErrors() {
		final var e = new IOException();
		final var in = mock(InputStream.class);
		try (final var mockIuStream = mockStatic(IuStream.class); //
				final var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockIuStream.when(() -> IuStream.copy(eq(in), any(OutputStream.class))).thenThrow(e);
			assertSame(e, assertThrows(IOException.class, () -> TypeBundleSpi.copyJarToTempFile("", in)));
			mockFiles.verify(() -> Files.deleteIfExists(any()));
		}
	}

	@Test
	public void testCopySuppressesCleanupErrors() {
		final var e = new IOException();
		final var e2 = new IOException();
		final var in = mock(InputStream.class);
		try (final var mockIuStream = mockStatic(IuStream.class); //
				final var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockIuStream.when(() -> IuStream.copy(eq(in), any(OutputStream.class))).thenThrow(e);
			mockFiles.when(() -> Files.deleteIfExists(any())).then(a -> {
				throw e2;
			});
			assertSame(e, assertThrows(IOException.class, () -> TypeBundleSpi.copyJarToTempFile("", in)));
			assertSame(e2, e.getSuppressed()[0]);
		}
	}

	@Test
	public void testCleanupLogsFailures() throws Exception {
		final var e = new Exception();
		final var closeable = mock(AutoCloseable.class);
		doThrow(e).when(closeable).close();
		IuTestLogger.expect(TypeBundleSpi.class.getName(), Level.WARNING, "Failed to clean up resources",
				Exception.class, thrown -> thrown == e);

		final var t = mock(Path.class);
		final var e2 = new IOException();
		try (final var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.deleteIfExists(t)).thenThrow(e2);
			IuTestLogger.expect(TypeBundleSpi.class.getName(), Level.WARNING, "Failed to clean up temp file.*",
					IOException.class, thrown -> thrown == e2);

			TypeBundleSpi.cleanUp(closeable, List.of(t));
		}
	}

}
