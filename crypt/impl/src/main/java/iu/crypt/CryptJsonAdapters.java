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

import java.math.BigInteger;
import java.net.URI;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Set;

import edu.iu.IuException;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import jakarta.json.JsonString;

/**
 * Provides {@link IuJsonAdapter} instances for converting Web Crypto and PEM
 * formats to and from JSON.
 */
public class CryptJsonAdapters {

	private CryptJsonAdapters() {
	}

	/**
	 * JSON type adapter for byte[] using Base64URL encoding with no padding.
	 */
	public static final IuJsonAdapter<byte[]> B64URL = IuJsonAdapter.text(IuText::base64Url, IuText::base64Url);

	/**
	 * Singleton.
	 */
	public static final IuJsonAdapter<BigInteger> BIGINT = IuJsonAdapter.text(
			v -> UnsignedBigInteger.bigInt(IuText.base64Url(v)), v -> IuText.base64Url(UnsignedBigInteger.bigInt(v)));

	/**
	 * JSON type adapter for {@link X509Certificate}.
	 */
	public static final IuJsonAdapter<X509Certificate> CERT = IuJsonAdapter.from(
			a -> PemEncoded.asCertificate(IuText.base64(((JsonString) a).getString())),
			a -> IuJson.string(IuText.base64(IuException.unchecked(a::getEncoded))));

	/**
	 * JSON type adapter for {@link X509CRL}.
	 */
	public static final IuJsonAdapter<X509CRL> CRL = IuJsonAdapter.text(a -> PemEncoded.asCRL(IuText.base64(a)),
			a -> IuText.base64(IuException.unchecked(a::getEncoded)));

	/**
	 * JSON type adapter for {@link Use}.
	 */
	public static final IuJsonAdapter<Use> USE = IuJsonAdapter.text(Use::from, u -> u.use);

	/**
	 * JSON type adapter for {@link Operation}.
	 */
	public static final IuJsonAdapter<Operation> OP = IuJsonAdapter.text(Operation::from, a -> a.keyOp);

	/**
	 * JSON type adapter for {@link Algorithm}.
	 */
	public static final IuJsonAdapter<Algorithm> ALG = IuJsonAdapter.text(Algorithm::from, a -> a.alg);

	/**
	 * JSON type adapter
	 */
	public static final IuJsonAdapter<Encryption> ENC = IuJsonAdapter.text(Encryption::from, e -> e.enc);

	/**
	 * JSON type adapter for {@link WebKey}.
	 */
	public static final IuJsonAdapter<WebKey> WEBKEY = IuJsonAdapter.from(v -> {
		return new Jwk(v.asJsonObject());
	}, v -> {
		final var o = IuJson.object();
		((Jwk) v).serializeTo(o);
		return o.build();
	});

	/**
	 * JSON type adapter for {@link WebCryptoHeader}.
	 */
	public static final IuJsonAdapter<WebCryptoHeader> JOSE = IuJsonAdapter.from(Jose::new,
			h -> ((Jose) h).toJson(a -> true));

	/**
	 * Gets a JSON type adapter by {@link WebCryptoHeader.Param}.
	 * 
	 * @param <T>   value type
	 * @param param {@link WebCryptoHeader.Param}
	 * @return {@link IuJsonAdapter}
	 */
	@SuppressWarnings("unchecked")
	public static final <T> IuJsonAdapter<T> of(WebCryptoHeader.Param param) {
		switch (param) {
		case ALGORITHM:
			return (IuJsonAdapter<T>) ALG;

		case CERTIFICATE_CHAIN:
			return (IuJsonAdapter<T>) CERT;

		case CERTIFICATE_THUMBPRINT:
		case CERTIFICATE_SHA256_THUMBPRINT:
		case INITIALIZATION_VECTOR:
		case PARTY_UINFO:
		case PARTY_VINFO:
		case PASSWORD_SALT:
		case TAG:
			return (IuJsonAdapter<T>) B64URL;

		case CRITICAL_PARAMS:
			return (IuJsonAdapter<T>) IuJsonAdapter.of(Set.class, IuJsonAdapter.of(String.class));

		case ENCRYPTION:
			return (IuJsonAdapter<T>) ENC;

		case EPHEMERAL_PUBLIC_KEY:
		case KEY:
			return (IuJsonAdapter<T>) WEBKEY;

		case CERTIFICATE_URI:
		case KEY_SET_URI:
			return (IuJsonAdapter<T>) IuJsonAdapter.of(URI.class);

		case PASSWORD_COUNT:
			return (IuJsonAdapter<T>) IuJsonAdapter.of(Integer.class);

		case CONTENT_TYPE:
		case KEY_ID:
		case TYPE:
		case ZIP:
		}
		return (IuJsonAdapter<T>) IuJsonAdapter.of(String.class);
	}

}
