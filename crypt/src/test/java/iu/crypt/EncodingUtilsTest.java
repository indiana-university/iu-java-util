package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.NoSuchElementException;

import javax.crypto.KeyAgreement;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuCrypt;
import edu.iu.client.IuJson;

@SuppressWarnings("javadoc")
public class EncodingUtilsTest {

	@Test
	public void testCompactJson() {
		final var object = IuJson.object().add(IdGenerator.generateId(), IdGenerator.generateId()).build();
		assertEquals(object, EncodingUtils.compactJson(EncodingUtils.base64Url(EncodingUtils.utf8(object.toString()))));
	}

	@Test
	public void testCompactIterator() {
		final var f1 = EncodingUtils.base64Url(EncodingUtils.utf8(IdGenerator.generateId()));
		final var f2 = EncodingUtils.base64Url(EncodingUtils.utf8(IdGenerator.generateId()));
		final var i = EncodingUtils.compact(f1 + ".." + f2);
		assertTrue(i.hasNext());
		assertArrayEquals(EncodingUtils.base64(f1), i.next());
		assertTrue(i.hasNext());
		assertNull(i.next());
		assertTrue(i.hasNext());
		assertArrayEquals(EncodingUtils.base64(f2), i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, i::next);
		assertFalse(i.hasNext());
	}

	@Test
	public void testUnpad() {
		assertNull(EncodingUtils.unpad(null));
		assertEquals("", EncodingUtils.unpad(""));
		assertEquals("a", EncodingUtils.unpad("a==="));
	}

	@Test
	public void testPad() {
		assertNull(EncodingUtils.pad(null));
		assertEquals("", EncodingUtils.pad(""));
		assertEquals("a===", EncodingUtils.pad("a"));
		assertEquals("ab==", EncodingUtils.pad("ab"));
		assertEquals("abc=", EncodingUtils.pad("abc"));
		assertEquals("abcd", EncodingUtils.pad("abcd"));
		assertEquals("abcde===", EncodingUtils.pad("abcde"));
	}

	@Test
	public void testBase64() {
		// padded
		assertEquals("Zm9vbGJhcm4=", EncodingUtils.base64(EncodingUtils.utf8("foolbarn")));
		assertEquals("foolbarn", EncodingUtils.utf8(EncodingUtils.base64("Zm9vbGJhcm4=")));
	}

	@Test
	public void testBase64Url() {
		// unpadded
		assertNull(EncodingUtils.base64Url((String) null));
		assertNull(EncodingUtils.base64Url(" "));
		assertEquals("", EncodingUtils.base64Url((byte[]) null));
		assertEquals("", EncodingUtils.base64Url(new byte[0]));
		assertEquals("Zm9vdGJhcnQ", EncodingUtils.base64Url(EncodingUtils.utf8("footbart")));
		assertEquals("footbart", EncodingUtils.utf8(EncodingUtils.base64Url("Zm9vdGJhcnQ")));
	}

	@Test
	public void testGetSetBigInt() {
		// covers getBytes()/setBytes()
		final var o = IuJson.object();
		final var bi = BigInteger.valueOf(System.currentTimeMillis());
		EncodingUtils.setBigInt(o, "time", bi);
		assertEquals(bi, EncodingUtils.getBigInt(o.build(), "time"));
	}

	private void assertEncodedBigInt(String e) {
		final var b = EncodingUtils.base64Url(e);
		assertEquals(e, EncodingUtils.base64Url(b));
		final var i = EncodingUtils.toBigInteger(b);
		assertArrayEquals(b, EncodingUtils.toByteArray(i));
	}

	@Test
	public void testRFC7517_A_1_BigInts() {
		assertEncodedBigInt("MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4");
		assertEncodedBigInt("4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM");
		assertEncodedBigInt(
				"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw");
	}

	@Test
	public void testConcatKdf_RFC_7818_C() throws NoSuchAlgorithmException, InvalidKeyException {
		final var alice = JwkBuilder.parse("{\"kty\":\"EC\",\r\n" //
				+ "      \"crv\":\"P-256\",\r\n" //
				+ "      \"x\":\"gI0GAILBdu7T53akrFmMyGcsF3n5dO7MmwNBHKW5SV0\",\r\n" //
				+ "      \"y\":\"SLW_xSffzlPWrHEVI30DHM_4egVwt3NQqeUD7nMFpps\",\r\n" //
				+ "      \"d\":\"0_NxaRPUMQoAJt50Gz8YiTr8gRTwyEaCumd-MToTmIo\"\r\n" //
				+ "     }");

		final var bob = JwkBuilder.parse("{\"kty\":\"EC\",\r\n" //
				+ "      \"crv\":\"P-256\",\r\n" //
				+ "      \"x\":\"weNJy2HscCSM6AEDTDg04biOvhFhyyWvOHQfeF_PxMQ\",\r\n" //
				+ "      \"y\":\"e8lnCO-AlStT-NJVX-crhB7QRYhiix03illJOVAOyck\",\r\n" //
				+ "      \"d\":\"VEmDZpDXXK8p8N0Cndsxs924q6nS1RXFASRl6BfUqdw\"\r\n" //
				+ "     }");

		final var ka = KeyAgreement.getInstance("ECDH");
		ka.init(alice.getPrivateKey());
		ka.doPhase(bob.getPublicKey(), true);
		final var z = ka.generateSecret();
		assertArrayEquals(new byte[] { (byte) 158, 86, (byte) 217, 29, (byte) 129, 113, 53, (byte) 211, 114, (byte) 131,
				66, (byte) 131, (byte) 191, (byte) 132, 38, (byte) 156, (byte) 251, 49, 110, (byte) 163, (byte) 218,
				(byte) 128, 106, 72, (byte) 246, (byte) 218, (byte) 167, 121, (byte) 140, (byte) 254, (byte) 144,
				(byte) 196 }, z);

		final var round = 1;
		final var algid = "A128GCM";
		final var u = "Alice";
		final var v = "Bob";
		final var datalen = 128;

		var buf = EncodingUtils.concatKdf(round, z, algid, u, v, datalen);

		assertArrayEquals(buf,
				new byte[] { 0, 0, 0, 1, (byte) 158, 86, (byte) 217, 29, (byte) 129, 113, 53, (byte) 211, 114,
						(byte) 131, 66, (byte) 131, (byte) 191, (byte) 132, 38, (byte) 156, (byte) 251, 49, 110,
						(byte) 163, (byte) 218, (byte) 128, 106, 72, (byte) 246, (byte) 218, (byte) 167, 121,
						(byte) 140, (byte) 254, (byte) 144, (byte) 196, 0, 0, 0, 7, 65, 49, 50, 56, 71, 67, 77, 0, 0, 0,
						5, 65, 108, 105, 99, 101, 0, 0, 0, 3, 66, 111, 98, 0, 0, 0, (byte) 128 });

		final var h = IuCrypt.sha256(buf);
		final var k = Arrays.copyOf(h, 16);
		assertEquals("VqqN6vgjbSBcIijNcacQGg", EncodingUtils.base64Url(k));
	}

	@Test
	public void testBigEndianLong() {
		final byte[] buf = new byte[8];
		EncodingUtils.bigEndian(408L, buf, 0);
		assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 1, (byte) 152 }, buf);
	}

}
