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
package iu.type;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class ComponentFactoryTest {

	@Test
	public void testThrowsIOExceptionOnRead() throws IOException {
		var ioException = new IOException();
		var in = new InputStream() {
			@Override
			public int read() throws IOException {
				throw ioException;
			}
		};
		try (var mockComponentFactory = mockStatic(ComponentFactory.class)) {
			mockComponentFactory.when(() -> ComponentFactory.createComponent(in)).thenCallRealMethod();
			assertSame(ioException, assertThrows(IOException.class, () -> ComponentFactory.createComponent(in)));
		}
	}

	@Test
	public void testThrowsIOExceptionOnClose() throws IOException {
		var ioException = new IOException();
		var in = new ByteArrayInputStream(TestArchives.EMPTY_JAR) {
			@Override
			public void close() throws IOException {
				throw ioException;
			}
		};
		try (var mockComponentFactory = mockStatic(ComponentFactory.class)) {
			mockComponentFactory.when(() -> ComponentFactory.createComponent(in)).thenCallRealMethod();
			assertSame(ioException, assertThrows(IOException.class, () -> ComponentFactory.createComponent(in)));
		}
	}

	@Test
	public void testThrowsIOExceptionOnReadAndClose() throws IOException {
		var ioException = new IOException();
		var dep = new ByteArrayInputStream(TestArchives.EMPTY_JAR);
		var in = new ByteArrayInputStream(TestArchives.EMPTY_JAR) {
			@Override
			public void close() throws IOException {
				throw ioException;
			}
		};
		try (var mockComponentFactory = mockStatic(ComponentFactory.class)) {
			mockComponentFactory.when(() -> ComponentFactory.createComponent(dep, in)).thenCallRealMethod();
			mockComponentFactory.when(() -> ComponentFactory.createFromSourceQueue(any())).thenThrow(new IOException());
			assertSame(ioException, assertThrows(IOException.class, () -> ComponentFactory.createComponent(dep, in))
					.getSuppressed()[0]);
		}
	}

	@Test
	public void testThrowsRuntimeExceptionOnClose() throws IOException {
		var illegalStateException = new IllegalStateException();
		var in = new ByteArrayInputStream(TestArchives.EMPTY_JAR) {
			@Override
			public void close() throws IOException {
				throw illegalStateException;
			}
		};
		try (var mockComponentFactory = mockStatic(ComponentFactory.class)) {
			mockComponentFactory.when(() -> ComponentFactory.createComponent(in)).thenCallRealMethod();
			assertSame(illegalStateException,
					assertThrows(IllegalStateException.class, () -> ComponentFactory.createComponent(in)));
		}
	}

	@Test
	public void testThrowsErrorOnClose() throws IOException {
		var error = new Error();
		var in = new ByteArrayInputStream(TestArchives.EMPTY_JAR) {
			@Override
			public void close() throws IOException {
				throw error;
			}
		};
		try (var mockComponentFactory = mockStatic(ComponentFactory.class)) {
			mockComponentFactory.when(() -> ComponentFactory.createComponent(in)).thenCallRealMethod();
			assertSame(error, assertThrows(Error.class, () -> ComponentFactory.createComponent(in)));
		}
	}

}
