/*
 * Copyright © 2025 Indiana University
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
package edu.iu.client;

import jakarta.json.JsonObject;

/**
 * Represents a HashiCorp Vault K/V secret.
 */
public interface IuVaultSecret {

	/**
	 * Gets the secret name.
	 * 
	 * @return secret name
	 */
	String getName();

	/**
	 * Gets secret data as a JSON object.
	 * 
	 * @return {@link JsonObject}
	 */
	JsonObject getData();

	/**
	 * Gets K/V secret metadata.
	 * 
	 * @return {@link IuVaultMetadata}
	 */
	IuVaultMetadata getMetadata();

	/**
	 * Gets a keyed value.
	 * 
	 * @param <T>  value type
	 * @param key  key
	 * @param type type
	 * @return keyed value
	 */
	<T> IuVaultKeyedValue<T> get(String key, Class<T> type);

	/**
	 * Sets a keyed value.
	 * 
	 * @param <T>   value type
	 * @param key   key
	 * @param value value
	 * @param type  type
	 */
	<T> void set(String key, T value, Class<T> type);

}
