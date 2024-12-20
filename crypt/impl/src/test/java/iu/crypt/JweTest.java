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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Level;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebCryptoHeader.Param;
import edu.iu.crypt.WebEncryption;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class JweTest {

	private static class Builder extends JoseBuilder<Builder> {
		private Builder(Algorithm algorithm, Encryption encryption) {
			super(algorithm);
			param(Param.ENCRYPTION, encryption);
		}

		private Jose build() {
			return new Jose(toJson());
		}
	}

	@Test
	public void testRFC7516_A_1() throws Exception {
		final var message = "The true sign of intelligence is not knowledge but imagination.";
		byte[] plaintext = message.getBytes("UTF-8");
		assertArrayEquals(new byte[] { 84, 104, 101, 32, 116, 114, 117, 101, 32, 115, 105, 103, 110, 32, 111, 102, 32,
				105, 110, 116, 101, 108, 108, 105, 103, 101, 110, 99, 101, 32, 105, 115, 32, 110, 111, 116, 32, 107,
				110, 111, 119, 108, 101, 100, 103, 101, 32, 98, 117, 116, 32, 105, 109, 97, 103, 105, 110, 97, 116, 105,
				111, 110, 46 }, plaintext);

		final var alg = Algorithm.RSA_OAEP;
		final var enc = Encryption.A256GCM;
		final var jose = new Builder(alg, enc).algorithm(alg).build().toJson(a -> true);
		final var protectedHeader = IuText.base64Url(IuText.utf8(jose.toString()));
		assertEquals("eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ", protectedHeader, jose::toString);

		final var cek = new byte[] { (byte) 177, (byte) 161, (byte) 244, (byte) 128, 84, (byte) 143, (byte) 225, 115,
				63, (byte) 180, 3, (byte) 255, 107, (byte) 154, (byte) 212, (byte) 246, (byte) 138, 7, 110, 91, 112, 46,
				34, 105, 47, (byte) 130, (byte) 203, 46, 122, (byte) 234, 64, (byte) 252 };

		final var rsa = WebKey.parse("{\"kty\":\"RSA\",\n" //
				+ "      \"n\":\"oahUIoWw0K0usKNuOR6H4wkf4oBUXHTxRvgb48E-BVvxkeDNjbC4he8rUW" //
				+ "cJoZmds2h7M70imEVhRU5djINXtqllXI4DFqcI1DgjT9LewND8MW2Krf3S" //
				+ "psk_ZkoFnilakGygTwpZ3uesH-PFABNIUYpOiN15dsQRkgr0vEhxN92i2a" //
				+ "sbOenSZeyaxziK72UwxrrKoExv6kc5twXTq4h-QChLOln0_mtUZwfsRaMS" //
				+ "tPs6mS6XrgxnxbWhojf663tuEQueGC-FCMfra36C9knDFGzKsNa7LZK2dj" //
				+ "YgyD3JR_MB_4NUJW_TqOQtwHYbxevoJArm-L5StowjzGy-_bq6Gw\",\n" //
				+ "      \"e\":\"AQAB\",\n" //
				+ "      \"d\":\"kLdtIj6GbDks_ApCSTYQtelcNttlKiOyPzMrXHeI-yk1F7-kpDxY4-WY5N" //
				+ "WV5KntaEeXS1j82E375xxhWMHXyvjYecPT9fpwR_M9gV8n9Hrh2anTpTD9" //
				+ "3Dt62ypW3yDsJzBnTnrYu1iwWRgBKrEYY46qAZIrA2xAwnm2X7uGR1hghk" //
				+ "qDp0Vqj3kbSCz1XyfCs6_LehBwtxHIyh8Ripy40p24moOAbgxVw3rxT_vl" //
				+ "t3UVe4WO3JkJOzlpUf-KTVI2Ptgm-dARxTEtE-id-4OJr0h-K-VFs3VSnd" //
				+ "VTIznSxfyrj8ILL6MG_Uv8YAu7VILSB3lOW085-4qE3DzgrTjgyQ\",\n" //
				+ "      \"p\":\"1r52Xk46c-LsfB5P442p7atdPUrxQSy4mti_tZI3Mgf2EuFVbUoDBvaRQ-" //
				+ "SWxkbkmoEzL7JXroSBjSrK3YIQgYdMgyAEPTPjXv_hI2_1eTSPVZfzL0lf" //
				+ "fNn03IXqWF5MDFuoUYE0hzb2vhrlN_rKrbfDIwUbTrjjgieRbwC6Cl0\",\n" //
				+ "      \"q\":\"wLb35x7hmQWZsWJmB_vle87ihgZ19S8lBEROLIsZG4ayZVe9Hi9gDVCOBm" //
				+ "UDdaDYVTSNx_8Fyw1YYa9XGrGnDew00J28cRUoeBB_jKI1oma0Orv1T9aX" //
				+ "IWxKwd4gvxFImOWr3QRL9KEBRzk2RatUBnmDZJTIAfwTs0g68UZHvtc\",\n" //
				+ "      \"dp\":\"ZK-YwE7diUh0qR1tR7w8WHtolDx3MZ_OTowiFvgfeQ3SiresXjm9gZ5KL" //
				+ "hMXvo-uz-KUJWDxS5pFQ_M0evdo1dKiRTjVw_x4NyqyXPM5nULPkcpU827" //
				+ "rnpZzAJKpdhWAgqrXGKAECQH0Xt4taznjnd_zVpAmZZq60WPMBMfKcuE\",\n" //
				+ "      \"dq\":\"Dq0gfgJ1DdFGXiLvQEZnuKEN0UUmsJBxkjydc3j4ZYdBiMRAy86x0vHCj" //
				+ "ywcMlYYg4yoC4YZa9hNVcsjqA3FeiL19rk8g6Qn29Tt0cj8qqyFpz9vNDB" //
				+ "UfCAiJVeESOjJDZPYHdHY8v1b-o-Z2X5tvLx-TCekf7oxyeKDUqKWjis\",\n" //
				+ "      \"qi\":\"VIMpMYbPf47dT1w_zDUXfPimsSegnMOA1zTaX7aGk_8urY6R8-ZW1FxU7" //
				+ "AlWAyLWybqq6t16VFd7hQd0y6flUK4SlOydB61gwanOsXGOAOv82cHq0E3" //
				+ "eL4HrtZkUuKvnPrMnsUUFlfUdybVzxyjz9JF_XyaY14ardLSjf4L_FNY\"\n" //
				+ "     }\n");

		final var keyCipher = Cipher.getInstance("RSA");
		keyCipher.init(Cipher.ENCRYPT_MODE, rsa.getPublicKey());
		final var encryptedKey = keyCipher.doFinal(cek);
		final var keyUnwrapCipher = Cipher.getInstance("RSA");
		keyUnwrapCipher.init(Cipher.UNWRAP_MODE, rsa.getPrivateKey());
		assertArrayEquals(cek, keyUnwrapCipher.unwrap(encryptedKey, "AES", Cipher.SECRET_KEY).getEncoded());

		final var iv = new byte[] { (byte) 227, (byte) 197, 117, (byte) 252, 2, (byte) 219, (byte) 233, 68, (byte) 180,
				(byte) 225, 77, (byte) 219 };
		assertEquals("48V1_ALb6US04U3b", IuText.base64Url(iv));

		final var aad = protectedHeader.getBytes("US-ASCII");
		assertArrayEquals(new byte[] { 101, 121, 74, 104, 98, 71, 99, 105, 79, 105, 74, 83, 85, 48, 69, 116, 84, 48, 70,
				70, 85, 67, 73, 115, 73, 109, 86, 117, 89, 121, 73, 54, 73, 107, 69, 121, 78, 84, 90, 72, 81, 48, 48,
				105, 102, 81 }, aad);

		final var gcmSpec = new GCMParameterSpec(128, iv);
		final var messageCipher = Cipher.getInstance(enc.algorithm);
		messageCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), gcmSpec);
		messageCipher.updateAAD(aad);
		final var encryptedData = messageCipher.doFinal(plaintext);
		final var cipherText = Arrays.copyOf(encryptedData, encryptedData.length - 16);
		assertArrayEquals(
				new byte[] { (byte) 229, (byte) 236, (byte) 166, (byte) 241, (byte) 53, (byte) 191, (byte) 115,
						(byte) 196, (byte) 174, (byte) 43, (byte) 73, (byte) 109, (byte) 39, (byte) 122, (byte) 233,
						(byte) 96, (byte) 140, (byte) 206, (byte) 120, (byte) 52, (byte) 51, (byte) 237, (byte) 48,
						(byte) 11, (byte) 190, (byte) 219, (byte) 186, (byte) 80, (byte) 111, (byte) 104, (byte) 50,
						(byte) 142, (byte) 47, (byte) 167, (byte) 59, (byte) 61, (byte) 181, (byte) 127, (byte) 196,
						(byte) 21, (byte) 40, (byte) 82, (byte) 242, (byte) 32, (byte) 123, (byte) 143, (byte) 168,
						(byte) 226, (byte) 73, (byte) 216, (byte) 176, (byte) 144, (byte) 138, (byte) 247, (byte) 106,
						(byte) 60, (byte) 16, (byte) 205, (byte) 160, (byte) 109, (byte) 64, (byte) 63, (byte) 192 },
				cipherText);
		assertEquals("5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX_EFShS8iB7j6jiSdiwkIr3ajwQzaBtQD_A",
				IuText.base64Url(cipherText));

		final var tag = Arrays.copyOfRange(encryptedData, encryptedData.length - 16, encryptedData.length);
		assertArrayEquals(new byte[] { 92, 80, 104, 49, (byte) 133, 25, (byte) 161, (byte) 215, (byte) 173, 101,
				(byte) 219, (byte) 211, (byte) 136, 91, (byte) 210, (byte) 145 }, tag);
		assertEquals("XFBoMYUZodetZdvTiFvSkQ", IuText.base64Url(tag));
	}

	@Test
	public void testRFC7516_A_2() throws Exception {
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);

		final var jwe = WebEncryption.parse("eyJhbGciOiJSU0ExXzUiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0." //
				+ "UGhIOguC7IuEvf_NPVaXsGMoLOmwvc1GyqlIKOK1nN94nHPoltGRhWhw7Zx0-kFm" //
				+ "1NJn8LE9XShH59_i8J0PH5ZZyNfGy2xGdULU7sHNF6Gp2vPLgNZ__deLKxGHZ7Pc" //
				+ "HALUzoOegEI-8E66jX2E4zyJKx-YxzZIItRzC5hlRirb6Y5Cl_p-ko3YvkkysZIF" //
				+ "NPccxRU7qve1WYPxqbb2Yw8kZqa2rMWI5ng8OtvzlV7elprCbuPhcCdZ6XDP0_F8" //
				+ "rkXds2vE4X-ncOIM8hAYHHi29NX0mcKiRaD0-D-ljQTP-cFPgwCp6X-nZZd9OHBv" //
				+ "-B3oWh2TbqmScqXMR4gp_A." //
				+ "AxY8DCtDaGlsbGljb3RoZQ." //
				+ "KDlTtXchhZTGufMYmOYGS4HffxPSUrfmqCHXaI9wOGY." //
				+ "9hH0vgRfYgPnAHOd8stkvw");

		final var key = WebKey.parse("{\"kty\":\"RSA\"," //
				+ "      \"n\":\"sXchDaQebHnPiGvyDOAT4saGEUetSyo9MKLOoWFsueri23bOdgWp4Dy1Wl" //
				+ "UzewbgBHod5pcM9H95GQRV3JDXboIRROSBigeC5yjU1hGzHHyXss8UDpre" //
				+ "cbAYxknTcQkhslANGRUZmdTOQ5qTRsLAt6BTYuyvVRdhS8exSZEy_c4gs_" //
				+ "7svlJJQ4H9_NxsiIoLwAEk7-Q3UXERGYw_75IDrGA84-lA_-Ct4eTlXHBI" //
				+ "Y2EaV7t7LjJaynVJCpkv4LKjTTAumiGUIuQhrNhZLuF_RJLqHpM2kgWFLU" //
				+ "7-VTdL1VbC2tejvcI2BlMkEpk1BzBZI0KQB0GaDWFLN-aEAw3vRw\"," //
				+ "      \"e\":\"AQAB\"," //
				+ "      \"d\":\"VFCWOqXr8nvZNyaaJLXdnNPXZKRaWCjkU5Q2egQQpTBMwhprMzWzpR8Sxq" //
				+ "1OPThh_J6MUD8Z35wky9b8eEO0pwNS8xlh1lOFRRBoNqDIKVOku0aZb-ry" //
				+ "nq8cxjDTLZQ6Fz7jSjR1Klop-YKaUHc9GsEofQqYruPhzSA-QgajZGPbE_" //
				+ "0ZaVDJHfyd7UUBUKunFMScbflYAAOYJqVIVwaYR5zWEEceUjNnTNo_CVSj" //
				+ "-VvXLO5VZfCUAVLgW4dpf1SrtZjSt34YLsRarSb127reG_DUwg9Ch-Kyvj" //
				+ "T1SkHgUWRVGcyly7uvVGRSDwsXypdrNinPA4jlhoNdizK2zF2CWQ\"," //
				+ "      \"p\":\"9gY2w6I6S6L0juEKsbeDAwpd9WMfgqFoeA9vEyEUuk4kLwBKcoe1x4HG68" //
				+ "ik918hdDSE9vDQSccA3xXHOAFOPJ8R9EeIAbTi1VwBYnbTp87X-xcPWlEP" //
				+ "krdoUKW60tgs1aNd_Nnc9LEVVPMS390zbFxt8TN_biaBgelNgbC95sM\"," //
				+ "      \"q\":\"uKlCKvKv_ZJMVcdIs5vVSU_6cPtYI1ljWytExV_skstvRSNi9r66jdd9-y" //
				+ "BhVfuG4shsp2j7rGnIio901RBeHo6TPKWVVykPu1iYhQXw1jIABfw-MVsN" //
				+ "-3bQ76WLdt2SDxsHs7q7zPyUyHXmps7ycZ5c72wGkUwNOjYelmkiNS0\"," //
				+ "      \"dp\":\"w0kZbV63cVRvVX6yk3C8cMxo2qCM4Y8nsq1lmMSYhG4EcL6FWbX5h9yuv" //
				+ "ngs4iLEFk6eALoUS4vIWEwcL4txw9LsWH_zKI-hwoReoP77cOdSL4AVcra" //
				+ "Hawlkpyd2TWjE5evgbhWtOxnZee3cXJBkAi64Ik6jZxbvk-RR3pEhnCs\"," //
				+ "      \"dq\":\"o_8V14SezckO6CNLKs_btPdFiO9_kC1DsuUTd2LAfIIVeMZ7jn1Gus_Ff" //
				+ "7B7IVx3p5KuBGOVF8L-qifLb6nQnLysgHDh132NDioZkhH7mI7hPG-PYE_" //
				+ "odApKdnqECHWw0J-F0JWnUd6D2B_1TvF9mXA2Qx-iGYn8OVV1Bsmp6qU\"," //
				+ "      \"qi\":\"eNho5yRBEBxhGBtQRww9QirZsB66TrfFReG_CcteI1aCneT0ELGhYlRlC" //
				+ "tUkTRclIfuEPmNsNDPbLoLqqCVznFbvdB7x-Tl-m0l_eFTj2KiqwGqE9PZ" //
				+ "B9nNTwMVvH3VRRSLWACvPnSiwP8N5Usy-WRXS-V7TbpxIhvepTfE0NNo\"" //
				+ "     }");

		assertEquals("Live long and prosper.", jwe.decryptText(key));
	}

	@Test
	public void testRFC7516_A_3() throws Exception {
		String message = "Live long and prosper.";
		byte[] plaintext = message.getBytes("UTF-8");
		assertArrayEquals(new byte[] { 76, 105, 118, 101, 32, 108, 111, 110, 103, 32, 97, 110, 100, 32, 112, 114, 111,
				115, 112, 101, 114, 46 }, plaintext);

		final var alg = Algorithm.A128KW;
		final var enc = Encryption.AES_128_CBC_HMAC_SHA_256;
		final var jose = new Builder(alg, enc).build().toJson(a -> true);
		final var protectedHeader = IuText.base64Url(IuText.utf8(jose.toString()));
		assertEquals("eyJhbGciOiJBMTI4S1ciLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0", protectedHeader, jose::toString);

		final var cek = new byte[] { 4, (byte) 211, 31, (byte) 197, 84, (byte) 157, (byte) 252, (byte) 254, 11, 100,
				(byte) 157, (byte) 250, 63, (byte) 170, 106, (byte) 206, 107, 124, (byte) 212, 45, 111, 107, 9,
				(byte) 219, (byte) 200, (byte) 177, 0, (byte) 240, (byte) 143, (byte) 156, 44, (byte) 207 };

		final var secretKey = WebKey.parse("{\"kty\":\"oct\",\r\n" //
				+ "      \"k\":\"GawgguFyGrWKav7AX4VKUg\"\r\n" //
				+ "     }");

		final var cipher = Cipher.getInstance("AESWrap");
		cipher.init(Cipher.WRAP_MODE, new SecretKeySpec(secretKey.getKey(), "AES"));
		final var encryptedKey = cipher.wrap(new SecretKeySpec(cek, "AES"));
		assertArrayEquals(new byte[] { (byte) 232, (byte) 160, 123, (byte) 211, (byte) 183, 76, (byte) 245, (byte) 132,
				(byte) 200, (byte) 128, 123, 75, (byte) 190, (byte) 216, 22, 67, (byte) 201, (byte) 138, (byte) 193,
				(byte) 186, 9, 91, 122, 31, (byte) 246, 90, 28, (byte) 139, 57, 3, 76, 124, (byte) 193, 11, 98, 37,
				(byte) 173, 61, 104, 57 }, encryptedKey);
		assertEquals("6KB707dM9YTIgHtLvtgWQ8mKwboJW3of9locizkDTHzBC2IlrT1oOQ", IuText.base64Url(encryptedKey));

		final var iv = new byte[] { 3, 22, 60, 12, 43, 67, 104, 105, 108, 108, 105, 99, 111, 116, 104, 101 };
		assertEquals("AxY8DCtDaGlsbGljb3RoZQ", IuText.base64Url(iv));

		final var aad = IuText.ascii(protectedHeader);
		assertArrayEquals(new byte[] { 101, 121, 74, 104, 98, 71, 99, 105, 79, 105, 74, 66, 77, 84, 73, 52, 83, 49, 99,
				105, 76, 67, 74, 108, 98, 109, 77, 105, 79, 105, 74, 66, 77, 84, 73, 52, 81, 48, 74, 68, 76, 85, 104,
				84, 77, 106, 85, 50, 73, 110, 48 }, aad);

		final var macKey = Arrays.copyOf(cek, alg.size / 8);
		assertArrayEquals(new byte[] { 4, (byte) 211, 31, (byte) 197, 84, (byte) 157, (byte) 252, (byte) 254, 11, 100,
				(byte) 157, (byte) 250, 63, (byte) 170, 106, (byte) 206 }, macKey);

		final var encKey = Arrays.copyOfRange(cek, alg.size / 8, alg.size / 4);
		assertArrayEquals(new byte[] { 107, 124, (byte) 212, 45, 111, 107, 9, (byte) 219, (byte) 200, (byte) 177, 0,
				(byte) 240, (byte) 143, (byte) 156, 44, (byte) 207 }, encKey);

		final var messageCipher = Cipher.getInstance(enc.algorithm);
		messageCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKey, "AES"), new IvParameterSpec(iv));
		final var cipherText = messageCipher.doFinal(plaintext);
		assertArrayEquals(
				new byte[] { 40, 57, 83, (byte) 181, 119, 33, (byte) 133, (byte) 148, (byte) 198, (byte) 185,
						(byte) 243, 24, (byte) 152, (byte) 230, 6, 75, (byte) 129, (byte) 223, 127, 19, (byte) 210, 82,
						(byte) 183, (byte) 230, (byte) 168, 33, (byte) 215, 104, (byte) 143, 112, 56, 102 },
				cipherText);

		final var cat = ByteBuffer.wrap(new byte[aad.length + iv.length + cipherText.length + 8]);
		cat.put(aad);
		cat.put(iv);
		cat.put(cipherText);
		EncodingUtils.bigEndian((long) aad.length * 8L, cat);
		assertArrayEquals(new byte[] { 101, 121, 74, 104, 98, 71, 99, 105, 79, 105, 74, 66, 77, 84, 73, 52, 83, 49, 99,
				105, 76, 67, 74, 108, 98, 109, 77, 105, 79, 105, 74, 66, 77, 84, 73, 52, 81, 48, 74, 68, 76, 85, 104,
				84, 77, 106, 85, 50, 73, 110, 48, 3, 22, 60, 12, 43, 67, 104, 105, 108, 108, 105, 99, 111, 116, 104,
				101, 40, 57, 83, (byte) 181, 119, 33, (byte) 133, (byte) 148, (byte) 198, (byte) 185, (byte) 243, 24,
				(byte) 152, (byte) 230, 6, 75, (byte) 129, (byte) 223, 127, 19, (byte) 210, 82, (byte) 183, (byte) 230,
				(byte) 168, 33, (byte) 215, 104, (byte) 143, 112, 56, 102, 0, 0, 0, 0, 0, 0, 1, (byte) 152 },
				cat.array());

		final var mac = Mac.getInstance(enc.mac);
		mac.init(new SecretKeySpec(macKey, enc.mac));
		final var hash = mac.doFinal(cat.array());

		assertArrayEquals(
				new byte[] { 83, 73, (byte) 191, 98, 104, (byte) 205, (byte) 211, (byte) 128, (byte) 201, (byte) 189,
						(byte) 199, (byte) 133, 32, 38, (byte) 194, 85, 9, 84, (byte) 229, (byte) 201, (byte) 219,
						(byte) 135, 44, (byte) 252, (byte) 145, 102, (byte) 179, (byte) 140, 105, 86, (byte) 229, 116 },
				hash);

		final var tag = Arrays.copyOf(hash, enc.size / 16);
		assertArrayEquals(new byte[] { 83, 73, (byte) 191, 98, 104, (byte) 205, (byte) 211, (byte) 128, (byte) 201,
				(byte) 189, (byte) 199, (byte) 133, 32, 38, (byte) 194, 85 }, tag);

		assertEquals("KDlTtXchhZTGufMYmOYGS4HffxPSUrfmqCHXaI9wOGY", IuText.base64Url(cipherText));
		assertEquals("U0m_YmjN04DJvceFICbCVQ", IuText.base64Url(tag));
	}

	@Test
	public void testFlattenedAndNotFlattened() {
		assertThrows(IllegalArgumentException.class, () -> new Jwe(
				IuJson.object().add("header", IuJson.object()).add("recipients", IuJson.array()).build().toString()));
	}

	@Test
	public void testExcessCompact() {
		assertThrows(IllegalArgumentException.class,
				() -> new Jwe(WebEncryption.builder(Encryption.A128GCM).compact().addRecipient(Algorithm.DIRECT)
						.key(WebKey.ephemeral(Encryption.A128GCM)).encrypt("foo").compact() + ".foo"));
	}

	@Test
	public void testCompactNoAdditional() {
		assertThrows(IllegalStateException.class,
				() -> WebEncryption.builder(Encryption.A128GCM).aad(new byte[] { 1, 2, 3 })
						.addRecipient(Algorithm.DIRECT).key(WebKey.ephemeral(Encryption.A128GCM)).encrypt("foo")
						.compact());
	}

	@Test
	public void testWrongKey() {
		final var k1 = WebKey.ephemeral(Algorithm.A128KW);
		final var k2 = WebKey.ephemeral(Algorithm.A128KW);
		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		assertThrows(IllegalStateException.class, () -> WebEncryption.builder(Encryption.A128GCM)
				.addRecipient(Algorithm.A128KW).key(k1).encrypt("foo").decryptText(k2));
	}

	@Test
	public void testPerRecipient() {
		final var key = WebKey.ephemeral(Encryption.A128GCM);
		final var jwe = WebEncryption.builder(Encryption.A128GCM).addRecipient(Algorithm.DIRECT).key(key).then()
				.addRecipient(Algorithm.DIRECT).key(key).encrypt("foo");
		assertEquals(IuJson.array().add(IuJson.object()).add(IuJson.object()).build(),
				IuJson.parse(jwe.toString()).asJsonObject().getJsonArray("recipients"));
		assertEquals(IuJson.parse(jwe.toString()), IuJson.parse(Jwe.JSON.fromJson(Jwe.JSON.toJson(jwe)).toString()));

		final var jwe2 = WebEncryption.builder(Encryption.A128GCM).compact().addRecipient(Algorithm.DIRECT).key(key)
				.encrypt("foo");
		assertTrue(jwe.isDeflate());
		assertNotNull(jwe.getInitializationVector());
		assertNotNull(jwe.getCipherText());
		assertNotNull(jwe.getAuthenticationTag());
		assertNull(jwe.getAdditionalData());
		assertNull(IuJson.parse(jwe2.toString()).asJsonObject().getJsonArray("recipients"));
		assertNull(IuJson.parse(jwe2.toString()).asJsonObject().getJsonObject("header"));
		assertEquals(IuJson.parse(jwe2.toString()), IuJson.parse(Jwe.JSON.fromJson(Jwe.JSON.toJson(jwe2)).toString()));

		final var jwe3 = WebEncryption.builder(Encryption.A128GCM).protect(Param.ENCRYPTION, Param.ZIP, Param.ALGORITHM)
				.addRecipient(Algorithm.DIRECT).keyId(IdGenerator.generateId()).key(key).then()
				.addRecipient(Algorithm.DIRECT).keyId(IdGenerator.generateId()).key(key).encrypt("foo");
		assertNotNull(IuJson.parse(jwe3.toString()).asJsonObject().getJsonArray("recipients"));
		assertNull(IuJson.parse(jwe3.toString()).asJsonObject().getJsonObject("unprotected"));
		assertEquals(IuJson.parse(jwe3.toString()), IuJson.parse(Jwe.JSON.fromJson(Jwe.JSON.toJson(jwe3)).toString()));

	}

	@Test
	public void testValidRecipients() {
		final var key = WebKey.ephemeral(Encryption.A192GCM);
		assertThrows(IllegalArgumentException.class,
				() -> WebEncryption.builder(Encryption.A128GCM).addRecipient(Algorithm.DIRECT).key(key).encrypt("foo"));
	}

	@Test
	public void testInvalidVector() throws Exception {
		final var jwk = WebKey.ephemeral(Encryption.AES_128_CBC_HMAC_SHA_256);
		final var id = IdGenerator.generateId();
		final var jwe = WebEncryption.builder(Encryption.AES_128_CBC_HMAC_SHA_256) //
				.addRecipient(Algorithm.DIRECT).key(jwk).encrypt(id);
		final var serialized = IuJson.parse(jwe.toString()).asJsonObject();

		final var iv = Arrays.copyOf(CryptJsonAdapters.B64URL.fromJson(serialized.get("iv")), 12);
		final var cipherText = CryptJsonAdapters.B64URL.fromJson(serialized.get("cipher_text"));
		final var macInput = ByteBuffer.wrap(new byte[iv.length + cipherText.length + 8]);
		macInput.put(iv);
		macInput.put(cipherText);
		EncodingUtils.bigEndian((long) 0L, macInput);
		final var mac = Mac.getInstance(Encryption.AES_128_CBC_HMAC_SHA_256.mac);
		mac.init(new SecretKeySpec(Arrays.copyOfRange(jwk.getKey(), 0, jwk.getKey().length / 2),
				Encryption.AES_128_CBC_HMAC_SHA_256.mac));

		final var b = IuJson.object(serialized);
		b.add("iv", IuText.base64Url(iv));
		b.add("tag", IuText.base64Url(Arrays.copyOf(mac.doFinal(macInput.array()), jwk.getKey().length / 2)));

		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		assertThrows(IllegalArgumentException.class, () -> WebEncryption.parse(b.build().toString()).decrypt(jwk));
	}

	@Test
	public void testInvalidVectorGCM() throws Exception {
		final var jwk = WebKey.ephemeral(Encryption.A128GCM);
		final var id = IdGenerator.generateId();
		final var jwe = WebEncryption.builder(Encryption.A128GCM) //
				.addRecipient(Algorithm.DIRECT).key(jwk).encrypt(id);
		final var serialized = IuJson.parse(jwe.toString()).asJsonObject();

		final var iv = Arrays.copyOf(CryptJsonAdapters.B64URL.fromJson(serialized.get("iv")), 11);

		final var b = IuJson.object(serialized);
		b.add("iv", IuText.base64Url(iv));

		IuTestLogger.allow("iu.crypt.Jwe", Level.FINE);
		assertThrows(IllegalArgumentException.class, () -> WebEncryption.parse(b.build().toString()).decrypt(jwk));
	}

}
