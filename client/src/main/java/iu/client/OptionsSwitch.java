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

import java.util.function.Predicate;
import java.util.function.Supplier;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonSerializationOptions;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

/**
 * Converts by one of two conversions, as the options in effect for each
 * conversion say.
 */
public final class OptionsSwitch implements IuJsonAdapter<Object> {

	private final Supplier<IuJsonSerializationOptions> options;
	private final Predicate<IuJsonSerializationOptions> test;
	private final IuJsonAdapter<Object> whenTrue;
	private final IuJsonAdapter<Object> whenFalse;

	/**
	 * Gets a conversion that switches by the options in effect.
	 *
	 * @param options   supplies the options in effect for each conversion
	 * @param test      selects a conversion from the options
	 * @param whenTrue  conversion when the test passes
	 * @param whenFalse conversion otherwise
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	public static IuJsonAdapter<?> of(Supplier<IuJsonSerializationOptions> options,
			Predicate<IuJsonSerializationOptions> test, IuJsonAdapter<?> whenTrue, IuJsonAdapter<?> whenFalse) {
		return new OptionsSwitch(options, test, (IuJsonAdapter<Object>) whenTrue, (IuJsonAdapter<Object>) whenFalse);
	}

	private OptionsSwitch(Supplier<IuJsonSerializationOptions> options, Predicate<IuJsonSerializationOptions> test,
			IuJsonAdapter<Object> whenTrue, IuJsonAdapter<Object> whenFalse) {
		this.options = options;
		this.test = test;
		this.whenTrue = whenTrue;
		this.whenFalse = whenFalse;
	}

	private IuJsonAdapter<Object> adapter() {
		return test.test(JsonSerializer.snapshot(options)) ? whenTrue : whenFalse;
	}

	@Override
	public Object fromJson(JsonValue jsonValue) {
		return adapter().fromJson(jsonValue);
	}

	@Override
	public JsonValue toJson(Object javaValue) {
		return adapter().toJson(javaValue);
	}

	@Override
	public Object read(JsonParser parser) {
		return adapter().read(parser);
	}

	@Override
	public void write(Object value, JsonGenerator generator) {
		adapter().write(value, generator);
	}

}
