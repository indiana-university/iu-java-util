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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ArchiveSource implements AutoCloseable {

	private final InputStream in;
	private final JarInputStream jar;
	private final boolean sealed;
	private final List<String> classPath;
	private final List<ComponentVersion> dependencies;
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

		sealed = "true".equals(attributes.getValue(Name.SEALED));

		var classPathAttribute = manifest.getMainAttributes().getValue(Name.CLASS_PATH);
		if (classPathAttribute == null)
			classPath = List.of();
		else
			classPath = List.of(classPathAttribute.split(" "));

		var extensionListAttribute = attributes.getValue(Name.EXTENSION_LIST);
		if (extensionListAttribute == null)
			dependencies = List.of();
		else
			this.dependencies = Stream.of(extensionListAttribute.split(" "))
					.map(extension -> new ComponentVersion(extension, attributes)).collect(Collectors.toList());
	}

	boolean sealed() {
		return sealed;
	}

	List<String> classPath() {
		return classPath;
	}

	List<ComponentVersion> dependencies() {
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

	@Override
	public String toString() {
		return "ArchiveSource [sealed=" + sealed + ", classPath=" + classPath + ", dependencies=" + dependencies
				+ (next == null ? "" : ", next=" + next) + (last == null ? "" : ", last=" + last) + ", closed=" + closed
				+ "]";
	}

}
