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
package iu.type.jsonb;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;

import edu.iu.type.IuType;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import jakarta.json.spi.JsonProvider;

/**
 * Converts Java objects to and from JSON.
 * 
 * @see Jsonb
 */
class IuJsonb implements Jsonb {

	private final IuJsonbConfig config;
	private final JsonProvider jsonpProvider;

	/**
	 * Constructor.
	 * 
	 * @param config        {@link IuJsonbConfig}
	 * @param jsonpProvider {@link JsonProvider}
	 */
	IuJsonb(IuJsonbConfig config, JsonProvider jsonpProvider) {
		this.config = config;
		this.jsonpProvider = jsonpProvider;
	}

	@Override
	public <T> T fromJson(String str, Class<T> type) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public <T> T fromJson(String str, Type runtimeType) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public <T> T fromJson(Reader reader, Class<T> type) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public <T> T fromJson(Reader reader, Type runtimeType) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public <T> T fromJson(InputStream stream, Class<T> type) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public <T> T fromJson(InputStream stream, Type runtimeType) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String toJson(Object object) throws JsonbException {
		return toJson(object, object.getClass());
	}

	@Override
	public String toJson(Object object, Type runtimeType) throws JsonbException {
		final var sw = new StringWriter();
		toJson(object, runtimeType, sw);
		return sw.toString();
	}

	@Override
	public void toJson(Object object, Writer writer) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void toJson(Object object, Type runtimeType, Writer writer) throws JsonbException {
		final var rootBuilder = new JsonValueBuilder(jsonpProvider);

		Deque<PendingToJsonValue<?>> buildStack = new ArrayDeque<>();
		buildStack.push(new PendingToJsonValue<>(jsonpProvider, rootBuilder, IuType.of(runtimeType), object));
		while (!buildStack.isEmpty())
			buildStack.pop().apply(config).forEach(buildStack::push);

		final var jsonpWriter = jsonpProvider.createWriterFactory(config.getWriterProperties()).createWriter(writer);
		if (rootBuilder.value.isEmpty())
			jsonpWriter.write(JsonValue.NULL);
		else
			jsonpWriter.write(rootBuilder.value.get());
	}

	@Override
	public void toJson(Object object, OutputStream stream) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void toJson(Object object, Type runtimeType, OutputStream stream) throws JsonbException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
