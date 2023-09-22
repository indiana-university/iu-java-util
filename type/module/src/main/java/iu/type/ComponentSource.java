package iu.type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import iu.type.ComponentEntry.Input;

class ComponentSource implements Iterator<ComponentEntry>, AutoCloseable {

	private final InputStream in;
	private final JarInputStream jar;
	private final Manifest manifest;
	private byte[] buf = new byte[16384];
	private Optional<ComponentEntry> next;
	private ComponentEntry last;
	private boolean closed;

	ComponentSource(InputStream in) throws IOException {
		this.in = in;
		try {
			jar = new JarInputStream(in);
			manifest = Objects.requireNonNull(jar.getManifest(), "Missing META-INF/MANIFEST.MF");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	String name() {
		return manifest.getMainAttributes().getValue("Extension-Name");
	}

	String version() {
		return manifest.getMainAttributes().getValue("Implementation-Version");
	}

	Iterable<String> classPath() {
		var classPathAttribute = manifest.getMainAttributes().getValue("Class-Path");
		if (classPathAttribute == null)
			return Set.of();
		else
			return List.of(classPathAttribute.split(" "));
	}

	Iterable<ComponentDependency> dependencies() {
		var attributes = manifest.getMainAttributes();
		var extensionListAttribute = attributes.getValue("Extension-List");
		if (extensionListAttribute == null)
			return Set.of();
		else {
			Queue<ComponentDependency> dependencies = new ArrayDeque<>();
			for (var extension : List.of(extensionListAttribute.split(" "))) {
				extension = extension.replace('.', '_');
				var name = Objects.requireNonNull(attributes.getValue(extension + "-Extension-Name"));
				var version = Objects.requireNonNull(attributes.getValue(extension + "-Implementation-Version"));
				dependencies.offer(new ComponentDependency(name, version));
			}
			return dependencies;
		}
	}

	@Override
	public boolean hasNext() {
		if (closed)
			return false;

		if (next == null)
			try {
				if (last != null) {
					last.close();
					last = null;
				}

				JarEntry jarEntry = jar.getNextJarEntry();
				if (jarEntry == null) {
					close();
					return false;
				}

				next = Optional.of(new ComponentEntry(jarEntry, new Input() {
					@Override
					public byte[] read() throws IOException {
						int r;
						var out = new ByteArrayOutputStream();
						while ((r = jar.read(buf, 0, buf.length)) > 0)
							out.write(buf, 0, r);
						return out.toByteArray();
					}
				}));

			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

		return true;
	}

	@Override
	public ComponentEntry next() {
		if (!hasNext())
			throw new NoSuchElementException();

		last = next.get();
		next = null;

		return last;
	}

	@Override
	public void close() {
		if (!closed) {
			try {
				jar.close();
				in.close();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

			if (next != null) {
				next.get().close();
				next = null;
			}

			if (last != null) {
				last.close();
				last = null;
			}

			buf = null;
			closed = true;
		}
	}

}
