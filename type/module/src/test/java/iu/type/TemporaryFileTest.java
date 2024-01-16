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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.iu.UnsafeFunction;

@SuppressWarnings("javadoc")
public class TemporaryFileTest extends IuTypeTestCase {

	@BeforeAll
	private static void setupClass() throws ClassNotFoundException {
		Class.forName(TemporaryFile.class.getName());
	}

	@Test
	public void testCantCreateTempFile() {
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenThrow(IOException.class);
			assertThrows(IOException.class, () -> TemporaryFile.init(null));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testThrowsDeletesOnError() throws Throwable {
		var temp = mock(Path.class);
		var initializer = mock(UnsafeFunction.class);
		when(initializer.apply(temp)).thenThrow(IOException.class);
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			assertThrows(IOException.class, () -> TemporaryFile.init(initializer));
			mockFiles.verify(() -> Files.deleteIfExists(temp));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSuppressesDeleteError() throws Throwable {
		var temp = mock(Path.class);
		var deleteError = new IOException();
		var initializer = mock(UnsafeFunction.class);
		when(initializer.apply(temp)).thenThrow(new IOException());
		try (var mockFiles = mockStatic(Files.class)) {
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
		try (var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.createTempFile("iu-type-", ".jar")).thenReturn(temp);
			assertSame(temp, TemporaryFile.init(initializer));
			verify(initializer).apply(temp);
		}
	}

}
