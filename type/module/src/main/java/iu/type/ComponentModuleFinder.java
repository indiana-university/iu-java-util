/*
 * Copyright © 2023 Indiana University
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

import edu.iu.IuException;
import edu.iu.UnsafeSupplier;

class ComponentModuleFinder implements ModuleFinder, AutoCloseable {

	private class Ref extends ModuleReference implements AutoCloseable {

		private UnsafeSupplier<ModuleReader> readerSupplier;
		private ModuleReader reader;
		private boolean closed;

		protected Ref(ModuleDescriptor descriptor, URI location, UnsafeSupplier<ModuleReader> readerSupplier) {
			super(descriptor, location);
			this.readerSupplier = readerSupplier;
		}

		@Override
		public synchronized ModuleReader open() throws IOException {
			if (closed)
				throw new IllegalStateException();
			if (reader == null) {
				reader = IuException.checked(IOException.class, readerSupplier);
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
