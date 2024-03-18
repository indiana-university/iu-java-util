package iu.crypt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
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

}
