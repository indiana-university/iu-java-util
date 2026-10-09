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

import java.util.EnumMap;
import java.util.Map;

import edu.iu.client.IuJsonPropertyNameFormat;

/**
 * Names Java properties in JSON, and matches JSON names when reading.
 */
public interface PropertyNaming {

	/**
	 * Gets the naming for an IU property name format.
	 *
	 * @param format property name format
	 * @return {@link PropertyNaming}
	 */
	static PropertyNaming of(IuJsonPropertyNameFormat format) {
		return Formats.FORMATS.get(format);
	}

	/**
	 * Names a property in JSON.
	 *
	 * @param javaName Java property name
	 * @return JSON property name
	 */
	String name(String javaName);

	/**
	 * Gets the key a JSON property name is matched by when reading.
	 *
	 * @param jsonName JSON property name
	 * @return the name itself, unless matching ignores case
	 */
	default String key(String jsonName) {
		return jsonName;
	}

}

/**
 * Holds the naming for each IU property name format.
 */
final class Formats {
	/**
	 * Naming by format.
	 */
	static final Map<IuJsonPropertyNameFormat, PropertyNaming> FORMATS = new EnumMap<>(
			IuJsonPropertyNameFormat.class);

	static {
		for (final var format : IuJsonPropertyNameFormat.values())
			FORMATS.put(format, name -> JsonSerializer.formatPropertyName(name, format));
	}

	private Formats() {
	}
}
