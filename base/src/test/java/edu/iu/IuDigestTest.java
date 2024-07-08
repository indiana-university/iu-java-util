package edu.iu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.security.MessageDigest;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuDigestTest {

	@SuppressWarnings("deprecation")
	@Test
	public void testMD5() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("MD5").digest(data), IuDigest.md5(data));
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testSha1() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-1").digest(data), IuDigest.sha1(data));
	}

	@Test
	public void testSha256() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(data), IuDigest.sha256(data));
	}

	@Test
	public void testSha384() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-384").digest(data), IuDigest.sha384(data));
	}

	@Test
	public void testSha512() throws Throwable {
		final var data = IdGenerator.generateId().getBytes();
		assertArrayEquals(MessageDigest.getInstance("SHA-512").digest(data), IuDigest.sha512(data));
	}

}
