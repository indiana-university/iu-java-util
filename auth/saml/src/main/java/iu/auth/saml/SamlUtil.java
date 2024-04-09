package iu.auth.saml;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

/**
 * Utility class to support SAML implementation. TODO review this class and move
 * code to crypt package as needed
 */
public class SamlUtil {
	private static Key CRYPT_KEY;

	static {
		try {
			CRYPT_KEY = KeyGenerator.getInstance("AES").generateKey();
		} catch (NoSuchAlgorithmException e) {
			throw new SecurityException(e);
		}
	}
	
	/**
	 * default constructor 
	 */
	public SamlUtil() {
	}

	/**
	 * decrypt data
	 * 
	 * @param d string to decrypt
	 * @return decrypted string
	 */
	public static String decrypt(String d) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, CRYPT_KEY);
			String de = new String(cipher.doFinal(Base64.getDecoder().decode(d)), "UTF-8").trim();
			return de;
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to decrpty", e);
		}
	}

	/**
	 * encrypt data
	 * 
	 * @param s string to encrypt
	 * @return encrypted string
	 */
	public static String encrypt(String s) {
		try {
			byte[] dataToEncrypt = s.trim().getBytes("UTF-8");
			int mod = dataToEncrypt.length % 16;
			if (mod != 0)
				dataToEncrypt = Arrays.copyOf(dataToEncrypt, dataToEncrypt.length + 16 - mod);
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, CRYPT_KEY);
			return Base64.getEncoder().encodeToString(cipher.doFinal(dataToEncrypt));
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to encrypt", e);
		}
	}
}
