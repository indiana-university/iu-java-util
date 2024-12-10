package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuFixedLimitOutputBufferTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testNothingToDo() {
		final var buf = new IuFixedLimitOutputBuffer(32);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn((byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertEquals(32, buf.remaining());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testExactFit() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(data.length);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn(data, (byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(data, out.toByteArray());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testOverflow() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(8, data.length);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn(data, (byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(Arrays.copyOf(data, 24), out.toByteArray());
		assertEquals(0, buf.remaining());

		out.reset();
		buf.resetCount();
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(Arrays.copyOfRange(data, 24, 32), out.toByteArray());
		assertEquals(24, buf.remaining());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testUndersized() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(8, 0, data.length);
		final var out = new ByteArrayOutputStream();
		final Supplier<byte[]> dataSupplier = mock(Supplier.class);
		when(dataSupplier.get()).thenReturn(data, (byte[]) null);
		assertDoesNotThrow(() -> buf.write(dataSupplier, out));
		assertArrayEquals(data, out.toByteArray());
	}

	@Test
	public void testFillAvailable() {
		final var data = IuText.utf8(IdGenerator.generateId());
		final var buf = new IuFixedLimitOutputBuffer(8, 0, data.length);
		buf.fill(() -> data);
		assertEquals(0, buf.available());
		assertDoesNotThrow(() -> buf.fill(() -> null));
	}

	@Test
	public void testBigRemaining() {
		final var buf = new IuFixedLimitOutputBuffer(((long) Integer.MAX_VALUE) + 1);
		assertEquals(Integer.MAX_VALUE, buf.remaining());
	}

	@Test
	public void testBulk() {
		final var control = new ByteArrayOutputStream();
		final Queue<byte[]> queue = new ConcurrentLinkedDeque<>();
		final var feed = new Thread() {
			volatile boolean active = true;

			@Override
			public void run() {
				final var rand = new Random();
				while (active)
					synchronized (this) {
						IuException.unchecked(() -> {
							final var length = rand.nextInt(16382) + 2;
							final var data = new byte[length];
							rand.nextBytes(data);
							control.write(data);
							queue.offer(data);
							this.wait(100L);
						});
					}
			}
		};
		feed.start();

		final var out = new ByteArrayOutputStream();
		final var buf = new IuFixedLimitOutputBuffer(4096);
		for (var i = 0; i < 20; i++) {
			while (buf.remaining() > 0) {
				assertDoesNotThrow(() -> buf.write(queue::poll, out));
				IuException.unchecked(() -> Thread.sleep(50L));
			}
			assertEquals(4096, out.size());
			assertArrayEquals(Arrays.copyOfRange(control.toByteArray(), i * 4096, (i + 1) * 4096), out.toByteArray());

			out.reset();
			buf.resetCount();
		}

		synchronized (feed) {
			feed.active = false;
			feed.notifyAll();
		}
	}

}
