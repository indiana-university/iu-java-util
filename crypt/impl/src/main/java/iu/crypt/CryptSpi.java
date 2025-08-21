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
package iu.crypt;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

import edu.iu.IuCacheMap;
import edu.iu.IuException;
import edu.iu.client.IuHttp;
import edu.iu.client.IuJson;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebCryptoHeader;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import edu.iu.crypt.WebToken;
import edu.iu.crypt.WebTokenBuilder;
import iu.crypt.spi.IuCryptSpi;
import jakarta.json.JsonObject;

/**
 * {@link IuCryptSpi} implementation.
 */
public class CryptSpi implements IuCryptSpi {

	private static Map<URI, X509Certificate[]> CERT_CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	/**
	 * Default constructor.
	 */
	public CryptSpi() {
	}

	@Override
	public X509Certificate[] getCertificateChain(URI uri) {
		var chain = CERT_CACHE.get(uri);
		if (chain == null)
			CERT_CACHE.put(uri, chain = PemEncoded.getCertificateChain((Iterator<PemEncoded>) IuException
					.unchecked(() -> IuHttp.get(uri, IuHttp.validate(PemEncoded::parse, IuHttp.OK)))));
		return chain;
	}

	@Override
	public WebCryptoHeader getProtectedHeader(String serialized) {
		final JsonObject protectedHeader;
		if (serialized.charAt(0) == '{')
			protectedHeader = IuJson.parse(serialized).asJsonObject().getJsonObject("protected");
		else
			protectedHeader = CompactEncoded.getProtectedHeader(serialized);

		return CryptJsonAdapters.JOSE.fromJson(protectedHeader);
	}

	@Override
	public WebKey.Builder<?> getJwkBuilder(Type type) {
		return JwkBuilder.of(type);
	}

	@Override
	public WebKey parseJwk(String jwk) {
		return new Jwk(IuJson.parse(jwk).asJsonObject());
	}

	@Override
	public Iterable<? extends WebKey> parseJwks(String jwks) {
		return Jwk.parseJwks(IuJson.parse(jwks).asJsonObject());
	}

	@Override
	public Iterable<? extends WebKey> readJwks(URI jwks) {
		return Jwk.readJwks(jwks);
	}

	@Override
	public Iterable<? extends WebKey> readJwks(InputStream jwks) {
		return Jwk.readJwks(jwks);
	}

	@Override
	public String asJwks(Iterable<? extends WebKey> webKeys) {
		return Jwk.asJwks(webKeys).toString();
	}

	@Override
	public void writeJwks(Iterable<? extends WebKey> webKeys, OutputStream out) {
		Jwk.writeJwks(webKeys, out);
	}

	@Override
	public WebSignature.Builder<?> getJwsBuilder(Algorithm algorithm) {
		return new JwsBuilder(algorithm);
	}

	@Override
	public Builder getJweBuilder(Encryption encryption, boolean deflate) {
		return new JweBuilder(encryption, deflate);
	}

	@Override
	public WebEncryption parseJwe(String jwe) {
		return new Jwe(jwe);
	}

	@Override
	public WebSignedPayload parseJws(String jws) {
		return JwsBuilder.parse(jws);
	}

	@Override
	public WebTokenBuilder getJwtBuilder() {
		return new JwtBuilder<>();
	}

	@Override
	public WebToken verifyJwt(String jwt, WebKey issuerKey) {
		return new Jwt(Jwt.verify(jwt, issuerKey));
	}

	@Override
	public WebToken decryptAndVerifyJwt(String jwt, WebKey issuerKey, WebKey audienceKey) {
		return new Jwt(Jwt.decryptAndVerify(jwt, issuerKey, audienceKey));
	}

}
