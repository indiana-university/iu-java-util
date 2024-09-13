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
package iu.auth.pki;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Use;
import iu.crypt.PemEncoded;

@SuppressWarnings("javadoc")
public class KeyUsageTest {

	@Test
	public void testMissingExtensions() {
		final var cert = PemEncoded.parse(
				"MIICkjCCAhKgAwIBAgIUcxqmnbfXnGYrhdxfhuaDVIvFrAAwBQYDK2VxMIGXMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTIwMAYDVQQDDCl1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0tleVVzYWdlVGVzdDAgFw0yNDA3MDUxOTAwMDdaGA8yMTI0MDcwNjE5MDAwN1owgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MEMwBQYDK2VxAzoAsJRTRjaa+4IyXAhqk2VMq4VE4kWmB00P3Y+0M3YfGAwzVsDT09oU68EmvoOOuSD07dbtpn1CThyAo1MwUTAdBgNVHQ4EFgQUHi4A9chnH6qQuhyyGAY4c5IPT68wHwYDVR0jBBgwFoAUHi4A9chnH6qQuhyyGAY4c5IPT68wDwYDVR0TAQH/BAUwAwEB/zAFBgMrZXEDcwAGjixyy/y21Ch6mB3w/QJW8zvo6pGGvEGMeHtDQYqrPEc/chd6jT2Ft7ejUa2n4CwegHU18h6jmoD+EN4dWBU+JGmN8C3gRqIlV+yc43zNIkYix5gTDwFnERdxXlPRhc5qg3tQNmYe7yo2BxG+TmxyOgA=")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertFalse(keyUsage.isCA());
		assertFalse(keyUsage.matches(Use.ENCRYPT));
		assertFalse(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.SIGN)));
		assertFalse(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertFalse(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertFalse(keyUsage.matches(Set.of(Operation.WRAP)));
		assertEquals(0, keyUsage.ops(true, false).length);
		assertEquals(0, keyUsage.ops(true, true).length);
		assertEquals(0, keyUsage.ops(false, false).length);
		assertEquals(0, keyUsage.ops(false, true).length);
	}

	@Test
	public void testMissingKeyUsage() {
		final var cert = PemEncoded.parse(
				"MIIClTCCAhWgAwIBAgIUWixq1sQ5RmkrM7sR0J6tu7ZUqpowBQYDK2VxMIGXMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTIwMAYDVQQDDCl1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0tleVVzYWdlVGVzdDAgFw0yNDA3MDUxOTA5MzRaGA8yMTI0MDcwNjE5MDkzNFowgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MEMwBQYDK2VxAzoA9i8HAqZUq/Vm1I3siqo9MGNB/vlFv6eNwkFmvZsbVECiP/8BsOGg+sr+FNppwGPdYxivAdhgK3GAo1YwVDAdBgNVHQ4EFgQUFrlWXVqsylYfIKFS5hAVPoKgGMwwHwYDVR0jBBgwFoAUFrlWXVqsylYfIKFS5hAVPoKgGMwwEgYDVR0TAQH/BAgwBgEB/wIBADAFBgMrZXEDcwBDMwhcxRrn+PF9AbmF5lJSD5VR6v+GL4agaQv+zwlL1Zx2qoGmE2d+SqLy/2wJXL9Zz6S47NC+pICSklewDJefFhFJOrRYvSRhgxensKlnb872WOKi4wPWyBf/kTiN1S1erdxN12GB6j8WNVmtaO4gAQA=")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertFalse(keyUsage.isCA());
		assertFalse(keyUsage.matches(Use.ENCRYPT));
		assertFalse(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.SIGN)));
		assertFalse(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertFalse(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertFalse(keyUsage.matches(Set.of(Operation.WRAP)));
		assertEquals(0, keyUsage.ops(true, false).length);
		assertEquals(0, keyUsage.ops(true, true).length);
		assertEquals(0, keyUsage.ops(false, false).length);
		assertEquals(0, keyUsage.ops(false, true).length);
	}

	@Test
	public void testMissingCACRLSign() {
		final var cert = PemEncoded.parse(
				"MIICoTCCAiGgAwIBAgITWEwI2NqjmRZsIXysuTZXaEdc3TAFBgMrZXEwgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MCAXDTI0MDcwNTE5MTcyN1oYDzIxMjQwNzA2MTkxNzI3WjCBlzELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEyMDAGA1UEAwwpdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNLZXlVc2FnZVRlc3QwQzAFBgMrZXEDOgDWgMVi8v+i6wq1OUNnNXacfYWLIa+WNKicL2aj0LikMy2WO8t2JAY/64JST/+75NeGCLtAo+CjPICjYzBhMB0GA1UdDgQWBBROrS+L+Y/Ow/eZwRMJ41rNlO0MTTAfBgNVHSMEGDAWgBROrS+L+Y/Ow/eZwRMJ41rNlO0MTTASBgNVHRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwICBDAFBgMrZXEDcwAnLY61GAJx5wrzk8m1qEBD9C+LpgvM7Rscx6T6kXQYSm/DuKldUPQYh3Te4JIY5fvHfWSyw8d5YwALpdYWBY0g+RGpcngrNtGTrdearQZ4vBlbwDvMkN0mujvRh6BvgQBlnboRtiqPpKG7jS9mFbBhPwA=")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertFalse(keyUsage.isCA());
		assertFalse(keyUsage.matches(Use.ENCRYPT));
		assertFalse(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.SIGN)));
		assertFalse(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertFalse(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertFalse(keyUsage.matches(Set.of(Operation.WRAP)));
		assertEquals(0, keyUsage.ops(true, false).length);
		assertEquals(0, keyUsage.ops(true, true).length);
		assertEquals(0, keyUsage.ops(false, false).length);
		assertEquals(0, keyUsage.ops(false, true).length);
	}

	@Test
	public void testMissingCACertSign() {
		final var cert = PemEncoded.parse(
				"MIICojCCAiKgAwIBAgIUV/4N3u9ZvDs0MMeXMcDKypwwR1owBQYDK2VxMIGXMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTIwMAYDVQQDDCl1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0tleVVzYWdlVGVzdDAgFw0yNDA3MDUxOTE5MDRaGA8yMTI0MDcwNjE5MTkwNFowgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MEMwBQYDK2VxAzoAmSCPYnD5x9cKb05DQ6pzwko1Cz710qLRsnYLOr8fUgt69Qk4kfxXUJoEY0kjX2Qo5wPX2Vrby/AAo2MwYTAdBgNVHQ4EFgQU8Bko4M6BLMn2fHDejU4KrXWMHWMwHwYDVR0jBBgwFoAU8Bko4M6BLMn2fHDejU4KrXWMHWMwEgYDVR0TAQH/BAgwBgEB/wIBADALBgNVHQ8EBAMCAQIwBQYDK2VxA3MA5wOEuiiqAm2EAYi1izXmauZQaaveiXS6+jtBGublcKKtDrF7+llsDP5Tu2J9WL6EJfqApgZYzaCAhN/bR7mmOiYNi+L4qvSJdUFcax9mX2qfrDmz+XO/MtNot58GPmrYrqMXDtkRiyPGXtjuwKBQbyAA")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertFalse(keyUsage.isCA());
		assertFalse(keyUsage.matches(Use.ENCRYPT));
		assertFalse(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.SIGN)));
		assertFalse(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertFalse(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertFalse(keyUsage.matches(Set.of(Operation.WRAP)));
		assertEquals(0, keyUsage.ops(true, false).length);
		assertEquals(0, keyUsage.ops(true, true).length);
		assertEquals(0, keyUsage.ops(false, false).length);
		assertEquals(0, keyUsage.ops(false, true).length);
	}

	@Test
	public void testValidCA() {
		final var cert = PemEncoded.parse(
				"MIICojCCAiKgAwIBAgIULcoZ4UZdZ9PKpbEq2MbkBKz8ubQwBQYDK2VxMIGXMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTIwMAYDVQQDDCl1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0tleVVzYWdlVGVzdDAgFw0yNDA3MDUxOTIxMDRaGA8yMTI0MDcwNjE5MjEwNFowgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MEMwBQYDK2VxAzoAL7sb2ELxoO2HOQEjQGlBO3Gvy6qrceQO1mvv5pPtbE+EFJ1QGEcyGTB6W6BHOhP4SKj4oeRUmDGAo2MwYTAdBgNVHQ4EFgQUfgeibRQ8fu/7klB/PqzqaZp6fo0wHwYDVR0jBBgwFoAUfgeibRQ8fu/7klB/PqzqaZp6fo0wEgYDVR0TAQH/BAgwBgEB/wIBADALBgNVHQ8EBAMCAQYwBQYDK2VxA3MAPkORkd7Gbfx86P+Xhqns6znqGZG3Ri3z9sP06hrOisk2nnTxW5kZl1cYtsyXJAYUlXROz6+0tmsAnbBIs8EbMZo4RhsUH4seJLnqhL5gCRnT4Q4jZg6aPq7tAZEiDUkQfdG1tVFkrqIGN0C4Jqh6NTIA")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertTrue(keyUsage.isCA());
		assertFalse(keyUsage.matches(Use.ENCRYPT));
		assertFalse(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.SIGN)));
		assertFalse(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertFalse(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertFalse(keyUsage.matches(Set.of(Operation.WRAP)));
		assertEquals(0, keyUsage.ops(true, false).length);
		assertEquals(0, keyUsage.ops(true, true).length);
		assertEquals(0, keyUsage.ops(false, false).length);
		assertEquals(0, keyUsage.ops(false, true).length);
	}

	@Test
	public void testValidEdwardsCurve() {
		final var cert = PemEncoded.parse(
				"MIICTjCCAgCgAwIBAgIUfcaHPls3LOu+fCGvzWibspJV9V0wBQYDK2VwMIGXMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTIwMAYDVQQDDCl1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI0tleVVzYWdlVGVzdDAgFw0yNDA3MDUxOTI0NTNaGA8yMTI0MDcwNjE5MjQ1M1owgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MCowBQYDK2VwAyEAFwpFW5WQkWgU1xVbQ1fA1JFhzRP7Ag2ceeE6vm+yT8mjWjBYMB0GA1UdDgQWBBSIGME3NgZ/xwe2gHlNrigkL9HANDAfBgNVHSMEGDAWgBSIGME3NgZ/xwe2gHlNrigkL9HANDAJBgNVHRMEAjAAMAsGA1UdDwQEAwIHgDAFBgMrZXADQQAnsHo/tpEglD+p0kLuPlxydMLc1fbhZl7ka0UDHnjoYYc2r/IqUFFl3PIa2lze9Mc7YcxTsAoCnEgQlCrfTFoF")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertFalse(keyUsage.isCA());
		assertFalse(keyUsage.matches(Use.ENCRYPT));
		assertTrue(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertTrue(keyUsage.matches(Set.of(Operation.SIGN)));
		assertFalse(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertTrue(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertFalse(keyUsage.matches(Set.of(Operation.WRAP)));
		assertArrayEquals(new Operation[] { Operation.VERIFY }, keyUsage.ops(true, false));
		assertArrayEquals(new Operation[] { Operation.VERIFY, Operation.SIGN }, keyUsage.ops(true, true));
		assertEquals(0, keyUsage.ops(false, false).length);
		assertEquals(0, keyUsage.ops(false, true).length);
	}

	@Test
	public void testValidEllipticCurve() {
		final var cert = PemEncoded.parse(
				"MIICjzCCAjSgAwIBAgIUUj8YPetlZBtLNUK98uiEoLd32sAwCgYIKoZIzj0EAwIwgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MCAXDTI0MDcwNTE5MjczM1oYDzIxMjQwNzA2MTkyNzMzWjCBlzELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEyMDAGA1UEAwwpdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNLZXlVc2FnZVRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATFfvQR0GvzpIQhJnV1jK8uNY+17NlZF81cnBlrPYNgDIyYo+DAFSPevlVNcvMvKl3VO/hQReQrZI7+xZAQ0Wf9o1owWDAdBgNVHQ4EFgQUc6HjXSUa24fdFaaov5i3WGwzpuIwHwYDVR0jBBgwFoAUc6HjXSUa24fdFaaov5i3WGwzpuIwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwCgYIKoZIzj0EAwIDSQAwRgIhAKZGbg7aUgsNrhN3sJNykvNr84rrPUs/8mtSVpoZ1GTWAiEAo8NSb00q1vzcqv5uH5z3ai5lr4xO6cf1It11Oofd/Ts=")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertFalse(keyUsage.isCA());
		assertTrue(keyUsage.matches(Use.ENCRYPT));
		assertTrue(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertTrue(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertTrue(keyUsage.matches(Set.of(Operation.SIGN)));
		assertFalse(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertTrue(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertFalse(keyUsage.matches(Set.of(Operation.WRAP)));
		assertArrayEquals(new Operation[] { Operation.VERIFY }, keyUsage.ops(true, false));
		assertArrayEquals(new Operation[] { Operation.VERIFY, Operation.SIGN }, keyUsage.ops(true, true));
		assertArrayEquals(new Operation[] { Operation.DERIVE_KEY }, keyUsage.ops(false, false));
		assertArrayEquals(new Operation[] { Operation.DERIVE_KEY }, keyUsage.ops(false, true));
	}

	@Test
	public void testValidRSA() {
		final var cert = PemEncoded.parse(
				"MIIEGjCCAwKgAwIBAgIUHHxX3/OUtnMVTFWvc4VjZYmuhNcwDQYJKoZIhvcNAQELBQAwgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjS2V5VXNhZ2VUZXN0MCAXDTI0MDcwNTE5MjgwOVoYDzIxMjQwNzA2MTkyODA5WjCBlzELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEyMDAGA1UEAwwpdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNLZXlVc2FnZVRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDFyW7OWl4UOKqvh9od9APfY8tH/a1l0nFuMD0CQ6QjVdo9qTUxQ//t5af6/eIIiXBmi+ocZMJfKkJDSRZbqiZtYfRDQ8+dIhXCvGRUrh2vRfGdngoVICJyYQ+cfdPVGzmWV+jYs9nE2pKxqTuN/WgtTWrvJC8ylbFGYzozWmKRl7xVL9CA3CMoANZ09DUAIii2/scRHx5qw/LbxQAiZaon0WjDThXGMn09A/MGUAan9AktLjTD9Bct0HKtAfv6GJKjhPLeDT5AOWrpzHhRVO2iVzZuvkXSxMLP09eyQFCi0ET28zDmPiGPyLesF8koWGufaOM8k+7XeGGrs47fZIgNAgMBAAGjWjBYMB0GA1UdDgQWBBR1A+wxykkT4SEvOM53fRQyyAzzGjAfBgNVHSMEGDAWgBR1A+wxykkT4SEvOM53fRQyyAzzGjAJBgNVHRMEAjAAMAsGA1UdDwQEAwIFoDANBgkqhkiG9w0BAQsFAAOCAQEAtrhW4RexfqESdCsqOYgQ514tkSUsrrpv0dTbe/NRgFxdWgxko71XnWx023xzaGO1D/WPboDr6WyijJnhByPGxQdaZIqhQOYrA7WJ1TfNKlZvH0iCPl39SiOizf8CfJB2DjL3pLvcKczuNROaeGGnB92mdLjOy2EUD9qaszNr8Gs7QjTj3ns8TPnGrWI+nBthMjhY6poTAPtzHvDkArrMTwwm671GwW+/x0Rb7SEHUhKaSTv6iLMY87zqhWOOiBtHoeG1hMihr3oo920BZ84P9x4ticrFyi2MhsdpJaypejbagUejeGsgbOYCQvUNTxfQLTQCDUffswvU+NfXGPEmsQ==")
				.next().asCertificate();
		final var keyUsage = new KeyUsage(cert);
		assertFalse(keyUsage.isCA());
		assertTrue(keyUsage.matches(Use.ENCRYPT));
		assertTrue(keyUsage.matches(Use.SIGN));
		assertFalse(keyUsage.matches(Set.of(Operation.DECRYPT)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_BITS)));
		assertFalse(keyUsage.matches(Set.of(Operation.DERIVE_KEY)));
		assertFalse(keyUsage.matches(Set.of(Operation.ENCRYPT)));
		assertTrue(keyUsage.matches(Set.of(Operation.SIGN)));
		assertTrue(keyUsage.matches(Set.of(Operation.UNWRAP)));
		assertTrue(keyUsage.matches(Set.of(Operation.VERIFY)));
		assertTrue(keyUsage.matches(Set.of(Operation.WRAP)));
		assertArrayEquals(new Operation[] { Operation.VERIFY }, keyUsage.ops(true, false));
		assertArrayEquals(new Operation[] { Operation.VERIFY, Operation.SIGN }, keyUsage.ops(true, true));
		assertArrayEquals(new Operation[] { Operation.WRAP }, keyUsage.ops(false, false));
		assertArrayEquals(new Operation[] { Operation.WRAP, Operation.UNWRAP }, keyUsage.ops(false, true));
	}

}
