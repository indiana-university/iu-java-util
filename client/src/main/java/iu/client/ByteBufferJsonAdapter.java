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
package iu.client;

import java.nio.ByteBuffer;

import edu.iu.client.IuJsonAdapter;
import jakarta.json.JsonValue;

/**
 * Implements {@link IuJsonAdapter} for {@link ByteBuffer}
 */
class ByteBufferJsonAdapter implements IuJsonAdapter<ByteBuffer> {

	/**
	 * Singleton instance.
	 */
	static final ByteBufferJsonAdapter INSTANCE = new ByteBufferJsonAdapter();

	private ByteBufferJsonAdapter() {
	}

	@Override
	public ByteBuffer fromJson(JsonValue value) {
		final var data = BinaryJsonAdapter.INSTANCE.fromJson(value);
		if (data == null)
			return null;
		else
			return ByteBuffer.wrap(data);
	}

	@Override
	public JsonValue toJson(ByteBuffer value) {
		if (value == null || !value.hasRemaining())
			return JsonValue.NULL;

		final byte[] data;
		if (value.hasArray() //
				&& value.arrayOffset() == 0 //
				&& value.limit() == value.capacity()) {
			value.position(value.limit());
			data = value.array();
		} else {
			data = new byte[value.remaining()];
			value.get(data);
		}

		return BinaryJsonAdapter.INSTANCE.toJson(data);
	}

}
