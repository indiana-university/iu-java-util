/*
 * Copyright © 2026 Indiana University
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import edu.iu.IuException;
import edu.iu.UnsafeConsumer;

/**
 * Represents an entry in the component archive.
 */
class ComponentEntry implements AutoCloseable {

	private final String name;
	private final InputStream input;
	private boolean read;
	private byte[] data;
	private boolean closed;

	/**
	 * Constructor use only from {@link ArchiveSource}.
	 * 
	 * @param name  entry name
	 * @param input entry input stream
	 */
	ComponentEntry(String name, InputStream input) {
		this.name = name;
		this.input = input;
	}

	/**
	 * Gets the entry name.
	 * 
	 * @return entry name
	 */
	String name() {
		if (closed)
			throw new IllegalStateException("closed");
		return name;
	}

	/**
	 * Reads raw data from the archive with externally provided logic.
	 * 
	 * <p>
	 * Either may only be called once, and must not be invoked after
	 * {@link #data()}. Will cause subsequent calls to {@link #data()} to throw
	 * {@link IllegalStateException}.
	 * </p>
	 * 
	 * @param with input handling logic
	 */
	void read(UnsafeConsumer<InputStream> with) {
		if (closed)
			throw new IllegalStateException("closed");

		if (read)
			throw new IllegalStateException("already read");

		IuException.unchecked(() -> {
			with.accept(input);
			read = true;
		});
	}

	/**
	 * Reads and buffers raw data.
	 * 
	 * <p>
	 * May be invoked multiple times, but not if {@link #read(UnsafeConsumer)} is
	 * invoked first. The result of first invocation will be returned by all
	 * subsequent invocations.
	 * </p>
	 * 
	 * @return raw data
	 */
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
