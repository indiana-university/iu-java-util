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
import java.io.OutputStream;
import java.util.function.Supplier;

/**
 * Maintains a fixed buffer of streamed data, for normalizing the rate of output
 * and limiting output to a single source.
 */
public class IuFixedLimitOutputBuffer {

	private final byte[] buffer;
	private final long maxSize;
	private long count;

	private int mark;
	private int pos;
	private byte[] overflow;
	private int overflowOffset;

	/**
	 * Constructor.
	 * 
	 * @param maxSize maximum number of bytes to allow writing to the target
	 */
	public IuFixedLimitOutputBuffer(long maxSize) {
		this(0, maxSize);
	}

	/**
	 * Constructor.
	 * 
	 * @param count   number of bytes previously written to the target
	 * @param maxSize maximum number of bytes to allow writing to the target
	 */
	public IuFixedLimitOutputBuffer(long count, long maxSize) {
		this(16384, count, maxSize);
	}

	/**
	 * Constructor.
	 * 
	 * @param bufferSize fixed length buffer size
	 * @param count      number of bytes previously written to the target
	 * @param maxSize    maximum number of bytes to allow writing to the target
	 */
	public IuFixedLimitOutputBuffer(int bufferSize, long count, long maxSize) {
		this.buffer = new byte[bufferSize];
		this.count = count;
		this.maxSize = maxSize;
	}

	/**
	 * Resets the count of bytes written to the target.
	 */
	public void resetCount() {
		count = 0;
	}

	/**
	 * Gets the number of bytes remaining in the target output quota.
	 * 
	 * @return number of bytes remaining in the target output quota.
	 */
	public int remaining() {
		final var remaining = maxSize - count;
		if (remaining > Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		else
			return (int) remaining;
	}

	/**
	 * Writes to an {@link OutputStream}, as long as it source data is available, up
	 * to the {@link #IuFixedLimitOutputBuffer(long) max size}.
	 * 
	 * <p>
	 * This method returns when:
	 * </p>
	 * <ul>
	 * <li>{@link Supplier#get() dataSupplier.get()} returns null to indicate no
	 * more source data is available.</li>
	 * <li>The {@link #IuFixedLimitOutputBuffer(long) max size} has been reached.
	 * {@link #remaining()} will return 0 and no more data will be written from this
	 * buffer without first invoking {@link #resetCount()}.</li>
	 * </ul>
	 * 
	 * @param dataSupplier {@link Supplier} of source data
	 * @param out          {@link OutputStream}
	 * @throws IOException If an error occurs writing to the stream.
	 */
	public void write(Supplier<byte[]> dataSupplier, OutputStream out) throws IOException {
		int remaining;
		while ((pos > mark || //
				fill(dataSupplier)) && //
				(remaining = remaining()) > 0) {
			final var len = pos - mark;
			if (remaining < len) {
				out.write(buffer, mark, remaining);
				count += remaining;
				mark += remaining;
			} else {
				out.write(buffer, mark, len);
				count += len;
				mark = pos = 0;
			}
		}
	}

	/**
	 * Fills the buffer with source data.
	 * 
	 * @param dataSupplier {@link Supplier} of source data
	 * @return true if data was added to the buffer; else false
	 */
	public boolean fill(Supplier<byte[]> dataSupplier) {
		var available = available();
		if (available <= 0)
			return false;

		final var opos = pos;

		if (overflow != null) {
			final var overflowRemaining = overflow.length - overflowOffset;

			if (overflowRemaining > available) {
				// fill available space from overflow
				System.arraycopy(overflow, overflowOffset, buffer, pos, available);
				pos += available;

				// increment overflow offset for next iteration
				overflowOffset += available;
				return true;
			}

			// else copy all overflow bytes into the buffer
			System.arraycopy(overflow, overflowOffset, buffer, pos, overflowRemaining);
			pos += overflowRemaining;
			overflow = null;
			overflowOffset = 0;
		}

		byte[] data;
		while ((data = dataSupplier.get()) != null) {
			available = available();

			final var messageLength = data.length;
			if (messageLength > available) {
				// fill available space from data
				System.arraycopy(data, 0, buffer, pos, available);
				pos += available;

				// copy data reference as overflow buffer and set initial offset
				overflow = data;
				overflowOffset = available;
				return true;
			}

			// copy data into buffer and increment position
			System.arraycopy(data, 0, buffer, pos, messageLength);
			pos += messageLength;
		}

		return pos > opos;
	}

	/**
	 * Gets the number of bytes available in the buffer.
	 * 
	 * @return number of bytes available in the buffer.
	 */
	int available() {
		return buffer.length - pos;
	}

}
