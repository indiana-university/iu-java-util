package iu.type;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closeable module finder.
 */
class ComponentModuleFinder implements ModuleFinder, AutoCloseable {

	@FunctionalInterface
	private interface ReaderSupplier {
		ModuleReader get() throws IOException;
	}

	private class Ref extends ModuleReference implements AutoCloseable {

		private ReaderSupplier readerSupplier;
		private ModuleReader reader;
		private boolean closed;

		protected Ref(ModuleDescriptor descriptor, URI location, ReaderSupplier readerSupplier) {
			super(descriptor, location);
			this.readerSupplier = readerSupplier;
		}

		@Override
		public synchronized ModuleReader open() throws IOException {
			if (closed)
				throw new IllegalStateException();
			if (reader == null) {
				reader = readerSupplier.get();
				readerSupplier = null;
			}
			return reader;
		}

		@Override
		public void close() throws IOException {
			if (reader != null) {
				reader.close();
				reader = null;
			}
			if (readerSupplier != null)
				readerSupplier = null;
			closed = true;
		}
	}

	private Map<String, Ref> refs = new LinkedHashMap<>();

	ComponentModuleFinder(Path... path) {
		var finder = ModuleFinder.of(path);
		for (var ref : finder.findAll()) {
			var descriptor = ref.descriptor();
			refs.put(descriptor.name(), new Ref(descriptor, ref.location().get(), ref::open));
		}
	}

	@Override
	public Optional<ModuleReference> find(String name) {
		if (refs == null)
			throw new IllegalStateException("closed");
		return Optional.ofNullable(refs.get(name));
	}

	@Override
	public Set<ModuleReference> findAll() {
		if (refs == null)
			throw new IllegalStateException("closed");
		return refs.values().stream().collect(Collectors.toSet());
	}

	@Override
	public void close() throws IOException {
		Map<String, Ref> refs;
		synchronized (this) {
			refs = this.refs;
			if (refs != null)
				this.refs = null;
		}

		if (refs != null)
			for (var ref : refs.values())
				ref.close();
	}

}
