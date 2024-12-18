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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import edu.iu.IuException;
import edu.iu.IuFixedLimitOutputBuffer;
import edu.iu.IuText;

/**
 * Publishes log messages to files.
 */
class LogFilePublisher {

	private static final Map<String, Object> MUTEX = new HashMap<>();

	private final Object mutex;
	private final Path logFile;
	private final Path lockFile;
	private final long maxSize;
	private final int nlimit;

	private Queue<String> messages = new ConcurrentLinkedQueue<>();

	private byte[] poll() {
		return IuText.utf8(messages.poll());
	}

	/**
	 * Rotates log files.
	 * 
	 * @throws IOException if an error occurs
	 */
	void rotate() throws IOException {
		for (int i = nlimit - 1; i >= 0; i--) {
			final var f = i == 0 ? logFile : Path.of(logFile.toString() + '.' + i);
			final var f1 = Path.of(logFile.toString() + '.' + (i + 1));
			if (Files.exists(f)) {
				Files.deleteIfExists(f1);
				Files.move(f, f1);
				Files.createFile(f);
			}
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param path    {@link Path} of the primary log file
	 * @param maxSize maximum file size
	 * @param nlimit  maximum number of backup files to retain
	 */
	LogFilePublisher(Path path, long maxSize, int nlimit) {
		this.logFile = path;
		this.lockFile = Path.of(path.getParent().toString(), "." + path.getFileName() + ".lock");
		this.maxSize = maxSize;
		this.nlimit = nlimit;

		String fileName = logFile.getFileName().toString();
		Object mutex;
		synchronized (MUTEX) {
			mutex = MUTEX.get(fileName);
			if (mutex == null)
				MUTEX.put(fileName, mutex = new Object());
		}
		this.mutex = mutex;
	}

	/**
	 * Synchronously writes all buffered data to the log file.
	 * 
	 * @throws IOException If an I/O error occurs
	 */
	void flush() throws IOException {
		final IuFixedLimitOutputBuffer outputBuffer;
		{
			var count = 0L;
			if (Files.exists(logFile) && (count = IuException
					.unchecked(() -> Files.readAttributes(logFile, BasicFileAttributes.class).size())) >= maxSize) {
				rotate();
				count = 0L;
			}
			outputBuffer = new IuFixedLimitOutputBuffer(count, maxSize);
		}
		if (!outputBuffer.fill(this::poll))
			return;

		synchronized (mutex) {
			FileChannel lockChannel = null;
			FileLock lock = null;
			long lockTimeout = System.currentTimeMillis() + 2000L;
			while (lock == null //
					&& System.currentTimeMillis() < lockTimeout)
				try {
					lockChannel = FileChannel.open(lockFile, StandardOpenOption.WRITE, StandardOpenOption.APPEND,
							StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
					lock = lockChannel.tryLock();
				} catch (IOException | RuntimeException | Error e) {
					IuException.suppress(e, () -> mutex.wait(100L));
					throw e;
				}

			if (lock == null)
				throw new IllegalStateException("Failed to acquire file lock " + lockFile);

			try {
				boolean done = false;
				do {
					try (final var out = Files.newOutputStream(logFile, StandardOpenOption.CREATE,
							StandardOpenOption.APPEND)) {
						outputBuffer.write(this::poll, out);
					}

					if ((IuException.unchecked(
							() -> Files.readAttributes(logFile, BasicFileAttributes.class).size())) >= maxSize) {
						rotate();
						outputBuffer.resetCount();
						continue;
					}

					done = true;
				} while (!done);
			} finally {
				lock.release();
				lockChannel.close();
			}
		}

	}

	/**
	 * Publishes a formatted message to the log file.
	 * 
	 * @param message formatted message
	 */
	void publish(String message) {
		messages.offer(message + System.lineSeparator());
	}

}