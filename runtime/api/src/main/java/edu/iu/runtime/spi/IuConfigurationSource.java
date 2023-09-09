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
package edu.iu.runtime.spi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import edu.iu.runtime.IuRuntime;

/**
 * Provides configuration file formats to {@link IuRuntime}.
 * 
 * <p>
 * Regardless of source format, implementors must provide data in a format
 * understood by {@link IuRuntime}. The default implementation understands
 * Jakarta JSON-P JsonObject when a provider is available, and {@link Map
 * Map&lt;String, String&gt;}.
 * </p>
 * 
 * <p>
 * May be provided by a application or third-party module to support
 * configuration formats (i.e., YAML) not supported natively by the default
 * implementation.
 * </p>
 */
public interface IuConfigurationSource {

	/**
	 * Returns the file extensions understood by the implementation.
	 * 
	 * @return Mapping from file extension to parsed data type.
	 */
	Map<String, Class<?>> getSupportedFileExtensions();

	/**
	 * Reads raw configuration data from an input stream and parsed to a supported
	 * format.
	 * 
	 * @param <T>    parsed data type
	 * @param type   parsed data type
	 * @param source {@link InputStream}
	 * @return parsed data
	 * @throws IOException from {@link InputStream}. Implementations should handle
	 *                     all other checked exceptions.
	 */
	<T> T read(Class<T> type, InputStream source) throws IOException;

}
