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
package iu.logging.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import edu.iu.IdGenerator;
import edu.iu.IuStream;
import edu.iu.IuText;

@SuppressWarnings("javadoc")
public class LogFilePublisherTest {

	private Path path;
	private Path path1;
	private Path path2;
	private Path lockPath;

	@BeforeEach
	public void setup(TestInfo testInfo) throws IOException {
		path = Files.createTempFile(getClass().getSimpleName(), testInfo.getTestMethod().get().getName());
		path1 = Path.of(path + ".1");
		path2 = Path.of(path + ".2");
		lockPath = Path.of(path.getParent() + File.separator + "." + path.getFileName() + ".lock");
	}

	@AfterEach
	public void teardown() throws IOException {
		Files.deleteIfExists(lockPath);
		Files.deleteIfExists(path2);
		Files.deleteIfExists(path1);
		Files.deleteIfExists(path);
	}

	@Test
	public void testRotate() throws IOException {
		final var msg1 = IdGenerator.generateId();
		final var pub = new LogFilePublisher(path, 32L, 3);
		pub.publish(msg1);
		pub.flush();
		try (final var in = Files.newInputStream(path1)) {
			assertEquals(msg1, IuText.utf8(IuStream.read(in)));
		}
	}

	@Test
	public void testFlushRotates() throws IOException {
		final var msg1 = IdGenerator.generateId();
		try (final var out = Files.newOutputStream(path)) {
			out.write(IuText.utf8(msg1));
		}

		final var pub = new LogFilePublisher(path, 32L, 3);
		pub.flush();
		try (final var in = Files.newInputStream(path1)) {
			assertEquals(msg1, IuText.utf8(IuStream.read(in)));
		}
	}

	@Test
	public void testRotateLockFailure() throws IOException {
		final var msg1 = IdGenerator.generateId();
		final var pub = new LogFilePublisher(path, 32L, 3);
		pub.publish(msg1);

		final var lockChannel = mock(FileChannel.class);
		try (final var mockFileChannel = mockStatic(FileChannel.class)) {
			mockFileChannel.when(() -> FileChannel.open(lockPath, StandardOpenOption.WRITE, StandardOpenOption.APPEND,
					StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE)).thenReturn(lockChannel);

			final var error = assertThrows(IllegalStateException.class, pub::flush);
			assertEquals("Failed to acquire file lock " + lockPath, error.getMessage());
		}
	}

	@Test
	public void testRotateLockError() throws IOException {
		final var msg1 = IdGenerator.generateId();
		final var pub = new LogFilePublisher(path, 32L, 3);
		pub.publish(msg1);

		final var error = new IllegalStateException();
		try (final var mockFileChannel = mockStatic(FileChannel.class)) {
			mockFileChannel.when(() -> FileChannel.open(lockPath, StandardOpenOption.WRITE, StandardOpenOption.APPEND,
					StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE)).thenThrow(error);

			assertSame(error, assertThrows(IllegalStateException.class, pub::flush));
		}
	}

	@Test
	public void testFileWriteError() throws IOException {
		final var pub = new LogFilePublisher(path, 32L, 3);
		try (final var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.exists(any())).thenReturn(true);
			mockFiles.when(() -> Files.deleteIfExists(any())).thenThrow(IOException.class);
			assertThrows(IOException.class, pub::rotate);
		}
	}

	@Test
	public void testFileRotateError() throws IOException {
		final var pub = new LogFilePublisher(path, 32L, 3);
		try (final var mockFiles = mockStatic(Files.class)) {
			mockFiles.when(() -> Files.exists(path)).thenReturn(true);
			mockFiles.when(() -> Files.move(path, path1)).thenThrow(IOException.class);
			assertThrows(IOException.class, pub::rotate);
		}
	}

	@Test
	public void testDuelingPublishers() throws IOException {
		final var msg1 = IdGenerator.generateId();
		final var pub1 = new LogFilePublisher(path, 64L + System.lineSeparator().length() * 2, 3);
		final var msg2 = IdGenerator.generateId();
		final var pub2 = new LogFilePublisher(path, 64L + System.lineSeparator().length() * 2, 3);
		pub1.publish(msg1);
		pub2.publish(msg2);
		pub2.flush();
		pub1.flush();
		final var msg3 = IdGenerator.generateId();
		final var msg4 = IdGenerator.generateId();
		pub2.publish(msg4);
		pub1.publish(msg3);
		pub1.flush();
		pub2.flush();
		pub2.flush(); // superfluous
		try (final var in = Files.newInputStream(path1)) {
			assertEquals(msg3 + System.lineSeparator() + msg4 + System.lineSeparator(), IuText.utf8(IuStream.read(in)));
		}
		try (final var in = Files.newInputStream(path2)) {
			assertEquals(msg2 + System.lineSeparator() + msg1 + System.lineSeparator(), IuText.utf8(IuStream.read(in)));
		}
	}

	@Test
	public void testFlushNothing() throws IOException {
		Files.delete(path);
		final var pub = new LogFilePublisher(path, 32L, 3);
		pub.flush();
		assertFalse(Files.exists(path));
	}

	@Test
	public void testFlushEverything() throws IOException {
		Files.delete(path);
		final var pub = new LogFilePublisher(path, 64L + System.lineSeparator().length() * 2, 3);
		final var msg1 = IdGenerator.generateId();
		final var msg2 = IdGenerator.generateId();
		final var msg3 = IdGenerator.generateId();
		final var msg4 = IdGenerator.generateId();
		pub.publish(msg1);
		pub.publish(msg2);
		pub.publish(msg3);
		pub.publish(msg4);
		pub.flush();
		try (final var in = Files.newInputStream(path)) {
			assertEquals("", IuText.utf8(IuStream.read(in)));
		}
		try (final var in = Files.newInputStream(path1)) {
			assertEquals(msg3 + System.lineSeparator() + msg4 + System.lineSeparator(), IuText.utf8(IuStream.read(in)));
		}
		try (final var in = Files.newInputStream(path2)) {
			assertEquals(msg1 + System.lineSeparator() + msg2 + System.lineSeparator(), IuText.utf8(IuStream.read(in)));
		}
	}

}
