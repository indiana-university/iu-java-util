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

import edu.iu.IuException;

/**
 * Determines, once, whether the JSON-B API is present, and so which
 * {@link BindingMetadata} applies.
 */
final class JsonbPresence {

	/**
	 * JSON-B API module name.
	 */
	static final String JSONB = "jakarta.json.bind";

	/**
	 * Metadata for the runtime.
	 */
	static final BindingMetadata METADATA = metadata(JsonbPresence.class.getModule(), JSONB);

	/**
	 * Determines which metadata applies to a module.
	 *
	 * <p>
	 * {@code requires static} resolves the JSON-B module only when something
	 * else in the layer requires it, so its presence is the module reading it.
	 * The class that reads the annotations loads only then.
	 * </p>
	 *
	 * @param module module converting business objects
	 * @param jsonb  JSON-B API module name
	 * @return JSON-B annotation metadata if the module reads the JSON-B API;
	 *         otherwise {@link BindingMetadata#NONE}
	 */
	static BindingMetadata metadata(Module module, String jsonb) {
		final var layer = module.getLayer();
		if (layer == null)
			return BindingMetadata.NONE;

		final var api = layer.findModule(jsonb);
		if (api.isEmpty() || !module.canRead(api.get()))
			return BindingMetadata.NONE;

		return (BindingMetadata) IuException.unchecked(() -> Class.forName("iu.client.jsonb.JsonbMetadata")
				.getField("INSTANCE").get(null));
	}

	private JsonbPresence() {
	}

}
