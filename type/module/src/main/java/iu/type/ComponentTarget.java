package iu.type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class ComponentTarget implements AutoCloseable {

	private final OutputStream out;
	private final JarOutputStream jar;
	private final byte[] buf = new byte[16384];

	ComponentTarget(Path path) throws IOException {
		out = Files.newOutputStream(path);
		jar = new JarOutputStream(out);
	}

	void put(String name, InputStream data) throws IOException {
		int r;
		jar.putNextEntry(new JarEntry(name));
		while ((r = data.read(buf, 0, buf.length)) > 0)
			jar.write(buf, 0, r);
		jar.closeEntry();
	}

	@Override
	public void close() throws IOException {
		jar.close();
		out.close();
	}

}
