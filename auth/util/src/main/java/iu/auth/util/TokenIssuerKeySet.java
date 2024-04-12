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
package iu.auth.util;

import java.util.Objects;
import java.util.Set;

import edu.iu.crypt.WebKey;

/**
 * Encapsulates a token issuer's key set.
 */
public class TokenIssuerKeySet implements WebKeyFactory {

	private final Set<WebKey> providerKeys;

	/**
	 * Constructor.
	 * 
	 * @param providerKeys provider key set
	 */
	public TokenIssuerKeySet(Set<WebKey> providerKeys) {
		this.providerKeys = providerKeys;
		for (final var providerKey : providerKeys) {
			Objects.requireNonNull(providerKey.getKeyId(), "id");
			Objects.requireNonNull(providerKey.getType(), "type");
			Objects.requireNonNull(providerKey.getUse(), "usage");
			Objects.requireNonNull(providerKey.getPublicKey(), "public");
			Objects.requireNonNull(providerKey.getPrivateKey(), "private");
		}
	}

	@Override
	public WebKey getKey(String keyId) {
		return providerKeys.stream().filter(k -> keyId.equals(k.getKeyId())).findFirst().get();
	}
}
