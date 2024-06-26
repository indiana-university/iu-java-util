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
package edu.iu.type.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuStream;
import edu.iu.IuText;
import edu.iu.UnsafeFunction;

@SuppressWarnings("javadoc")
public class TemporaryFileTest {

	@BeforeAll
	private static void setupClass() throws ClassNotFoundException {
		Class.forName(TemporaryFile.class.getName());
	}

	@Test
	public void testCantCreateTempFile() {
		try (var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenThrow(IOException.class);
			assertThrows(IOException.class, () -> TemporaryFile.init((UnsafeFunction<Path, ?>) null));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testThrowsDeletesOnError() throws Throwable {
		var temp = mock(Path.class);
		var initializer = mock(UnsafeFunction.class);
		when(initializer.apply(temp)).thenThrow(IOException.class);
		try (var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			mockFiles.when(() -> Files.deleteIfExists(temp)).thenReturn(true);
			assertEquals(0,
					assertThrows(IOException.class, () -> TemporaryFile.init(initializer)).getSuppressed().length);
			mockFiles.verify(() -> Files.deleteIfExists(temp), times(2));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSuppressesDeleteError() throws Throwable {
		var temp = mock(Path.class);
		var deleteError = new IOException();
		var initializer = mock(UnsafeFunction.class);
		when(initializer.apply(temp)).thenThrow(new IOException());
		try (var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			mockFiles.when(() -> Files.deleteIfExists(temp)).thenThrow(deleteError);
			assertSame(deleteError,
					assertThrows(IOException.class, () -> TemporaryFile.init(initializer)).getSuppressed()[0]);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testWorks() throws Throwable {
		var temp = mock(Path.class);
		var initializer = mock(UnsafeFunction.class);
		when(initializer.apply(temp)).thenReturn(temp);
		try (var mockFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			assertSame(temp, TemporaryFile.init(initializer));
			verify(initializer).apply(temp);
		}
	}

	@Test
	public void testDoubleInit() throws IOException {
		TemporaryFile.init(() -> TemporaryFile.init(() -> {
		}));
	}

	@Test
	public void testManagedCleanup() throws Throwable {
		class Box {
			Path path;
		}
		final var box = new Box();

		final var destroy = TemporaryFile.init(() -> {
			box.path = TemporaryFile.init(p -> p);
		});
		assertTrue(Files.exists(box.path));
		destroy.run();
		assertFalse(Files.exists(box.path));
	}

	@Test
	public void testInputStream() throws Throwable {
		final var id = IdGenerator.generateId();
		TemporaryFile.init(() -> {
			final var p = TemporaryFile.of(new ByteArrayInputStream(IuText.utf8(id)));
			try (final var r = Files.newBufferedReader(p)) {
				assertEquals(id, IuStream.read(r));
			}
		}).run();
	}

	@Test
	public void testRejectsNonFileURL() throws Throwable {
		assertThrows(IllegalArgumentException.class, () -> TemporaryFile.of(new URL("http://localhost/")));
	}

	@Test
	public void testRejectsNonFileJarURL() throws Throwable {
		assertThrows(IllegalArgumentException.class, () -> TemporaryFile.of(new URL("jar:http://localhost/!/foo")));
	}

	@Test
	public void testBundle() throws Throwable {
		TemporaryFile.init(() -> {
			final var bundleUrl = getClass().getClassLoader().getResource("iu-java-type-testruntime-bundle.jar");
			final var bundle = TemporaryFile.of(bundleUrl);
			assertNotNull(bundle);
			assertThrows(IllegalArgumentException.class, () -> TemporaryFile.of(new URL("jar:" + bundleUrl + "!/foo")));
			final var readBundle = TemporaryFile.readBundle(bundleUrl);
			int c = 0;
			for (final var p : readBundle) {
				c++;
				assertTrue(p.toString().endsWith(".jar"), p::toString);
			}
			assertEquals(7, c);

			final var bundleWithinBundleUrl = new URL(
					"jar:" + bundleUrl.toExternalForm() + "!/iu-java-type-testruntime.jar");
			final var bundleWithinBundle = TemporaryFile.of(bundleWithinBundleUrl);
			assertNotNull(bundleWithinBundle);

			final var readBundleJar = TemporaryFile.readBundle(bundleWithinBundleUrl);
			c = 0;
			for (final var p : readBundleJar) {
				c++;
				assertTrue(p.toString().endsWith(".jar"), p::toString);
			}
			assertEquals(6, c);
		}).run();
	}

}
