/*
 * Copyright © 2024 Indiana University
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
package edu.iu;

import java.io.IOException;
import java.io.Writer;

/**
 * {@link Writer} that buffers all incoming text until attached to a downstream
 * delegate.
 * 
 * <p>
 * This class is thread-safe and intended for use in asynchronous operations.
 * </p>
 */
public class DetachedWriter extends Writer {

	private StringBuffer buffer = new StringBuffer();
	private Writer delegate;
	private boolean closed;

	/**
	 * Default constructor.
	 */
	public DetachedWriter() {
	}

	/**
	 * Writes all buffered content, and if closed closes, a deferred delegate
	 * {@link Writer}.
	 * 
	 * @param delegate deferred delegate
	 * @throws IOException If an error occurs passing control to the delegate
	 */
	public synchronized void attach(Writer delegate) throws IOException {
		if (buffer == null)
			throw new IllegalStateException("Already attached");

		final var buffer = this.buffer;
		this.buffer = null;

		delegate.append(buffer);
		delegate.flush();
		if (closed)
			delegate.close();
		else
			this.delegate = delegate;
	}

	/**
	 * Gets buffered text, <em>may</em> be called before or instead of
	 * {@link #attach(Writer)}.
	 * 
	 * @return buffered text
	 */
	public synchronized CharSequence text() {
		if (buffer == null)
			throw new IllegalStateException("Attached");

		return buffer.toString();
	}

	@Override
	public synchronized void write(char[] cbuf, int off, int len) throws IOException {
		if (closed)
			throw new IOException("closed");

		if (buffer != null)
			buffer.append(cbuf, off, len);
		else
			delegate.write(cbuf, off, len);
	}

	@Override
	public synchronized void flush() throws IOException {
		if (delegate != null)
			delegate.flush();
	}

	@Override
	public synchronized void close() throws IOException {
		closed = true;
		buffer.trimToSize();

		if (delegate != null) {
			delegate.close();
			delegate = null;
		}
	}

}
