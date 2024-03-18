package edu.iu.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;

@SuppressWarnings("javadoc")
public class JwkTest {
	// Includes test cases from RFC-7517 appendices A and B

	@SuppressWarnings("deprecation")
	@Test
	public void testRFC7517_A_1() {
		final var text = "{\"keys\":\n" //
				+ "       [\n" //
				+ "         {\"kty\":\"EC\",\n" //
				+ "          \"crv\":\"P-256\",\n" //
				+ "          \"x\":\"MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4\",\n" //
				+ "          \"y\":\"4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM\",\n" //
				+ "          \"use\":\"enc\",\n" //
				+ "          \"kid\":\"1\"},\n" //
				+ "\n" //
				+ "         {\"kty\":\"RSA\",\n" //
				+ "          \"n\": \"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx" //
				+ "4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs" //
				+ "tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2" //
				+ "QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbI" //
				+ "SD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqb" //
				+ "w0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",\n" //
				+ "          \"e\":\"AQAB\",\n" //
				+ "          \"alg\":\"RS256\",\n" //
				+ "          \"kid\":\"2011-04-29\"}\n" //
				+ "       ]\n" //
				+ "     }\n";
		final var jwks = WebKey.parseJwks(text).collect(Collectors.toMap(WebKey::getId, a -> a));

		final var rsa = jwks.get("2011-04-29");
		assertEquals(Algorithm.RS256, rsa.getAlgorithm());
		assertInstanceOf(RSAPublicKey.class, rsa.getPublicKey());
		assertNull(rsa.getPrivateKey());

		final var ec = jwks.get("1");
		assertEquals(Type.EC_P256, ec.getType());
		assertEquals(Use.ENCRYPT, ec.getUse());
		final var pub = assertInstanceOf(ECPublicKey.class, ec.getPublicKey());
		assertEquals("secp256r1 [NIST P-256,X9.62 prime256v1] (1.2.840.10045.3.1.7)", pub.getParams().toString());
		assertNull(ec.getPrivateKey());

		assertEquals(IuJson.parse(text),
				IuJson.object()
						.add("keys", IuJson.array().add(IuJson.parse(ec.toString())).add(IuJson.parse(rsa.toString())))
						.build());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testRFC7517_A_2() {
		final var text = "{\"keys\":\n" //
				+ "       [\n" //
				+ "         {\"kty\":\"EC\",\n" //
				+ "          \"crv\":\"P-256\",\n" //
				+ "          \"x\":\"MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4\",\n" //
				+ "          \"y\":\"4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM\",\n" //
				+ "          \"d\":\"870MB6gfuTJ4HtUnUvYMyJpr5eUZNP4Bk43bVdj3eAE\",\n" //
				+ "          \"use\":\"enc\",\n" //
				+ "          \"kid\":\"1\"},\n" //
				+ "\n" //
				+ "         {\"kty\":\"RSA\",\n" //
				+ "          \"n\":\"0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4" //
				+ "cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMst" //
				+ "n64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2Q" //
				+ "vzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbIS" //
				+ "D08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw" //
				+ "0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw\",\n" //
				+ "          \"e\":\"AQAB\",\n" //
				+ "          \"d\":\"X4cTteJY_gn4FYPsXB8rdXix5vwsg1FLN5E3EaG6RJoVH-HLLKD9" //
				+ "M7dx5oo7GURknchnrRweUkC7hT5fJLM0WbFAKNLWY2vv7B6NqXSzUvxT0_YSfqij" //
				+ "wp3RTzlBaCxWp4doFk5N2o8Gy_nHNKroADIkJ46pRUohsXywbReAdYaMwFs9tv8d" //
				+ "_cPVY3i07a3t8MN6TNwm0dSawm9v47UiCl3Sk5ZiG7xojPLu4sbg1U2jx4IBTNBz" //
				+ "nbJSzFHK66jT8bgkuqsk0GjskDJk19Z4qwjwbsnn4j2WBii3RL-Us2lGVkY8fkFz" //
				+ "me1z0HbIkfz0Y6mqnOYtqc0X4jfcKoAC8Q\",\n" //
				+ "          \"p\":\"83i-7IvMGXoMXCskv73TKr8637FiO7Z27zv8oj6pbWUQyLPQBQxtPV" //
				+ "nwD20R-60eTDmD2ujnMt5PoqMrm8RfmNhVWDtjjMmCMjOpSXicFHj7XOuVIYQyqV" //
				+ "WlWEh6dN36GVZYk93N8Bc9vY41xy8B9RzzOGVQzXvNEvn7O0nVbfs\",\n" //
				+ "          \"q\":\"3dfOR9cuYq-0S-mkFLzgItgMEfFzB2q3hWehMuG0oCuqnb3vobLyum" //
				+ "qjVZQO1dIrdwgTnCdpYzBcOfW5r370AFXjiWft_NGEiovonizhKpo9VVS78TzFgx" //
				+ "kIdrecRezsZ-1kYd_s1qDbxtkDEgfAITAG9LUnADun4vIcb6yelxk\",\n" //
				+ "          \"dp\":\"G4sPXkc6Ya9y8oJW9_ILj4xuppu0lzi_H7VTkS8xj5SdX3coE0oim" //
				+ "YwxIi2emTAue0UOa5dpgFGyBJ4c8tQ2VF402XRugKDTP8akYhFo5tAA77Qe_Nmtu" //
				+ "YZc3C3m3I24G2GvR5sSDxUyAN2zq8Lfn9EUms6rY3Ob8YeiKkTiBj0\",\n" //
				+ "          \"dq\":\"s9lAH9fggBsoFR8Oac2R_E2gw282rT2kGOAhvIllETE1efrA6huUU" //
				+ "vMfBcMpn8lqeW6vzznYY5SSQF7pMdC_agI3nG8Ibp1BUb0JUiraRNqUfLhcQb_d9" //
				+ "GF4Dh7e74WbRsobRonujTYN1xCaP6TO61jvWrX-L18txXw494Q_cgk\",\n" //
				+ "          \"qi\":\"GyM_p6JrXySiz1toFgKbWV-JdI3jQ4ypu9rbMWx3rQJBfmt0FoYzg" //
				+ "UIZEVFEcOqwemRN81zoDAaa-Bk0KWNGDjJHZDdDmFhW3AN7lI-puxk_mHZGJ11rx" //
				+ "yR8O55XLSe3SPmRfKwZI6yU24ZxvQKFYItdldUKGzO6Ia6zTKhAVRU\",\n" //
				+ "          \"alg\":\"RS256\",\n" //
				+ "          \"kid\":\"2011-04-29\"}\n" //
				+ "       ]\n" //
				+ "     }";
		final var jwks = WebKey.parseJwks(text).collect(Collectors.toMap(WebKey::getId, a -> a));

		final var rsa = jwks.get("2011-04-29");
		assertEquals(Algorithm.RS256, rsa.getAlgorithm());
		final var pub = assertInstanceOf(RSAPublicKey.class, rsa.getPublicKey());
		final var priv = assertInstanceOf(RSAPrivateCrtKey.class, rsa.getPrivateKey());
		assertEquals(pub.getModulus(), priv.getModulus());
		assertEquals(pub.getPublicExponent(), priv.getPublicExponent());

		final var ec = jwks.get("1");
		assertEquals(Type.EC_P256, ec.getType());
		assertEquals(Use.ENCRYPT, ec.getUse());
		final var epub = assertInstanceOf(ECPublicKey.class, ec.getPublicKey());
		final var epriv = assertInstanceOf(ECPrivateKey.class, ec.getPrivateKey());
		assertEquals("secp256r1 [NIST P-256,X9.62 prime256v1] (1.2.840.10045.3.1.7)", epub.getParams().toString());
		assertEquals(epub.getParams(), epriv.getParams());

		assertEquals(IuJson.parse(text),
				IuJson.object()
						.add("keys", IuJson.array().add(IuJson.parse(ec.toString())).add(IuJson.parse(rsa.toString())))
						.build());
	}

	@Test
	public void testRFC7517_A_3() {
		final var text = "{\"keys\":\n" //
				+ "       [\n" //
				+ "         {\"kty\":\"oct\",\n" //
				+ "          \"alg\":\"A128KW\",\n" //
				+ "          \"k\":\"GawgguFyGrWKav7AX4VKUg\"},\n" //
				+ "\n" //
				+ "         {\"kty\":\"oct\",\n" //
				+ "          \"k\":\"AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow\",\n" //
				+ "          \"kid\":\"HMAC key used in JWS spec Appendix A.1 example\"}\n" //
				+ "       ]\n" //
				+ "     }";
		final var jwks = WebKey.parseJwks(text).toArray(WebKey[]::new);
		assertEquals(Type.RAW, jwks[0].getType());
		assertEquals(Algorithm.A128KW, jwks[0].getAlgorithm());
		assertEquals(16, jwks[0].getKey().length);
		assertEquals(Type.RAW, jwks[1].getType());
		assertEquals("HMAC key used in JWS spec Appendix A.1 example", jwks[1].getId());
		assertEquals(64, jwks[1].getKey().length);

		assertEquals(IuJson.parse(text),
				IuJson.object().add("keys",
						IuJson.array().add(IuJson.parse(jwks[0].toString())).add(IuJson.parse(jwks[1].toString())))
						.build());
	}

	@Test
	public void testRFC7517_B() {
		final var text = "{\"kty\":\"RSA\",\n" //
				+ "      \"use\":\"sig\",\n" //
				+ "      \"kid\":\"1b94c\",\n" //
				+ "      \"n\":\"vrjOfz9Ccdgx5nQudyhdoR17V-IubWMeOZCwX_jj0hgAsz2J_pqYW08" //
				+ "PLbK_PdiVGKPrqzmDIsLI7sA25VEnHU1uCLNwBuUiCO11_-7dYbsr4iJmG0Q" //
				+ "u2j8DsVyT1azpJC_NG84Ty5KKthuCaPod7iI7w0LK9orSMhBEwwZDCxTWq4a" //
				+ "YWAchc8t-emd9qOvWtVMDC2BXksRngh6X5bUYLy6AyHKvj-nUy1wgzjYQDwH" //
				+ "MTplCoLtU-o-8SNnZ1tmRoGE9uJkBLdh5gFENabWnU5m1ZqZPdwS-qo-meMv" //
				+ "VfJb6jJVWRpl2SUtCnYG2C32qvbWbjZ_jBPD5eunqsIo1vQ\",\n" //
				+ "      \"e\":\"AQAB\",\n" //
				+ "      \"x5c\":\n" //
				+ "       [\"MIIDQjCCAiqgAwIBAgIGATz/FuLiMA0GCSqGSIb3DQEBBQUAMGIxCzAJB" //
				+ "gNVBAYTAlVTMQswCQYDVQQIEwJDTzEPMA0GA1UEBxMGRGVudmVyMRwwGgYD" //
				+ "VQQKExNQaW5nIElkZW50aXR5IENvcnAuMRcwFQYDVQQDEw5CcmlhbiBDYW1" //
				+ "wYmVsbDAeFw0xMzAyMjEyMzI5MTVaFw0xODA4MTQyMjI5MTVaMGIxCzAJBg" //
				+ "NVBAYTAlVTMQswCQYDVQQIEwJDTzEPMA0GA1UEBxMGRGVudmVyMRwwGgYDV" //
				+ "QQKExNQaW5nIElkZW50aXR5IENvcnAuMRcwFQYDVQQDEw5CcmlhbiBDYW1w" //
				+ "YmVsbDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL64zn8/QnH" //
				+ "YMeZ0LncoXaEde1fiLm1jHjmQsF/449IYALM9if6amFtPDy2yvz3YlRij66" //
				+ "s5gyLCyO7ANuVRJx1NbgizcAblIgjtdf/u3WG7K+IiZhtELto/A7Fck9Ws6" //
				+ "SQvzRvOE8uSirYbgmj6He4iO8NCyvaK0jIQRMMGQwsU1quGmFgHIXPLfnpn" //
				+ "fajr1rVTAwtgV5LEZ4Iel+W1GC8ugMhyr4/p1MtcIM42EA8BzE6ZQqC7VPq" //
				+ "PvEjZ2dbZkaBhPbiZAS3YeYBRDWm1p1OZtWamT3cEvqqPpnjL1XyW+oyVVk" //
				+ "aZdklLQp2Btgt9qr21m42f4wTw+Xrp6rCKNb0CAwEAATANBgkqhkiG9w0BA" //
				+ "QUFAAOCAQEAh8zGlfSlcI0o3rYDPBB07aXNswb4ECNIKG0CETTUxmXl9KUL" //
				+ "+9gGlqCz5iWLOgWsnrcKcY0vXPG9J1r9AqBNTqNgHq2G03X09266X5CpOe1" //
				+ "zFo+Owb1zxtp3PehFdfQJ610CDLEaS9V9Rqp17hCyybEpOGVwe8fnk+fbEL" //
				+ "2Bo3UPGrpsHzUoaGpDftmWssZkhpBJKVMJyf/RuP2SmmaIzmnw9JiSlYhzo" //
				+ "4tpzd5rFXhjRbg4zW9C+2qok+2+qDM1iJ684gPHMIY8aLWrdgQTxkumGmTq" //
				+ "gawR+N5MDtdPTEQ0XfIBc2cJEUyMTY5MPvACWpkA6SdS4xSvdXK3IVfOWA==\"]\n" //
				+ "     }";
		final var jwk = WebKey.parse(text);
		assertEquals("1b94c", jwk.getId());
		assertEquals(Type.RSA, jwk.getType());
		assertEquals(Use.SIGN, jwk.getUse());
		final var pub = assertInstanceOf(RSAPublicKey.class, jwk.getPublicKey());

		assertEquals(pub, jwk.getCertificateChain()[0].getPublicKey());
		assertEquals(IuJson.parse(text), IuJson.parse(jwk.toString()));
	}

	@Test
	public void testRSAFromPEMNoCert() throws NoSuchAlgorithmException, InvalidKeySpecException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl genrsa
		final var rsa = WebKey.builder().type(Type.RSA).pem( //
				"-----BEGIN PRIVATE KEY-----\r\n"
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
						+ "-----END PRIVATE KEY-----\r\n").build();

		final var pub = assertInstanceOf(RSAPublicKey.class, rsa.getPublicKey());
		final var priv = assertInstanceOf(RSAPrivateCrtKey.class, rsa.getPrivateKey());
		assertEquals(pub.getModulus(), priv.getModulus());
		assertEquals(pub.getPublicExponent(), priv.getPublicExponent());
	}

