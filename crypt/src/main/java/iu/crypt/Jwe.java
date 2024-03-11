package iu.crypt;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import edu.iu.IuException;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryptionHeader;
import edu.iu.crypt.WebEncryptionHeader.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;

/**
 * JSON Web Encryption (JWE) implementation class.
 */
public class Jwe implements WebEncryption {

	private static final byte[] b0 = new byte[0];

	private static class AesGcm {
		private final int size;
		private final SecureRandom rand = new SecureRandom();
		private final byte[] fixed = new byte[4];
		private final byte[] iv = new byte[12];
		private SecretKey cek;
		private int c;

		private AesGcm(int size) {
			this.size = size;
			rand.nextBytes(fixed);
		}

		private GCMParameterSpec spec() {
			if (c++ == 0)
				cek = cek(size);

			// NIST 800-38D 8.2.1 Deterministic Construction
			rand.nextBytes(iv);
			System.arraycopy(fixed, 0, iv, 0, 4);
			iv[4] = (byte) c;
			iv[6] = (byte) ((c >>> 8) & 0xff);
			iv[8] = (byte) ((c >>> 16) & 0xff);
			iv[10] = (byte) ((c >>> 24) & 0xff);
			return new GCMParameterSpec(128, iv);
		}
	}

	private static ThreadLocal<Map<Integer, AesGcm>> GCM = new ThreadLocal<>() {
		@Override
		protected Map<Integer, AesGcm> initialValue() {
			return new HashMap<>();
		}
	};

	private static SecretKey cek(int size) {
		final var keygen = IuException.unchecked(() -> KeyGenerator.getInstance("AES"));
		keygen.init(size);
		return keygen.generateKey();
	}

	private static AesGcm gcm(int size) {
		final var gcm = GCM.get();
		var aesGcm = gcm.get(size);
		if (aesGcm == null)
			gcm.put(size, aesGcm = new AesGcm(size));
		return aesGcm;
	}

