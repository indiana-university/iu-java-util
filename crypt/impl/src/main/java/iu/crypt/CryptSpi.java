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
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Builder;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebSignature;
import edu.iu.crypt.WebSignedPayload;
import iu.crypt.spi.IuCryptSpi;

/**
 * {@link IuCryptSpi} implementation.
 */
public class CryptSpi implements IuCryptSpi {

	private static Map<URI, X509Certificate[]> CERT_CACHE = new IuCacheMap<>(Duration.ofMinutes(15L));

	@Override
	public X509Certificate[] getCertificateChain(URI uri) {
		var chain = CERT_CACHE.get(uri);
		if (chain == null)
			CERT_CACHE.put(uri, chain = PemEncoded.getCertificateChain((Iterator<PemEncoded>) IuException
					.unchecked(() -> IuHttp.get(uri, IuHttp.validate(PemEncoded::parse, IuHttp.OK)))));
		return chain;
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

}
