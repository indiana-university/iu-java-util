package iu.crypt;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.Queue;

import edu.iu.IuException;
import edu.iu.IuIterable;
import jakarta.json.JsonArray;
import jakarta.json.JsonString;

/**
 * Utilities for working with X.509 certificates.
 */
public class CertUtils {

	/**
	 * Decodes a certificate chain from raw encoded data.
	 * 
	 * @param x5c JSON array of Base-64 encoded certificates
	 * @return parsed certificate chain
	 */
	static X509Certificate[] decodeCertificateChain(JsonArray x5c) {
		return decodeCertificateChain(IuIterable.map(x5c, i -> EncodingUtils.base64(((JsonString) i).getString())));
	}

	/**
	 * Decodes a certificate chain from raw encoded data.
	 * 
	 * @param encodedCerts encoded certificates
	 * @return parsed certificate chain
	 */
	static X509Certificate[] decodeCertificateChain(Iterable<byte[]> encodedCerts) {
		return IuException.unchecked(() -> {
			final var certFactory = CertificateFactory.getInstance("X.509");
			final Queue<X509Certificate> certs = new ArrayDeque<>();
			for (final var encodedCert : encodedCerts)
				certs.offer((X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(encodedCert)));
			return certs.toArray(new X509Certificate[certs.size()]);
		});
	}

	private CertUtils() {
	}

}
