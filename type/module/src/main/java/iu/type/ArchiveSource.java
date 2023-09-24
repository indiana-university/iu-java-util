package iu.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

class ArchiveSource implements AutoCloseable {

	private final InputStream in;
	private final JarInputStream jar;
	private final String name;
	private final String version;
	private final boolean sealed;
	private final Iterable<String> classPath;
	private final Iterable<ComponentDependency> dependencies;
	private Optional<ComponentEntry> next;
	private ComponentEntry last;
	private boolean closed;

	ArchiveSource(ComponentEntry componentEntry) throws IOException {
		this(new ByteArrayInputStream(componentEntry.data()));
	}

	ArchiveSource(InputStream in) throws IOException {
		this.in = in;
		jar = new JarInputStream(in);

		var manifest = jar.getManifest();
		if (manifest == null)
			throw new IllegalArgumentException("Missing META-INF/MANIFEST.MF");

		var attributes = manifest.getMainAttributes();
		if (attributes.getValue(Name.MANIFEST_VERSION) == null)
			throw new IllegalArgumentException(
					"Missing " + Name.MANIFEST_VERSION + " attribute in META-INF/MANIFEST.MF");

		name = attributes.getValue(Name.EXTENSION_NAME);
		version = attributes.getValue(Name.IMPLEMENTATION_VERSION);
		sealed = "true".equals(attributes.getValue(Name.SEALED));

		var classPathAttribute = manifest.getMainAttributes().getValue(Name.CLASS_PATH);
		if (classPathAttribute == null)
			classPath = Set.of();
		else
			classPath = List.of(classPathAttribute.split(" "));

		var extensionListAttribute = attributes.getValue(Name.EXTENSION_LIST);
		if (extensionListAttribute == null)
			dependencies = Set.of();
		else {
			Queue<ComponentDependency> dependencies = new ArrayDeque<>();
			for (var extension : List.of(extensionListAttribute.split(" "))) {
				extension = extension.replace('.', '_');
				var name = Objects.requireNonNull(attributes.getValue(extension + '-' + Name.EXTENSION_NAME));
				var version = Objects
						.requireNonNull(attributes.getValue(extension + '-' + Name.IMPLEMENTATION_VERSION));
				dependencies.add(new ComponentDependency(name, version));
			}
			this.dependencies = dependencies;
		}
	}

	boolean sealed() {
		return sealed;
	}

	String name() {
		return name;
	}

	String version() {
		return version;
	}

	Iterable<String> classPath() {
		return classPath;
	}

	Iterable<ComponentDependency> dependencies() {
		return dependencies;
	}

	boolean hasNext() throws IOException {
		if (closed)
			return false;

		if (next == null) {
			if (last != null) {
				jar.closeEntry();
				last.close();
				last = null;
			}

			JarEntry jarEntry = jar.getNextJarEntry();
			if (jarEntry == null) {
				close();
				return false;
			}

			next = Optional.of(new ComponentEntry(jarEntry.getName(), jar));
		}

		return true;
	}

	ComponentEntry next() throws IOException {
		if (!hasNext())
			throw new NoSuchElementException();

		last = next.get();
		next = null;

		return last;
	}

	@Override
	public void close() throws IOException {
		if (!closed) {
			jar.close();
			in.close();

			if (next != null) {
				next.get().close();
				next = null;
			}

			if (last != null) {
				last.close();
				last = null;
			}

			closed = true;
		}
	}

}
