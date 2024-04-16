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
package iu.crypt;

import java.net.URI;
import java.util.Set;

import edu.iu.IuObject;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonBuilder;
import edu.iu.crypt.WebCryptoHeader.Builder;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;

/**
 * Builds a web signature or encryption header.
 * 
 * @param <B> builder type
 */
class JoseBuilder<B extends JoseBuilder<B>> extends KeyReferenceBuilder<B> implements Builder<B> {

	private Jwk key;

	/**
	 * Constructor.
	 * 
	 * @param algorithm algorithm
	 */
	protected JoseBuilder(Algorithm algorithm) {
		algorithm(algorithm);
	}

	@Override
	protected <S extends IuJsonBuilder<S>> B copy(S builder) {
		key(((JoseBuilder<?>) builder).key());
		return super.copy(builder);
	}

	@Override
	public B wellKnown(URI uri) {
		return param(Param.KEY_SET_URI, uri);
	}

	@Override
	public B wellKnown(WebKey key) {
		key(key);
		return param(Param.KEY, key.wellKnown());
	}

	@Override
	@SuppressWarnings("unchecked")
	public B key(WebKey key) {
		this.key = IuObject.once(this.key, (Jwk) key);
		return (B) this;
	}

	@Override
	public B type(String type) {
		return param(Param.TYPE, type);
	}

	@Override
	public B contentType(String contentType) {
		return param(Param.CONTENT_TYPE, contentType);
	}

	@Override
	public B crit(String... name) {
		for (final var paramName : name)
			if (Param.from(paramName) == null)
				Jose.getExtension(paramName);
		return param(Param.CRITICAL_PARAMS, Set.of(name));
	}

	@Override
	public <T> B param(Param param, T value) {
		return super.param(param.name, value, param.json());
	}

	@Override
	public <T> B param(String paramName, T value) {
		final var ext = Jose.getExtension(paramName);
		ext.validate(value, this);
		return super.param(paramName, value, ext);
	}

	@Override
	protected <T> B param(String name, T value, IuJsonAdapter<T> adapter) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the key to use for signing or encryption.
	 * 
	 * @return signing/encryption key
	 */
	protected Jwk key() {
		return key;
	}
}