	@Test
	public void testECFromPEMWithCert()
			throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidAlgorithmParameterException {
		// This is a sample key for testing and demonstration purpose only.
		// ---- NOT FOR PRODUCTION USE -----
		// $ openssl ecparam -genkey -name secp384r1 | \
		// openssl pkcs8 -topk8 -nocrypt > /tmp/k
		// $ openssl ec -no_public < /tmp/k | openssl pkcs8 -topk8 -nocrypt
		// $ openssl req -days 410 -x509 -key /tmp/k
		final var ec = WebKey.builder().type(Type.EC_P384).pem("-----BEGIN PRIVATE KEY-----\r\n" //
				+ "ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDDrK9MiCo6waL7wOKbfvrsq\r\n" //
				+ "IMldpggDYj9UyDWEiapLgXG/IKS0tFs68srJBzHGSqc=\r\n" //
				+ "-----END PRIVATE KEY-----\r\n" //
				+ "-----BEGIN CERTIFICATE-----\r\n" //
				+ "MIIClzCCAhygAwIBAgIURBnmOnYrSqsKrszgC751/Iat0uEwCgYIKoZIzj0EAwIw\r\n" //
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
				+ "ieNBfzWg9IDvuGnDWNEzN0l6IrnHdnEwVDQUpzFNw8UjGz8ohdztRSVKlQ==\r\n" //
				+ "-----END CERTIFICATE-----\r\n").build();

		assertEquals(Type.EC_P384, ec.getType());
		final var epub = assertInstanceOf(ECPublicKey.class, ec.getPublicKey());
		final var epriv = assertInstanceOf(ECPrivateKey.class, ec.getPrivateKey());
		assertEquals("secp384r1 [NIST P-384] (1.3.132.0.34)", epub.getParams().toString());
		assertEquals(epub.getParams(), epriv.getParams());
	}

}
