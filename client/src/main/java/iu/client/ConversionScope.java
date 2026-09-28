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
package iu.client;

import java.lang.reflect.Type;
import java.util.function.Function;
import java.util.function.Supplier;

import edu.iu.client.IuJsonAdapter;

/**
 * Tracks the conversions of the JSON-B call in progress on each thread, for a
 * conversion not bound to conversions of its own, such as an
 * {@link edu.iu.client.IuJsonProperties} created without them.
 */
public final class ConversionScope {

	private static final ThreadLocal<Function<Type, IuJsonAdapter<?>>> CURRENT = new ThreadLocal<>();

	private ConversionScope() {
	}

	/**
	 * Gets the conversions of the call in progress on the current thread.
	 *
	 * @return gets the conversion for a type; null if no call is in progress
	 */
	public static Function<Type, IuJsonAdapter<?>> current() {
		return CURRENT.get();
	}

	/**
	 * Runs a conversion with a call's conversions in progress on the current
	 * thread, then restores those of the call it's nested in, if any.
	 *
	 * @param <R>         result type
	 * @param conversions gets the conversion for a type
	 * @param conversion  conversion
	 * @return result
	 */
	public static <R> R within(Function<Type, IuJsonAdapter<?>> conversions, Supplier<R> conversion) {
		final var previous = CURRENT.get();
		CURRENT.set(conversions);
		try {
			return conversion.get();
		} finally {
			if (previous == null)
				CURRENT.remove();
			else
				CURRENT.set(previous);
		}
	}

}
