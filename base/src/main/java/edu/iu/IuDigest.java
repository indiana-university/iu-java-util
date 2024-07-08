package edu.iu;

import java.security.MessageDigest;

/**
 * Provides simplified access to common {@link MessageDigest} operations.
 */
public final class IuDigest {

	/**
	 * Calculates a MD5 digest.
	 * 
	 * @param data data to digest
	 * @return MD5 digest
	 * @deprecated MD5 is not considered secure and <em>should</em> be replaced with
	 *             a stronger algorithm where used
	 */
	@Deprecated
	public static byte[] md5(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("MD5").digest(data));
	}

	/**
	 * Calculates a SHA-1 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-1 digest
	 * @deprecated SHA-1 is not considered secure and <em>should</em> be replaced
	 *             with a stronger algorithm where used
	 */
	@Deprecated
	public static byte[] sha1(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-1").digest(data));
	}

	/**
	 * Calculates a SHA-256 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-256 digest
	 */
	public static byte[] sha256(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-256").digest(data));
	}

	/**
	 * Calculates a SHA-384 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-384 digest
	 */
	public static byte[] sha384(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-384").digest(data));
	}

	/**
	 * Calculates a SHA-512 digest.
	 * 
	 * @param data data to digest
	 * @return SHA-512 digest
	 */
	public static byte[] sha512(byte[] data) {
		return IuException.unchecked(() -> MessageDigest.getInstance("SHA-512").digest(data));
	}

	private IuDigest() {
	}
	
}
