package iu.logging;

import static iu.logging.IuLogHandler.handleFileWriteError;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

class LogFilePublisher {

	private static final Map<String, Object> MUTEX = new HashMap<>();

	private final Object mutex;
	private final File logFile;
	private final File lockFile;
	private final long maxSize;
	private final int nlimit;

	private Queue<String> messages = new ConcurrentLinkedQueue<>();

	public void rotate() {
		for (int i = nlimit - 1; i >= 0; i--) {
			File f = i == 0 ? logFile : new File(logFile.getPath() + '.' + i);
			File f1 = new File(logFile.getPath() + '.' + (i + 1));

			if (f1.exists() && !f1.delete())
				handleFileWriteError("Failed to remove old log file " + f, null);
			if (!f.renameTo(f1) && f.exists())
				handleFileWriteError(f + " not writable or rename failed rotating to " + f1, null);
		}
	}

	LogFilePublisher(String name, long maxSize, int nlimit) {
		try {
			this.logFile = new File(name).getCanonicalFile();
			this.lockFile = new File(this.logFile.getParent(), '.' + this.logFile.getName() + ".lock")
					.getCanonicalFile();
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
		this.maxSize = maxSize;
		this.nlimit = nlimit;

		String fileName = logFile.getName();
		Object mutex;
		synchronized (MUTEX) {
			mutex = MUTEX.get(fileName);
			if (mutex == null)
				MUTEX.put(fileName, mutex = new Object());
		}
		this.mutex = mutex;
	}

	private static final int BUFFER_SIZE = 16384;

	private class Plunger {
		long count;

		byte[] buffer = new byte[BUFFER_SIZE];
		int pos;

		byte[] overflow;
		int overflowOffset;

		private int available() {
			int available = BUFFER_SIZE - pos;
			long bytesRemainingInLogFile = maxSize - count;
			if (available > bytesRemainingInLogFile)
				return (int) bytesRemainingInLogFile;
			else
				return available;
		}

		private void buffer() {
			if (overflow != null) {
				int overflowRemaining = overflow.length - overflowOffset;
				int available = available();
				if (overflowRemaining > available) {
					System.arraycopy(overflow, overflowOffset, buffer, pos, available);

					pos += available;
					count += available;
					overflowOffset += available;

					return;
				} else {
					System.arraycopy(overflow, overflowOffset, buffer, pos, overflowRemaining);
					pos += overflowRemaining;
					count += overflowRemaining;

					overflow = null;
					overflowOffset = 0;
				}
			}

			while (!messages.isEmpty() && pos < BUFFER_SIZE && count < maxSize) {
				String message = messages.poll();
				if (message != null) {
					byte[] messageBytes = message.getBytes();
					int messageLength = messageBytes.length;

					int available = available();
					if (messageLength > available) {
						System.arraycopy(messageBytes, 0, buffer, pos, available);

						pos += available;
						count += available;

						overflow = messageBytes;
						overflowOffset = available;
						return;

					} else {
						System.arraycopy(messageBytes, 0, buffer, pos, messageLength);
						pos += messageLength;
					}
				}
			}
		}

		private void dump(OutputStream out) throws IOException {
			while (pos != 0) {
				out.write(buffer, 0, pos);
				pos = 0;
				buffer();
			}
		}

		private String unwritten() {
			StringBuilder sb = new StringBuilder();
			if (pos > 0)
				sb.append(new String(buffer, 0, pos));
			if (overflow != null)
				sb.append(new String(overflow, overflowOffset, overflow.length - overflowOffset));
			return sb.toString();
		}
	}

	public void flush() {
		synchronized (mutex) {
			Plunger plunger = new Plunger();

			plunger.count = logFile.exists() ? logFile.length() : 0L;
			if (plunger.count >= maxSize) {
				rotate();
				assert !logFile.exists() : logFile;
				plunger.count = 0;
			}

			plunger.buffer();
			if (plunger.pos == 0 && plunger.overflow == null)
				return;

			FileChannel lockChannel = null;
			FileLock lock = null;
			Throwable lastLockFailure = null;
			long lockTimeout = System.currentTimeMillis() + 2000L;
			while (lock == null && System.currentTimeMillis() < lockTimeout)
				try {
					lockChannel = FileChannel.open(lockFile.toPath(), StandardOpenOption.WRITE,
							StandardOpenOption.APPEND, StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);
					lock = lockChannel.tryLock();
				} catch (IOException | OverlappingFileLockException e) {
					lastLockFailure = e;
					try {
						mutex.wait(100L);
					} catch (InterruptedException e2) {
						e.addSuppressed(e2);
					}
				}

			if (lock == null) {
				System.err.println("Lock failed " + lockFile);
				if (lastLockFailure != null) {
					lastLockFailure.printStackTrace();
					System.err.println();
				}
			} else {
				try (OutputStream out = new FileOutputStream(logFile, true)) {
					plunger.dump(out);
				} catch (Throwable e) {
					handleFileWriteError("Log file write failure" + plunger.unwritten(), e);
				}

				try {
					lock.release();
					lockChannel.close();
				} catch (IOException e) {
					handleFileWriteError("Lock release failed " + lockFile, e);
				}
			}
		}

	}

	public void publish(String message) {
		messages.offer(message + '\n');
	}

}
