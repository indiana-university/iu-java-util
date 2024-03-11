package iu.auth.util;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;

import edu.iu.IuException;
import jakarta.json.JsonObjectBuilder;

/**
 * Encrypts and decrypts messages as JWE.
 * 
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7516">RFC-7516 JSON
 *      Web Encryption (JWE)</a>
 */
public class JweUtils {

	private static Iterator<byte[]> compact(final String data) {
		return new Iterator<byte[]>() {
			private int start;
			private int end = -1;

			@Override
			public byte[] next() {
				if (!hasNext())
					throw new NoSuchElementException();

				final var next = data.substring(start, end);
				start = end + 1;
				return base64Url(next);
			}

			@Override
			public boolean hasNext() {
				if (end < start) {
					end = data.indexOf('.', start);
					if (end == -1)
						end = data.length();
				}
				return start < data.length();
			}
		};
	}

	private static class Jwe {
		private String protectedHeader;
		private JsonObjectBuilder unprotected;
		private JsonObjectBuilder header;
		private byte[] key;
		private byte[] init;
		private byte[] cipher;
		private byte[] tag;
		private byte[] aad;

		private Jwe() {
		}

		private Jwe(String jwe) {
			if (jwe.startsWith("{"))
				throw new UnsupportedOperationException("TODO");

			final var i = JweUtils.compact(jwe);
			protectedHeader = utf8(i.next());
			key = i.next();
			init = i.next();
			cipher = i.next();
			tag = i.next();

			if (i.hasNext())
				throw new IllegalArgumentException();
		}

		private String compact() {
			return base64Url(utf8(protectedHeader)) //
					+ '.' + base64Url(key) //
					+ '.' + base64Url(init) //
					+ '.' + base64Url(cipher) //
					+ '.' + base64Url(tag);
		}

		private String serial() {
			return JsonProviderFactory.JSON.createObjectBuilder() //
					.add("protected", base64Url(utf8(protectedHeader))) //
					.add("unprotected", unprotected) //
					.add("header", header) //
					.add("encrypted_key", base64Url(key)) //
					.add("iv", base64Url(init)) //
					.add("ciphertext", base64Url(cipher)) //
					.add("tag", base64Url(tag)) //
					.add("aad", base64Url(aad)) //
					.build().toString();
		}
	}

	static String unpad(String b64) {
		if (b64 == null || b64.isEmpty())
			return b64;
		var i = b64.length() - 1;
		while (i > 0 && b64.charAt(i) == '=')
			i--;
		return b64.substring(0, i + 1);
	}

	static String pad(String b64) {
		if (b64 == null || b64.isEmpty())
			return b64;
		switch (b64.length() % 4) {
		case 1:
			return b64 + "===";
		case 2:
			return b64 + "==";
		case 3:
			return b64 + "=";
		default:
			return b64;
		}
	}

	static String base64Url(byte[] data) {
		return unpad(Base64.getUrlEncoder().encodeToString(data));
	}

	static byte[] base64Url(String data) {
		return Base64.getUrlDecoder().decode(pad(data));
	}

	static String utf8(byte[] data) {
		return IuException.unchecked(() -> new String(data, "UTF-8"));
	}

	static byte[] utf8(String data) {
		return IuException.unchecked(() -> data.getBytes("UTF-8"));
	}

	public static String encrypt(byte[] data, RSAPublicKey rsa) {
		final var jwe = new Jwe();
		
		final var header = JsonProviderFactory.JSON.createObjectBuilder();
		header.add("alg", "RSA-OAEP-256");
		header.add("enc", "A256GCM");
		header.add("zip", "DEF");
		
		
		// https://datatracker.ietf.org/doc/html/rfc3447#appendix-A.2.1
//		The remaining steps to finish creating this JWE are:
//
//			   o  Generate a random Content Encryption Key (CEK).
//
//			   o  Encrypt the CEK with the recipient's public key using the RSAES-
//			      OAEP algorithm to produce the JWE Encrypted Key.
//
//			   o  Base64url-encode the JWE Encrypted Key.
//
//			   o  Generate a random JWE Initialization Vector.
//
//			   o  Base64url-encode the JWE Initialization Vector.
//
//			   o  Let the Additional Authenticated Data encryption parameter be
//			      ASCII(BASE64URL(UTF8(JWE Protected Header))).
//
//			   o  Perform authenticated encryption on the plaintext with the AES GCM
//			      algorithm using the CEK as the encryption key, the JWE
//			      Initialization Vector, and the Additional Authenticated Data
//			      value, requesting a 128-bit Authentication Tag output.
//
//			   o  Base64url-encode the ciphertext.
//
//			   o  Base64url-encode the Authentication Tag.
//
//
//
//
//
//
//			Jones & Hildebrand           Standards Track                   [Page 10]
//
//			RFC 7516                JSON Web Encryption (JWE)               May 2015
//
//
//			   o  Assemble the final representation: The Compact Serialization of
//			      this result is the string BASE64URL(UTF8(JWE Protected Header)) ||
//			      '.' || BASE64URL(JWE Encrypted Key) || '.' || BASE64URL(JWE
//			      Initialization Vector) || '.' || BASE64URL(JWE Ciphertext) || '.'
//			      || BASE64URL(JWE Authentication Tag).
		return jwe.compact();
	}

	public static byte[] decrypt(String data, RSAPrivateKey rsa) {
		final var jwe = new Jwe(data);
		throw new UnsupportedOperationException("TODO");
	}

	private JweUtils() {
	}

}
