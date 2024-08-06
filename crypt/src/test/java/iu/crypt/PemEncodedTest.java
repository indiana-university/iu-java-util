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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509CRL;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.crypt.IuCryptTestCase;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.PemEncoded.KeyType;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;

@SuppressWarnings("javadoc")
public class PemEncodedTest extends IuCryptTestCase {

	@Test
	public void testInvalid() {
		final var i = PemEncoded.parse("_-$" + IdGenerator.generateId());
		assertTrue(i.hasNext());
		assertThrows(IllegalArgumentException.class, () -> i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
		assertFalse(i.hasNext());
	}

	@Test
	public void testInvalidSecondEntry() {
		final var i = PemEncoded
				.parse("-----BEGIN CERTIFICATE-----\n-----END CERTIFICATE-----\n" + IdGenerator.generateId());
		assertTrue(i.hasNext());
		i.next();
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
	}

	@Test
	public void testInvalidType() {
		final var i = PemEncoded.parse("-----BEGIN INVALID DATA-----\n-----END INVALID DATA-----\n");
		assertTrue(i.hasNext());
		assertThrows(IllegalArgumentException.class, () -> i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
		assertFalse(i.hasNext());
	}

	@Test
	public void testMissingEnd() {
		final var i = PemEncoded.parse("-----BEGIN PUBLIC KEY-----\n");
		assertTrue(i.hasNext());
		assertThrows(IllegalArgumentException.class, () -> i.next());
		assertFalse(i.hasNext());
		assertThrows(NoSuchElementException.class, () -> i.next());
		assertFalse(i.hasNext());
	}

	@Test
	public void testSerializeRequiresMatch() throws InvalidKeySpecException, NoSuchAlgorithmException {

		final var kp1 = WebKey.builder(Type.RSA).algorithm(Algorithm.RSA_OAEP).ephemeral().build();
		final var kp2 = WebKey.builder(Type.RSA).algorithm(Algorithm.RSA_OAEP).ephemeral().build();
		assertThrows(IllegalArgumentException.class,
				() -> PemEncoded.serialize(new KeyPair(kp1.getPublicKey(), kp2.getPrivateKey())));

		final var frankenpub = KeyFactory.getInstance("RSA")
				.generatePublic(new RSAPublicKeySpec(((RSAPublicKey) kp1.getPublicKey()).getModulus(), BigInteger.TEN));
		assertThrows(IllegalArgumentException.class,
				() -> PemEncoded.serialize(new KeyPair(frankenpub, kp1.getPrivateKey())));

		assertEquals(CERT, PemEncoded.CERT_JSON.fromJson(PemEncoded.CERT_JSON.toJson(CERT)));
		assertThrows(IllegalArgumentException.class,
				() -> PemEncoded.serialize(new KeyPair(kp1.getPublicKey(), kp1.getPrivateKey()), CERT));

		assertThrows(IllegalArgumentException.class, () -> PemEncoded
				.getCertificateChain(PemEncoded.serialize(new KeyPair(kp1.getPublicKey(), kp1.getPrivateKey()))));
	}

	@Test
	public void testRSAFromPEMNoCert() throws NoSuchAlgorithmException, InvalidKeySpecException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl genrsa
		final var text = "-----BEGIN PRIVATE KEY-----\r\n"
				+ "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDYXFUKjgq4Iblp\r\n"
				+ "mU3Ymww0LgMjaWIO/DmFQF+EViO+rCRteffNzKFWR3+raINMH4uKXL7d9NGJa1TF\r\n"
				+ "pbrj3XdsdKk/uhrmWfvnClvs79e8J/+UBQ59h5Da7C3f19rVfdIxf+jkPYff+lSw\r\n"
				+ "JLCLlZVsdn71BPAKpOvsu5qr9Nc04EMcPMklbc+n882hPsyeopAgZ01l928RX7/U\r\n"
				+ "NU3Uw+MQuYYia54XI3P6PPKDNfqd9dMY0KHLUeo6b/5FZZkLZvnikuvNVO4H+fQO\r\n"
				+ "OVDvezhXFxO2zM9Q2eCJbvayR2p0TthK2N7O48cKofgMdk1U4Un2vMDXF7pTdGtI\r\n"
				+ "udC1OzJXAgMBAAECggEACObvptIGVeIpV1Nz9QQYIfN8tJHK85PkJ/vokjDbIqbB\r\n"
				+ "jvGURRb00nB5q8tOj6zCmIxNXCONFYrhf4pcoLCFj+RS7GjTX4P3Td/KvXp21WqN\r\n"
				+ "5QC6Qmb4ClHqZ0nh2qPlKJ07L1zqwMfzgRXZX7zlW4OaoKk12TJE9MYZTJbz3dyC\r\n"
				+ "7Dl6Z6o2PM7HEUXfw7ge6CFDTUV6/cQxfNieKrpVEsCOSj3XUf1hCscWBa7JApWe\r\n"
				+ "ejhz3YEqFHwprIPe21ZkPbVGz1hkhNCMfBFLw2ZJmiu/yyV9/LhefIul+4nJyIGE\r\n"
				+ "InYzbjnYPn+gI46i9I8S6v/WQYCJu+q1ZD4mHPnMDQKBgQD8d92fLDIZwxnbC99J\r\n"
				+ "sJemmhxvcX3F8PvfqG+JcyNf6dgiaIUECUnvfgDipdDmKzHjzD6OTKyerzRVmidj\r\n"
				+ "qpDkivHVhTbuqNZpQaWt+8tSpjxN5oZySfizeOeBLrphcay61h6q6ne3HHiWIa82\r\n"
				+ "6qDVQUe0qYb18SP/RLmofMizUwKBgQDbYyioRlvzgbwZ+lhXD0gimx1+QRxb90/d\r\n"
				+ "+9GiE3IbSCTWwz1efyOdDy+xCzh8/L7NX8E0ZQ6e4pmuBBnDt8aZ3boyRt0e0ui/\r\n"
				+ "Sepg20iCfBVOJ37i3n9GhBprkzzIPBb4YoPQQwyOvBgjYhak8hrpADJUWJeIYzi0\r\n"
				+ "pdGCQdrIbQKBgQCH9ZEe7/EHGJ8q7EjR6Uyxxpp7lXWzDCTH/HAcaCnrtAXV+c1w\r\n"
				+ "MARl+chGRh+qZCaY01v4y+fGCPo5AywlKyyeNwknAHdlrPzScCzl9gw3tRgSp4tN\r\n"
				+ "rvJEzF53ng92/H2VnEulpWDU9nsl9nviKhZ04ZPZAdaRScwl4v/McW6vywKBgHq0\r\n"
				+ "MDZF/AHrKvjgo242FuN8HHfUFPd/EIWY5bwf4i9OH4Sa+IUU2SdsKgF8xCBsAI+/\r\n"
				+ "ocEbUJ0fIlNI6dwkuoiukgiyx9QIpLLwtY1suFZ67jOjNX3QciFPm7NVS6a2rSZJ\r\n"
				+ "e24NQkXHAD0yDHY/DzwIpx2z2zUmQb4QDGktSh/VAoGBAI+93qCHtVU5rUeY3771\r\n"
				+ "V541cJqy1gKCob3w9wfhbCTM8ynVREZyUpljcnDBQ9H+gkaoHtPy000FlbUHNyBf\r\n"
				+ "K1ixXXvUZZEvN/8UyQp3VJipKbL+NDXaq8qE8eixPwkG1L2ebqlbjZsxKXKbotnp\r\n"
				+ "Jh+eDKPGD66PxfmLT9GtZxS+\r\n" //
				+ "-----END PRIVATE KEY-----\r\n";
		final var pem = PemEncoded.parse(new ByteArrayInputStream(IuText.utf8(text)));

		assertTrue(pem.hasNext());
		final var key = pem.next();
		assertEquals(KeyType.PRIVATE_KEY, key.getKeyType());
		assertInstanceOf(RSAPrivateCrtKey.class, key.asPrivate("RSA"));

		assertThrows(IllegalStateException.class, () -> key.asPublic("RSA"));
		assertThrows(IllegalStateException.class, () -> key.asCertificate());

		assertFalse(pem.hasNext());

		assertEquals(text, key.toString().replace("\n", "\r\n"));
	}

	@Test
	public void testECFromPEMNoCert()
			throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl ecparam -genkey -noout -name secp521r1 > /tmp/k
		// $ openssl ec -no_public < /tmp/k | openssl pkcs8 -topk8 -nocrypt
		// $ openssl ec -pubout < /tmp/k
		final var text = "-----BEGIN PRIVATE KEY-----\r\n" //
				+ "MGACAQAwEAYHKoZIzj0CAQYFK4EEACMESTBHAgEBBEIBLCXIEemmfpj9jAzTmW/H\r\n" //
				+ "jZUeZyw4LT8umafRaMsH9xyVqcb/UTO5uixfKbz3uB+QnS5Yo90/bi5oxfdizYt+\r\n" //
				+ "U0w=\r\n" //
				+ "-----END PRIVATE KEY-----\r\n" //
				+ "-----BEGIN PUBLIC KEY-----\r\n" //
				+ "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQA/QrFKdH3OWU0RFctSl1F0b05DXkk\r\n" //
				+ "DDj9epDpM8WUT4xLa8SWESVQ7/uxgYt4RCZYGI4Z5FNU9PigadlPUOG8SS0BrIGc\r\n" //
				+ "PWwa6m5wXKoREuy6Msf3o/gXAu5Go1zH0VEAcUJ66voXchvFDQa1BHKXMZt7J0/d\r\n" //
				+ "fuza5i/odJOdUNYTE8U=\r\n" //
				+ "-----END PUBLIC KEY-----\r\n";
		final var pem = PemEncoded.parse(text);

		assertTrue(pem.hasNext());
		var key = pem.next();
		assertEquals(KeyType.PRIVATE_KEY, key.getKeyType());
		final var priv = assertInstanceOf(ECPrivateKey.class, key.asPrivate("EC"));
		assertEquals("secp521r1 [NIST P-521] (1.3.132.0.35)", priv.getParams().toString());

		assertTrue(pem.hasNext());
		key = pem.next();
		assertEquals(KeyType.PUBLIC_KEY, key.getKeyType());
		final var pub = assertInstanceOf(ECPublicKey.class, key.asPublic("EC"));
		assertEquals(priv.getParams(), pub.getParams());

		assertFalse(pem.hasNext());

		final var confirm = new StringBuilder();
		PemEncoded.serialize(new KeyPair(pub, priv)).forEachRemaining(confirm::append);
		assertEquals(text, confirm.toString().replace("\n", "\r\n"));
	}