	/**
	 * Encrypts data as JWE.
	 * 
	 * @param header header data
	 * @param data   data to encrypt
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption encrypt(WebEncryptionHeader header, byte[] data) {
		final var algorithm = header.getAlgorithm();
		if (!Use.ENCRYPT.equals(algorithm.use))
			throw new IllegalArgumentException("alg");

		final byte[] content;
		if (header.isDeflate()) {
			final var b = new ByteArrayOutputStream();
			IuException.unchecked(() -> {
				try (final var d = new DeflaterOutputStream(b)) {
					d.write(data);
				}
			});
			content = b.toByteArray();
		} else
			content = data;

		return IuException.unchecked(() -> {
			final Encryption encryption = header.getEncryption();
			final GCMParameterSpec gcm;
			final SecretKey cek;

			if (Type.RAW.equals(algorithm.type)) {
				cek = new SecretKeySpec(Jose.getKey(header).getKey(), "AES");
				if (algorithm.auxiliaryAlgorithm.equals("GCM")) {
					final var a = new AesGcm(algorithm.size);
					while (a.c == 0)
						a.c = a.rand.nextInt();
					gcm = a.spec();
				} else
					gcm = null;
			} else if ("GCM".equals(encryption.cipherMode)) {
				final var g = gcm(encryption.size);
				gcm = g.spec();
				cek = g.cek;
			} else {
				gcm = null;
				cek = cek(encryption.size);
			}

			final Key recipientKey;
			switch (algorithm.algorithm) {
			case "ECDH":
				recipientKey = IuException.unchecked(() -> KeyAgreement.getInstance(algorithm.algorithm)
						.doPhase(Jose.getKey(header).getPublicKey(), false));
				break;

			case "RSA":
				recipientKey = Jose.getKey(header).getPublicKey();
				break;

			default:
				recipientKey = null;
			}

			final byte[] encryptedKey;
			if (recipientKey != null) {
				final var cipher = Cipher.getInstance(algorithm.auxiliaryAlgorithm);
				cipher.init(Cipher.WRAP_MODE, recipientKey);
				if (gcm == null) {
					final byte[] mac = new byte[algorithm.size];
					new SecureRandom().nextBytes(mac);
					final byte[] k = new byte[algorithm.size * 2];
					System.arraycopy(mac, 0, k, 0, algorithm.size);
					System.arraycopy(cek.getEncoded(), 0, k, algorithm.size, algorithm.size);
					encryptedKey = cipher.doFinal(k);
				} else
					encryptedKey = cipher.doFinal(cek.getEncoded());
			} else
				encryptedKey = b0;

			final byte[] initializationVector;
			if (gcm != null)
				initializationVector = gcm.getIV();
			else if (recipientKey != null) {
				initializationVector = new byte[16];
				new SecureRandom().nextBytes(initializationVector);
			} else
				initializationVector = b0;

			final var encodedProtectedHeader = EncodingUtils
					.base64Url(EncodingUtils.utf8(Jose.getProtected(header).toString()));
			final byte[] aad;
			final var additionalData = header.getAdditionalAuthenticatedData();
			if (additionalData == null)
				aad = IuException.unchecked(() -> encodedProtectedHeader.getBytes("US-ASCII"));
			else
				aad = IuException
						.unchecked(() -> (encodedProtectedHeader + '.' + EncodingUtils.base64Url(additionalData))
								.getBytes("US-ASCII"));

			final Cipher cipher;
			switch (algorithm) {
			case DIRECT:
				cipher = Cipher.getInstance(algorithm.algorithm);
				cipher.init(Cipher.ENCRYPT_MODE, cek, gcm);
				break;

			case A128KW:
			case A192KW:
			case A256KW:
			case A128GCMKW:
			case A192GCMKW:
			case A256GCMKW:
			case PBES2_HS256_A128KW:
			case PBES2_HS384_A192KW:
			case PBES2_HS512_A256KW:
				cipher = Cipher.getInstance(algorithm.algorithm);
				cipher.init(Cipher.WRAP_MODE, cek, gcm);
				break;

			default:
				cipher = Cipher.getInstance(encryption.cipherAlgorithm + '/' + encryption.cipherMode + "/PKCS7Padding");
				cipher.init(Cipher.ENCRYPT_MODE, cek, gcm);
				break;
			}

			final byte[] cipherText, authenticationTag;
			if (gcm != null) {
				cipher.updateAAD(aad);

				final byte[] encrypted = cipher.doFinal(content);
				final int taglen = gcm.getTLen() / 8;
				cipherText = Arrays.copyOfRange(encrypted, 0, encrypted.length - taglen);
				authenticationTag = Arrays.copyOfRange(encrypted, encrypted.length - taglen, encrypted.length);

			} else if (recipientKey != null) {
				cipherText = cipher.doFinal(content);

				final var mac = Mac.getInstance(encryption.mac);
				mac.init(new SecretKeySpec(encryptedKey, 0, encryption.size, encryption.mac));
				final var tag = new ByteArrayOutputStream();
				tag.write(aad);
				tag.write(initializationVector);
				tag.write(cipherText);
				final var al = BigInteger.valueOf(aad.length).toByteArray();
				final var pad = 8 - al.length;
				for (int i = 0; i < pad; i++)
					tag.write(0);
				tag.write(al);
				authenticationTag = mac.doFinal(tag.toByteArray());
			} else {
				cipherText = cipher.doFinal(content);
				authenticationTag = b0;
			}

			return new Jwe(header, encryptedKey, initializationVector, cipherText, authenticationTag, additionalData);
		});
	}

	/**
	 * Parses a {@link #compact() compact} or {@link #serialize() serialized} JWE
	 * encrypted message.
	 * 
	 * @param jwe compact or serialized JWE message
	 * @return JSON Web Encryption (JWE) encrypted message
	 */
	public static WebEncryption readJwe(String jwe) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	private final WebEncryptionHeader header;
	private final byte[] encryptedKey;
	private final byte[] initializationVector;
	private final byte[] cipherText;
	private final byte[] authenticationTag;
	private final byte[] additionalData;

	private Jwe(WebEncryptionHeader header, byte[] encryptedKey, byte[] initializationVector, byte[] cipherText,
			byte[] authenticationTag, byte[] additionalData) {
		this.header = header;
		this.encryptedKey = encryptedKey;
		this.initializationVector = initializationVector;
		this.cipherText = cipherText;
		this.authenticationTag = authenticationTag;
		this.additionalData = additionalData;
	}

	@Override
	public WebEncryptionHeader getHeader() {
		return header;
	}

	@Override
	public byte[] getEncryptedKey() {
		return encryptedKey;
	}

	@Override
	public byte[] getInitializationVector() {
		return initializationVector;
	}

	@Override
	public byte[] getCipherText() {
		return cipherText;
	}

	@Override
	public byte[] getAuthenticationTag() {
		return authenticationTag;
	}

	@Override
	public byte[] getAdditionalData() {
		return additionalData;
	}

	@Override
	public String compact() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String serialize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] decrypt(WebKey key) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
