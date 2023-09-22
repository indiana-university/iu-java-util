package iu.type;

import java.io.IOException;
import java.util.jar.JarEntry;

class ComponentEntry implements AutoCloseable {

	@FunctionalInterface
	interface Input {
		byte[] read() throws IOException;
	}

	private final JarEntry entry;
	private final Input input;
	private byte[] data;
	private boolean closed;

	ComponentEntry(JarEntry entry, Input input) {
		this.entry = entry;
		this.input = input;
	}

	String name() {
		if (closed)
			throw new IllegalStateException("closed");
		return entry.getName();
	}

	byte[] data() {
		if (closed)
			throw new IllegalStateException("closed");
		if (data == null)
			try {
				data = input.read();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		return data;
	}

	@Override
	public void close() {
		closed = true;
	}

}