	@Test
	public void testECFromPEMWithCert()
			throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
		final var text = EC_PRIVATE_KEY + ANOTHER_CERT_TEXT;
		final var pem = PemEncoded.parse(text);

		assertTrue(pem.hasNext());
		var key = pem.next();
		assertEquals(KeyType.PRIVATE_KEY, key.getKeyType());
		final var priv = assertInstanceOf(ECPrivateKey.class, key.asPrivate("EC"));
		assertEquals("secp384r1 [NIST P-384] (1.3.132.0.34)", priv.getParams().toString());

		assertTrue(pem.hasNext());
		key = pem.next();
		assertEquals(KeyType.CERTIFICATE, key.getKeyType());
		final var cert = key.asCertificate();
		final var pub = assertInstanceOf(ECPublicKey.class, cert.getPublicKey());
		assertEquals(priv.getParams(), pub.getParams());

		assertFalse(pem.hasNext());

		final var confirm = new StringBuilder();
		PemEncoded.serialize(new KeyPair(pub, priv), cert).forEachRemaining(confirm::append);
		assertEquals(text, confirm.toString().replace("\n", "\r\n"));

		final var confirmNoPub = new StringBuilder();
		PemEncoded.serialize(new KeyPair(null, priv), cert).forEachRemaining(confirmNoPub::append);
		assertEquals(text, confirmNoPub.toString().replace("\n", "\r\n"));
	}

	@Test
	public void testRSAFromPEMWithCert()
			throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl genrsa | tee /tmp/k
		// $ openssl req -days 410 -x509 -key /tmp/k
		final var certText = "-----BEGIN CERTIFICATE-----\r\n" //
				+ "MIID5TCCAs2gAwIBAgIUDSy2fR7Mli1vvbswCfNcW8crSZYwDQYJKoZIhvcNAQEL\r\n"
				+ "BQAwgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtC\r\n"
				+ "bG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQL\r\n"
				+ "DAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5cHQtdGVzdDAgFw0yNDAzMTAx\r\n"
				+ "OTIxNDlaGA8yMTI0MDMxMTE5MjE0OVowgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQI\r\n"
				+ "DAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFu\r\n"
				+ "YSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEt\r\n"
				+ "Y3J5cHQtdGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALL0kKuy\r\n"
				+ "9h1E6AqPrFu3dUvOb3f2fPjyqlyStGGk4P8rUljJd+QyubAapIF2Sq420a9Q7atp\r\n"
				+ "EBZiLeC0fV8VbrBTYSFp2Up3rxUcEVkDZKpCjbwZ16RZIenGZWYBkLQh5P/VjrUG\r\n"
				+ "HCD9QSnTy08yBLAFrnOzBRL0mLoLmRVbam47QUV98pNAsmZF0wxsrSp6pmMSnHGY\r\n"
				+ "zlWFX9/vnrSWGMSKy229hYKMfSbY76sJNt605JWK19A3NjgeMT0rWZcCHnpv1s63\r\n"
				+ "DWx2ZQuKVNgTZm5oftLPQ6Dj4PwqEo9aMqahnIYw8t37zbq3ZsZgL+4Hcu866YAe\r\n"
				+ "W0GhvZVeOd89zS8CAwEAAaNTMFEwHQYDVR0OBBYEFNVoqadb2L5DK9+5yJ3WPxQs\r\n"
				+ "Dv/dMB8GA1UdIwQYMBaAFNVoqadb2L5DK9+5yJ3WPxQsDv/dMA8GA1UdEwEB/wQF\r\n"
				+ "MAMBAf8wDQYJKoZIhvcNAQELBQADggEBADzre/3bkFb36eYUeTrun+334+9v3VM2\r\n"
				+ "S6Sa2ycrUqquA0igkVI7Bf6odne+rW8z3YVxlRLBqOfuqCz/XShv+NiSvGTe4oGd\r\n"
				+ "rZv1Uz6s8SaUgbrOD7CphrUpkXl10jLiOwK77bBQBXXIjiTgReVQlZj3ni9ysvUP\r\n"
				+ "j05uY1zNDU631DQSHUZkPDAv4t5rCS9atoznIGDLgkSRDYLSbGoX7/1qSUg/yZvl\r\n"
				+ "vJ2qfMhgmuzhrTOF4rGNOZmJ/eMarqBu3oRBdpsZzdGQehAoEqoVTgrnhZ7KdWKE\r\n"
				+ "U++EQOj4ZKOR2YyYTXuYGLNZZiJZs9U6GmI32qLnxQIlhl6wxDKvjMs=\r\n" //
				+ "-----END CERTIFICATE-----\r\n";
		final var text = "-----BEGIN PRIVATE KEY-----\r\n" //
				+ "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCy9JCrsvYdROgK\r\n"
				+ "j6xbt3VLzm939nz48qpckrRhpOD/K1JYyXfkMrmwGqSBdkquNtGvUO2raRAWYi3g\r\n"
				+ "tH1fFW6wU2EhadlKd68VHBFZA2SqQo28GdekWSHpxmVmAZC0IeT/1Y61Bhwg/UEp\r\n"
				+ "08tPMgSwBa5zswUS9Ji6C5kVW2puO0FFffKTQLJmRdMMbK0qeqZjEpxxmM5VhV/f\r\n"
				+ "7560lhjEisttvYWCjH0m2O+rCTbetOSVitfQNzY4HjE9K1mXAh56b9bOtw1sdmUL\r\n"
				+ "ilTYE2ZuaH7Sz0Og4+D8KhKPWjKmoZyGMPLd+826t2bGYC/uB3LvOumAHltBob2V\r\n"
				+ "XjnfPc0vAgMBAAECggEAStOeJl0HMcfdKoVWsWbtgcwIqeWD7egxudGR4P47Ihbw\r\n"
				+ "MLXVDrLzF6KcRJG8ukmrtZ2mMzUUB/f3bOyrO3XPuaSziXAj7lJLAA2jZ0/W6KY5\r\n"
				+ "c3xuwYaPept9TIC9C8TcZujZ6fFrE5QxihbH/Q5SDXPitPgW2A00Sx6MXPTDdOFY\r\n"
				+ "858wiggmbPzVPwAIpHL64JfavjlzEYmS6JS5UvSJKb40ESk/9JqaUYmsK8zh/P8k\r\n"
				+ "6pVMNgXFHHhySBxhqfts03rd4i4UyWejKzp531IDda6KBCd9+rIUXJuTST4Ji6MW\r\n"
				+ "M5KWQKLMEkK57rkpYjlNMMfokTZAhSNYVbBTCgygaQKBgQDWrhKs587iqVr4TG5W\r\n"
				+ "RQWmeFY7EauQuNMCFs6Xt2aE0tw+WXnjAw2JH8JROrpGba/nMcL4ewvtgH/2R8c+\r\n"
				+ "T/McSEbMJgLfpfWq/5lxBMR6FH6wEI3qajcowg3YTlu0en25opLKtmtTEQ/cCT9B\r\n"
				+ "szX8VhNxgjYTTPL7gArW+0rIbQKBgQDVZjta9Ul33/A0jnzHKhsbhG8ECaHpvUaa\r\n"
				+ "ClcEDwCfqm05VWwHVmBZc/OGCGo9GBT+WF9DGkuzHvH5hY0exwZszudrc5Nx0Xww\r\n"
				+ "xD5+TEnvyFh575jHiDFbgDRGZBIJ1GTQo1GI+TCkUX7YoOfLLMCFtB1IWwo0XKRk\r\n"
				+ "tZKuYUaiiwKBgQDQPJWc5lXFguTcyLaWM+lhKoTqUx+KB92Vvy2x+JIW8Ln2OLCE\r\n"
				+ "RSfSBN5mEIMzYZ4oshofEKvnOqfk6ocXqFF285zI1W4gJkkAD2J1QFyc1lh0y/n8\r\n"
				+ "kf7jKChp0sgdbluZv1qICx5buscZdlbHkeAh3hSKG/X8Dr36up7JHYPSmQKBgGtz\r\n"
				+ "QXnIPkj82i8se8mw+ts9MMUAsKVH58/SWwQxJag/oSWYTH/ZT0RbbQhVHmFKye9T\r\n"
				+ "wgvAptM3RrHUPD1+C08oU0A4fsp1p6nKdokTkrsIzvv+15fIjMm84RV8d9S5PFYN\r\n"
				+ "lhV5G7PxUQR06KHWE85+5au0I4xABYqsHoJteqqHAoGAP81jDfVL+GKOGhuPMjAA\r\n"
				+ "nH1xCgeQ/l+pKl1TUAlTeVCKA+hsiXmNkFAEUjThrB2Pc0wtJGmUnDsIFIw00lMS\r\n"
				+ "FbH/Wv/sjrjQqlW58RjDnicnrxUCpjs2V+ur8+gaLJhUQW5gSfpqcYxebV7S0nxT\r\n"
				+ "fjOhcSDV8xjblhscXI1V2tg=\r\n" //
				+ "-----END PRIVATE KEY-----\r\n" //
				+ certText;
		final var pem = PemEncoded.parse(text);

		assertTrue(pem.hasNext());
		var key = pem.next();
		assertEquals(KeyType.PRIVATE_KEY, key.getKeyType());
		final var priv = assertInstanceOf(RSAPrivateKey.class, key.asPrivate("RSA"));

		assertTrue(pem.hasNext());
		key = pem.next();
		assertEquals(KeyType.CERTIFICATE, key.getKeyType());
		final var cert = key.asCertificate();
		final var pub = assertInstanceOf(RSAPublicKey.class, cert.getPublicKey());
		assertEquals(priv.getModulus(), pub.getModulus());

		assertFalse(pem.hasNext());

		final var confirm = new StringBuilder();
		PemEncoded.serialize(new KeyPair(pub, priv), cert).forEachRemaining(confirm::append);
		assertEquals(text, confirm.toString().replace("\n", "\r\n"));

		final var confirmNoPub = new StringBuilder();
		PemEncoded.serialize(new KeyPair(null, priv), cert).forEachRemaining(confirmNoPub::append);
		assertEquals(text, confirmNoPub.toString().replace("\n", "\r\n"));

		final var confirmNoPriv = new StringBuilder();
		PemEncoded.serialize(new KeyPair(pub, null), cert).forEachRemaining(confirmNoPriv::append);
		assertEquals(certText, confirmNoPriv.toString().replace("\n", "\r\n"));

		final var uri = uri(certText);
		final var fromUri = PemEncoded.getCertificateChain(uri);
		assertEquals(1, fromUri.length);
		assertEquals(cert, fromUri[0]);
		assertSame(fromUri, PemEncoded.getCertificateChain(uri));
	}

	@Test
	public void testECCertOnlyMarkers()
			throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		final var pem = PemEncoded.parse( //
				"MIIClzCCAhygAwIBAgIURBnmOnYrSqsKrszgC751/Iat0uEwCgYIKoZIzj0EAwIw\r\n" //
						+ "gYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9v\r\n" //
						+ "bWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZT\r\n" //
						+ "VEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5cHQtdGVzdDAgFw0yNDAzMTAxOTE2\r\n" //
						+ "MjRaGA8yMTI0MDMxMTE5MTYyNFowgYAxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJ\r\n" //
						+ "bmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBV\r\n" //
						+ "bml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxGzAZBgNVBAMMEml1LWphdmEtY3J5\r\n" //
						+ "cHQtdGVzdDB2MBAGByqGSM49AgEGBSuBBAAiA2IABB21Lelr9GqaBwPWN9aNn+ms\r\n" //
						+ "rjbWINECr3iEkqnCKMta7Zii6Gg8cjmUiLgVIpPfAXGUIo8Jr6SPH+Vb6845xRVj\r\n" //
						+ "ls4Gd/mhzbs1UeBKORACUCwt2PKWiIJFPXMgTpEY+aNTMFEwHQYDVR0OBBYEFIol\r\n" //
						+ "C3PH9md71NuPiuJQXhDl888QMB8GA1UdIwQYMBaAFIolC3PH9md71NuPiuJQXhDl\r\n" //
						+ "888QMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDaQAwZgIxAKHtm01BrBpO\r\n" //
						+ "+uNkzwxfsk8o5/Y3V31T53VN0N22+IMc2Fo0fX6EiRj7JUINzTJN/QIxAOKD0Dab\r\n" //
						+ "ieNBfzWg9IDvuGnDWNEzN0l6IrnHdnEwVDQUpzFNw8UjGz8ohdztRSVKlQ==\r\n");

		assertTrue(pem.hasNext());
		var key = pem.next();
		assertEquals(KeyType.CERTIFICATE, key.getKeyType());
		assertThrows(IllegalStateException.class, key::asCRL);
		assertInstanceOf(ECPublicKey.class, key.asCertificate().getPublicKey());

		assertThrows(IllegalStateException.class, () -> key.asPrivate("EC"));

		assertFalse(pem.hasNext());
	}

	@Test
	void testCrl() {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		final var pem = PemEncoded.parse( //
				"-----BEGIN X509 CRL-----\n" //
						+ "MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5h\n" //
						+ "MRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJz\n" //
						+ "aXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWph\n" //
						+ "dmEtYXV0aC1wa2kjUGtpU3BpVGVzdF9DQRcNMjQwNDE2MTA0NTM5WhgPMjEyNDA0\n" //
						+ "MTcxMDQ1MzlaMAUGAytlcQNzAC1rkeM6SUWX0un6apmCNwisvs6Hxsy0e4K6D7ou\n" //
						+ "+AXr0kWbeWzdisGRs7Zy0RUY0WXu5KiZ7kbwABDkGfOn0NFdnbA02hu5/V6xvfOa\n" //
						+ "jeDhXM+cmPQ/VFMuJf2tOy+n4TC+DvRMJg5bd8xqgU8Vm04lAA==\n" //
						+ "-----END X509 CRL-----\n");

		assertTrue(pem.hasNext());
		var key = pem.next();
		assertEquals(KeyType.X509_CRL, key.getKeyType());
		final var crl = key.asCRL();
		assertInstanceOf(X509CRL.class, crl);
		assertEquals(crl, PemEncoded.CRL_JSON.fromJson(PemEncoded.CRL_JSON.toJson(crl)));
		assertFalse(pem.hasNext());
	}

}
