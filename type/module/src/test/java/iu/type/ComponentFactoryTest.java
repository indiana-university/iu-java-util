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
