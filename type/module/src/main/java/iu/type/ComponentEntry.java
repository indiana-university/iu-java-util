package iu.type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class ComponentEntry implements AutoCloseable {

	@FunctionalInterface
	interface InputStreamConsumer {
		void accept(InputStream in) throws IOException;
	}

	private final String name;
	private final InputStream input;
	private boolean read;
	private byte[] data;
	private boolean closed;

	ComponentEntry(String name, InputStream input) {
		this.name = name;
		this.input = input;
	}

	String name() {
		if (closed)
			throw new IllegalStateException("closed");
		return name;
	}

	void read(InputStreamConsumer with) {
		if (closed)
			throw new IllegalStateException("closed");
		if (read)
			throw new IllegalStateException("already read");
		try {
			with.accept(input);
			read = true;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	byte[] data() {
		if (closed)
			throw new IllegalStateException("closed");
		if (data == null)
			read(in -> {
				byte[] buf = new byte[16384];
				int r;
				var out = new ByteArrayOutputStream();
				while ((r = input.read(buf, 0, buf.length)) > 0)
					out.write(buf, 0, r);
				data = out.toByteArray();
			});
		return data;
	}

	@Override
	public void close() {
		closed = true;
	}

	@Override
	public String toString() {
		return "ComponentEntry [name=" + name + ", read=" + read + (data == null ? "" : ", data=" + data.length + 'B')
				+ ", closed=" + closed + "]";
	}

}
