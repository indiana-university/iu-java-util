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
package iu.crypt.spi;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.cert.X509Certificate;

import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.crypt.WebToken;
import edu.iu.crypt.WebTokenBuilder;

/**
 * Defines methods to be provided by the implementation module.
 */
public interface IuCryptSpi {

	/**
	 * Implements {@link PemEncoded#getCertificateChain(URI)}.
	 * 
	 * @param uri {@link URI}
	 * @return Array of {@link X509Certificate}
	 */
	X509Certificate[] getCertificateChain(URI uri);

	/**
	 * Gets a {@link WebKey.Builder} instance.
	 * 
	 * @param type {@link WebKey.Type}
	 * @return {@link WebKey.Builder}
	 */
	WebKey.Builder<?> getJwkBuilder(WebKey.Type type);

	/**
	 * Implements {@link WebKey#parse(String)}
	 * 
	 * @param jwk Serialized {@link WebKey}
	 * @return {@link WebKey}
	 */
	WebKey parseJwk(String jwk);

	/**
	 * Implements {@link WebKey#parseJwks(String)}
	 * 
	 * @param jwks Serialized {@link WebKey} set
	 * @return {@link Iterable} of {@link WebKey}
	 */
	Iterable<? extends WebKey> parseJwks(String jwks);

	/**
	 * Implements {@link WebKey#readJwks(URI)}
	 * 
	 * @param jwks {@link WebKey} set {@link URI}
	 * @return {@link Iterable} of {@link WebKey}
	 */
	Iterable<? extends WebKey> readJwks(URI jwks);

	/**
	 * Implements {@link WebKey#readJwks(InputStream)}
	 * 
	 * @param jwks {@link InputStream}
	 * @return {@link Iterable} of {@link WebKey}
	 */
	Iterable<? extends WebKey> readJwks(InputStream jwks);

	/**
	 * Implements {@link WebKey#asJwks(Iterable)}
	 * 
	 * @param webKeys {@link Iterable} of {@link WebKey}
	 * @return Serialized {@link WebKey} set
	 */
	String asJwks(Iterable<? extends WebKey> webKeys);

	/**
	 * Implements {@link WebKey#writeJwks(Iterable, OutputStream)}
	 * 
	 * @param webKeys {@link Iterable} of {@link WebKey}
	 * @param out     {@link OutputStream}
	 */
	void writeJwks(Iterable<? extends WebKey> webKeys, OutputStream out);

	/**
	 * Implements {@link WebSignature#builder(Algorithm)}
	 * 
	 * @param algorithm {@link Algorithm}
	 * @return {@link WebSignature.Builder}
	 */
	WebSignature.Builder<?> getJwsBuilder(Algorithm algorithm);

	/**
	 * Implements {@link WebSignedPayload#parse(String)}
	 * 
	 * @param jws Serialized {@link WebSignedPayload}
	 * @return {@link WebSignedPayload}
	 */
	WebSignedPayload parseJws(String jws);

	/**
	 * Implements {@link WebEncryption#builder(Encryption, boolean)}
	 * 
	 * @param encryption {@link Encryption}
	 * @param deflate    Deflate flag
	 * @return {@link WebEncryption.Builder}
	 */
	WebEncryption.Builder getJweBuilder(Encryption encryption, boolean deflate);

	/**
	 * Implements {@link WebEncryption#parse(String)}
	 * 
	 * @param jwe Serialized {@link WebEncryption}
	 * @return {@link WebEncryption}
	 */
	WebEncryption parseJwe(String jwe);

	/**
	 * Implements {@link WebToken#builder()}
	 * 
	 * @return {@link WebTokenBuilder}
	 */
	WebTokenBuilder getJwtBuilder();

	/**
	 * Implements {@link WebToken#verify(String, WebKey)}
	 * 
	 * @param jwt       Signed JWT
	 * @param issuerKey Public key of the token issuer
	 * @return {@link WebToken}
	 */
	WebToken verifyJwt(String jwt, WebKey issuerKey);

	/**
	 * Implements {@link WebToken#decryptAndVerify(String, WebKey, WebKey)}
	 * 
	 * @param jwt         Signed JWT
	 * @param issuerKey   Public key of the token issuer
	 * @param audienceKey Public key of the token audience
	 * @return {@link WebToken}
	 */
	WebToken decryptAndVerifyJwt(String jwt, WebKey issuerKey, WebKey audienceKey);

	/**
	 * Implements {@link WebCryptoHeader#getProtectedHeader(String)}
	 * 
	 * @param serialized Serialized {@link WebSignedPayload} or
	 *                   {@link WebEncryption}
	 * @return {@link WebCryptoHeader}
	 */
	WebCryptoHeader getProtectedHeader(String serialized);

}
