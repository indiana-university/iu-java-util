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

import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.client.IuVaultKeyedValue;
import edu.iu.client.IuVaultSecret;

/**
 * Basic {@link IuVaultKeyedValue} implementation.
 * 
 * @param <T> value type
 */
final class VaultKeyedValue<T> implements IuVaultKeyedValue<T> {

	private final VaultSecret secret;
	private final String key;
	private final T value;
	private final Class<T> type;

	/**
	 * Constructor.
	 * 
	 * @param secret {@link VaultSecret}
	 * @param key    key
	 * @param value  value
	 * @param type   type
	 */
	VaultKeyedValue(VaultSecret secret, String key, T value, Class<T> type) {
		this.secret = secret;
		this.key = key;
		this.value = value;
		this.type = type;
	}

	@Override
	public IuVaultSecret getSecret() {
		return secret;
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public T getValue() {
		return value;
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	@Override
	public String toString() {
		return "VaultKeyedValue [" + key + "@" + secret + "]";
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(key, secret, type, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		final var other = (VaultKeyedValue<?>) obj;
		return IuObject.equals(key, other.key) //
				&& Objects.equals(secret, other.secret) //
				&& Objects.equals(type, other.type) //
				&& Objects.equals(value, other.value);
	}

}
