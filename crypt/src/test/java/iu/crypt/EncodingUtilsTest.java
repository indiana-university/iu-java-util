/*
 * Copyright © 2024 Indiana University
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.NoSuchElementException;

import javax.crypto.KeyAgreement;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;

@SuppressWarnings("javadoc")
public class EncodingUtilsTest {

	@Test
	public void testCompactJson() {
		final var object = IuJson.object().add(IdGenerator.generateId(), IdGenerator.generateId()).build();
		assertEquals(object, UnpaddedBinary.compactJson(UnpaddedBinary.base64Url(IuText.utf8(object.toString()))));
	}

	@Test
	public void testCompactIterator() {
		final var f1 = UnpaddedBinary.base64Url(IuText.utf8(IdGenerator.generateId()));
		final var f2 = UnpaddedBinary.base64Url(IuText.utf8(IdGenerator.generateId()));
		final var i = UnpaddedBinary.compact(f1 + ".." + f2);
		assertTrue(i.hasNext());
		assertEquals(f1, i.next());
		assertTrue(i.hasNext());
		assertNull(i.next());
		assertTrue(i.hasNext());
		assertEquals(f2, i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, i::next);
		assertFalse(i.hasNext());
	}

	@Test
	public void testUnpad() {
		assertNull(UnpaddedBinary.unpad(null));
		assertEquals("", UnpaddedBinary.unpad(""));
		assertEquals("a", UnpaddedBinary.unpad("a==="));
	}

	@Test
	public void testPad() {
		assertNull(UnpaddedBinary.pad(null));
		assertEquals("", UnpaddedBinary.pad(""));
		assertEquals("a===", UnpaddedBinary.pad("a"));
		assertEquals("ab==", UnpaddedBinary.pad("ab"));
		assertEquals("abc=", UnpaddedBinary.pad("abc"));
		assertEquals("abcd", UnpaddedBinary.pad("abcd"));
		assertEquals("abcde===", UnpaddedBinary.pad("abcde"));
	}

	@Test
	public void testBase64() {
		// padded
		assertEquals("Zm9vbGJhcm4=", IuText.base64(IuText.utf8("foolbarn")));
		assertEquals("foolbarn", IuText.utf8(IuText.base64("Zm9vbGJhcm4=")));
	}

	@Test
	public void testBase64Url() {
		// unpadded
		assertNull(UnpaddedBinary.base64Url((String) null));
		assertNull(UnpaddedBinary.base64Url((byte[]) null));
		assertEquals("", UnpaddedBinary.base64Url(new byte[0]));
		assertEquals("Zm9vdGJhcnQ", UnpaddedBinary.base64Url(IuText.utf8("footbart")));
		assertEquals("footbart", IuText.utf8(UnpaddedBinary.base64Url("Zm9vdGJhcnQ")));
	}

	@Test
	public void testGetSetBigInt() {
		// covers getBytes()/setBytes()
		final var o = IuJson.object();
		final var bi = BigInteger.valueOf(System.currentTimeMillis());
		IuJson.add(o, "time", () -> bi, UnsignedBigInteger.JSON);
		assertEquals(bi, IuJson.get(o.build(), "time", UnsignedBigInteger.JSON));
	}

	private void assertEncodedBigInt(String e) {
		final var b = UnpaddedBinary.base64Url(e);
		assertEquals(e, UnpaddedBinary.base64Url(b));
		final var i = UnsignedBigInteger.bigInt(b);
		assertArrayEquals(b, UnsignedBigInteger.bigInt(i));
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
		final var alice = WebKey.parse("{\"kty\":\"EC\",\r\n" //
				+ "      \"crv\":\"P-256\",\r\n" //
				+ "      \"x\":\"gI0GAILBdu7T53akrFmMyGcsF3n5dO7MmwNBHKW5SV0\",\r\n" //
				+ "      \"y\":\"SLW_xSffzlPWrHEVI30DHM_4egVwt3NQqeUD7nMFpps\",\r\n" //
				+ "      \"d\":\"0_NxaRPUMQoAJt50Gz8YiTr8gRTwyEaCumd-MToTmIo\"\r\n" //
				+ "     }");

		final var bob = WebKey.parse("{\"kty\":\"EC\",\r\n" //
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

		final var ka2 = KeyAgreement.getInstance("ECDH");
		ka2.init(bob.getPrivateKey());
		ka2.doPhase(alice.getPublicKey(), true);
		final var z2 = ka2.generateSecret();
		assertArrayEquals(new byte[] { (byte) 158, 86, (byte) 217, 29, (byte) 129, 113, 53, (byte) 211, 114, (byte) 131,
				66, (byte) 131, (byte) 191, (byte) 132, 38, (byte) 156, (byte) 251, 49, 110, (byte) 163, (byte) 218,
				(byte) 128, 106, 72, (byte) 246, (byte) 218, (byte) 167, 121, (byte) 140, (byte) 254, (byte) 144,
				(byte) 196 }, z2);

		final var round = 1;
		final var algid = IuText.ascii("A128GCM");
		final var u = IuText.ascii("Alice");
		final var v = IuText.ascii("Bob");
		final var datalen = 128;

		var buf = EncodingUtils.concatKdf(round, z, algid, u, v, datalen);

		assertArrayEquals(buf,
				new byte[] { 0, 0, 0, 1, (byte) 158, 86, (byte) 217, 29, (byte) 129, 113, 53, (byte) 211, 114,
						(byte) 131, 66, (byte) 131, (byte) 191, (byte) 132, 38, (byte) 156, (byte) 251, 49, 110,
						(byte) 163, (byte) 218, (byte) 128, 106, 72, (byte) 246, (byte) 218, (byte) 167, 121,
						(byte) 140, (byte) 254, (byte) 144, (byte) 196, 0, 0, 0, 7, 65, 49, 50, 56, 71, 67, 77, 0, 0, 0,
						5, 65, 108, 105, 99, 101, 0, 0, 0, 3, 66, 111, 98, 0, 0, 0, (byte) 128 });

		final var h = MessageDigest.getInstance("SHA-256").digest(buf);
		final var k = Arrays.copyOf(h, 16);
		assertEquals("VqqN6vgjbSBcIijNcacQGg", UnpaddedBinary.base64Url(k));
	}

	@Test
	public void testBigEndianLong() {
		final ByteBuffer buf = ByteBuffer.wrap(new byte[8]);
		EncodingUtils.bigEndian(408L, buf);
		assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 1, (byte) 152 }, buf.array());
	}

	@Test
	public void testReverse() {
		assertNull(EncodingUtils.reverse(null));
		assertArrayEquals(new byte[0], EncodingUtils.reverse(new byte[0]));
		assertArrayEquals(new byte[] { 1 }, EncodingUtils.reverse(new byte[] { 1 }));
		assertArrayEquals(new byte[] { 1, 2 }, EncodingUtils.reverse(new byte[] { 2, 1 }));
		assertArrayEquals(new byte[] { 1, 2, 3 }, EncodingUtils.reverse(new byte[] { 3, 2, 1 }));
	}
}
