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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.cert.CRL;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.auth.IuAuthenticationException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.client.IuJson;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.test.IuTestLogger;
import iu.auth.config.AuthConfig;

@SuppressWarnings("javadoc")
public class PkiFactoryTest {

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String SELF_SIGNED_PK = "-----BEGIN PRIVATE KEY-----\n"
			+ "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAeYE6IDMu0y3wqHVcT\n" //
			+ "+9G8+cxu33efYn7uzVqVPwefoA==\n" //
			+ "-----END PRIVATE KEY-----\n";

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String SELF_SIGNED_EE = "-----BEGIN CERTIFICATE-----\n"
			+ "MIICkzCCAjigAwIBAgIUKegWOIws1N0VWFVEnMKN0ZtPi8IwCgYIKoZIzj0EAwIw\n"
			+ "gZkxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9v\n"
			+ "bWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZT\n"
			+ "VEFSQ0gxNDAyBgNVBAMMK3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtp\n"
			+ "RmFjdG9yeVRlc3QwIBcNMjQwNjE4MTMzOTA4WhgPMjEyNDA2MTkxMzM5MDhaMIGZ\n"
			+ "MQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21p\n"
			+ "bmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RB\n"
			+ "UkNIMTQwMgYDVQQDDCt1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraUZh\n"
			+ "Y3RvcnlUZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEUk91L7bhYDhLGb96\n"
			+ "kxd5CRqRIDDY1v7aevxFuGHL14HYElT+iSgi0qgpiwHzQLqLbr6OgkujPyKLhosk\n"
			+ "9+z3yaNaMFgwHQYDVR0OBBYEFJVT6uuqy1cWXtzZ8TVON458QwlsMB8GA1UdIwQY\n"
			+ "MBaAFJVT6uuqy1cWXtzZ8TVON458QwlsMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgWg\n"
			+ "MAoGCCqGSM49BAMCA0kAMEYCIQC+G+S486N8OqsCZd6jsHBsDzVnRtCsZemxqo4W\n"
			+ "HEoq4wIhAMwi6ZSWplcAJLhMJ1hGGOQLFy+EpFVM65FEd34chWJC\n" //
			+ "-----END CERTIFICATE-----\n";

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String EXPIRED_EE = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICjTCCAg2gAwIBAgIULgtpCpGnSH76irCqvzsohlr9wXUwBQYDK2VxMIGSMQsw\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
			+ "MS0wKwYDVQQDDCR1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2V4cGlyZWQw\n" //
			+ "HhcNMjQwNDIyMTE1NjU4WhcNMjQwNDIzMTE1NjU4WjCBkjELMAkGA1UEBhMCVVMx\n" //
			+ "EDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQK\n" //
			+ "DBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEtMCsGA1UEAwwk\n" //
			+ "dXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNleHBpcmVkMEMwBQYDK2VxAzoA\n" //
			+ "QiBG7vOAhJFDUM1lSoR9k1XrQRUfuGu60+FxVwKulhooFdSxz9ffZk7dOpMGybAH\n" //
			+ "WiK6cBKAM0OAo1owWDAdBgNVHQ4EFgQUag2+Gg9UTkObj0vyxu547xLv3/gwHwYD\n" //
			+ "VR0jBBgwFoAUag2+Gg9UTkObj0vyxu547xLv3/gwCQYDVR0TBAIwADALBgNVHQ8E\n" //
			+ "BAMCA4gwBQYDK2VxA3MAk3KX8t3tzKA2N3mgclkkZAXJuavgzAMGlOgtZ8C0jTtP\n" //
			+ "/QAqxSwII2VyjZjXkqQOJ8rZ70pxppuAPeUr6nxms/YXxsmL16VfiPmzVV2twIv0\n" //
			+ "f5ISsvJY0jfEdwOnrO5c27KtbfL218KVOxKDzkOObzQA\n" //
			+ "-----END CERTIFICATE-----";

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String NOFRAGMENT_EE = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICizCCAgugAwIBAgIUe6o1zGeHH0C/9ILnEFX4+hKEe2YwBQYDK2VxMIGQMQsw\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
			+ "MSswKQYDVQQDDCJ1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpLW5vdGFnMCAX\n" //
			+ "DTI0MDQyMjEzMjkxMFoYDzIxMjQwNDIzMTMyOTEwWjCBkDELMAkGA1UEBhMCVVMx\n" //
			+ "EDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQK\n" //
			+ "DBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDErMCkGA1UEAwwi\n" //
			+ "dXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraS1ub3RhZzBDMAUGAytlcQM6AEIg\n" //
			+ "Ru7zgISRQ1DNZUqEfZNV60EVH7hrutPhcVcCrpYaKBXUsc/X32ZO3TqTBsmwB1oi\n" //
			+ "unASgDNDgKNaMFgwHQYDVR0OBBYEFGoNvhoPVE5Dm49L8sbueO8S79/4MB8GA1Ud\n" //
			+ "IwQYMBaAFGoNvhoPVE5Dm49L8sbueO8S79/4MAkGA1UdEwQCMAAwCwYDVR0PBAQD\n" //
			+ "AgZAMAUGAytlcQNzAGBm77RvSiPLw4OtQbUoTXJhgYXOUfv2/7D5arBIfMUKeU7m\n" //
			+ "snIeeT3F+9kQ6jwEz1dUmw+/OvqtAAIPPYRmgWSZ4z+APPP8CImhVHmq6Jzhtm99\n" //
			+ "1n05O3vYY3gLSLxGcf2wekycLtbNvhkqIm07ZrY/AA==\n" //
			+ "-----END CERTIFICATE-----";

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String DATAENC_EE = "-----BEGIN CERTIFICATE-----\n"
			+ "MIIClzCCAhegAwIBAgIUCJ6chGs4gSHia6CKXVQe1SkGT90wBQYDK2VxMIGWMQsw\n"
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
			+ "MTEwLwYDVQQDDCh1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2RhdGFFbmNy\n"
			+ "eXB0MCAXDTI0MDQyMjEzNDcwNVoYDzIxMjQwNDIzMTM0NzA1WjCBljELMAkGA1UE\n"
			+ "BhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRsw\n"
			+ "GQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDExMC8G\n"
			+ "A1UEAwwodXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNkYXRhRW5jcnlwdDBD\n"
			+ "MAUGAytlcQM6AEIgRu7zgISRQ1DNZUqEfZNV60EVH7hrutPhcVcCrpYaKBXUsc/X\n"
			+ "32ZO3TqTBsmwB1oiunASgDNDgKNaMFgwHQYDVR0OBBYEFGoNvhoPVE5Dm49L8sbu\n"
			+ "eO8S79/4MB8GA1UdIwQYMBaAFGoNvhoPVE5Dm49L8sbueO8S79/4MAkGA1UdEwQC\n"
			+ "MAAwCwYDVR0PBAQDAgQQMAUGAytlcQNzAJ2FAMhwAYZidSkd9wuDgqHegUL4pmeg\n"
			+ "OlZMFM8D/ILmol9MEXlT3/qf8ndPpgjB6vSfFfJKiuDOAKnkdI/jHgy0T0VnSmkN\n"
			+ "WJ/AWekK4zVa7w/IdjeFRuYG/olWP5MSPauz+XAXoZQPNJQtVpdAL5wtAA==\n" //
			+ "-----END CERTIFICATE-----";

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String KEYENC_EE = "-----BEGIN CERTIFICATE-----\n"
			+ "MIIClTCCAhWgAwIBAgIUdgj1+9770pRrQTtK7B7igdTDJB8wBQYDK2VxMIGVMQsw\n"
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
			+ "MTAwLgYDVQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2tleUVuY3J5\n"
			+ "cHQwIBcNMjQwNDIyMTM1MjQ3WhgPMjEyNDA0MjMxMzUyNDdaMIGVMQswCQYDVQQG\n"
			+ "EwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZ\n"
			+ "BgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTAwLgYD\n"
			+ "VQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2tleUVuY3J5cHQwQzAF\n"
			+ "BgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV1LHP199m\n"
			+ "Tt06kwbJsAdaIrpwEoAzQ4CjWjBYMB0GA1UdDgQWBBRqDb4aD1ROQ5uPS/LG7njv\n"
			+ "Eu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DAJBgNVHRMEAjAA\n"
			+ "MAsGA1UdDwQEAwIFIDAFBgMrZXEDcwDlj87FyC+xVzPClrMGQZqT9GGgTE6Du4+N\n"
			+ "vSfksPtRKMgO8KSTWhMgrgQ+BDTJ2wvlBU4LeOtP/AB81c5/qZQoTBZ1POgokhyP\n"
			+ "YEP1yOMcXcVyP3/6geBTGNuWBELol2TPdNvRTrq96IKMUHwvZ78OCQA=\n" + //
			"-----END CERTIFICATE-----";

	/**
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 */
	private static final String KEYAGREE_EE = "-----BEGIN CERTIFICATE-----\n"
			+ "MIICmTCCAhmgAwIBAgIUXRm8V31mpTVQl+QbETjKqpxXlgYwBQYDK2VxMIGXMQsw\n"
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
			+ "MTIwMAYDVQQDDCl1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2tleUFncmVl\n"
			+ "bWVudDAgFw0yNDA0MjIxMzU3MTJaGA8yMTI0MDQyMzEzNTcxMlowgZcxCzAJBgNV\n"
			+ "BAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEb\n"
			+ "MBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAw\n"
			+ "BgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5QWdyZWVtZW50\n"
			+ "MEMwBQYDK2VxAzoAQiBG7vOAhJFDUM1lSoR9k1XrQRUfuGu60+FxVwKulhooFdSx\n"
			+ "z9ffZk7dOpMGybAHWiK6cBKAM0OAo1owWDAdBgNVHQ4EFgQUag2+Gg9UTkObj0vy\n"
			+ "xu547xLv3/gwHwYDVR0jBBgwFoAUag2+Gg9UTkObj0vyxu547xLv3/gwCQYDVR0T\n"
			+ "BAIwADALBgNVHQ8EBAMCAwgwBQYDK2VxA3MAsow9D9ubkO7pAbGKS3AsA5DGvcEN\n"
			+ "IbXa6h6i80jcF/boR2weaEJ717oGhXExGMAul1QXWq2RDY2A5e6PhEIHorFeOhxP\n"
			+ "Gwk7a00JaJj//CtHMARLbjvGJ/itJUq+DI/F0h4Yx8EVotvwkbRq7/1FHw4A\n" //
			+ "-----END CERTIFICATE-----\n";

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String CA_ROOT = "-----BEGIN CERTIFICATE-----\n"
			+ "MIICnDCCAkGgAwIBAgIUVxJv/QjCBnIwWYJc9PpLMWOU/pMwCgYIKoZIzj0EAwIw\n"
			+ "gZwxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9v\n"
			+ "bWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZT\n"
			+ "VEFSQ0gxNzA1BgNVBAMMLnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtp\n"
			+ "RmFjdG9yeVRlc3RfQ0EwIBcNMjQwNjE4MTQxMjU4WhgPMjEyNDA2MTkxNDEyNTha\n"
			+ "MIGcMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxv\n"
			+ "b21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwG\n"
			+ "U1RBUkNIMTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1Br\n"
			+ "aUZhY3RvcnlUZXN0X0NBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEUk91L7bh\n"
			+ "YDhLGb96kxd5CRqRIDDY1v7aevxFuGHL14HYElT+iSgi0qgpiwHzQLqLbr6Ogkuj\n"
			+ "PyKLhosk9+z3yaNdMFswHQYDVR0OBBYEFJVT6uuqy1cWXtzZ8TVON458QwlsMB8G\n"
			+ "A1UdIwQYMBaAFJVT6uuqy1cWXtzZ8TVON458QwlsMAwGA1UdEwQFMAMBAf8wCwYD\n"
			+ "VR0PBAQDAgEGMAoGCCqGSM49BAMCA0kAMEYCIQCH0GxjQdZ/4qqBVn1CaivAwIdS\n"
			+ "1TGe9hRhWg+2oJKadwIhANYkEffs3K5tAnAsaPGjMrx1vDgnVrvxeKP34cPhjGcX\n" //
			+ "-----END CERTIFICATE-----\n";

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ touch /tmp/crl_index
	$ cat /tmp/cnf
	[ ca ]
	default_ca = a
	
	[ a ]
	database = /tmp/crl_index
	
	$ openssl ca -gencrl -keyfile /tmp/k -cert /tmp/ca -config /tmp/cnf -crldays 36525
	 * </pre>
	 */
	private static final String CA_ROOT_CRL = "-----BEGIN X509 CRL-----\n" //
			+ "MIIBQTCBwjAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5h\n" //
			+ "MRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJz\n" //
			+ "aXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWph\n" //
			+ "dmEtYXV0aC1wa2kjUGtpU3BpVGVzdF9DQRcNMjQwNDE2MTA0NTM5WhgPMjEyNDA0\n" //
			+ "MTcxMDQ1MzlaMAUGAytlcQNzAC1rkeM6SUWX0un6apmCNwisvs6Hxsy0e4K6D7ou\n" //
			+ "+AXr0kWbeWzdisGRs7Zy0RUY0WXu5KiZ7kbwABDkGfOn0NFdnbA02hu5/V6xvfOa\n" //
			+ "jeDhXM+cmPQ/VFMuJf2tOy+n4TC+DvRMJg5bd8xqgU8Vm04lAA==\n" //
			+ "-----END X509 CRL-----\n";

	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
	private static final String CA_SIGNED = "-----BEGIN CERTIFICATE-----\n"
			+ "MIICNzCCAd0CFFqpcBpyoA+TtQEJNQ2MyEBhtOtQMAoGCCqGSM49BAMCMIGcMQsw\n"
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
			+ "MTcwNQYDVQQDDC51cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraUZhY3Rv\n"
			+ "cnlUZXN0X0NBMCAXDTI0MDYxODE0MzYyOVoYDzIxMjQwNjE5MTQzNjI5WjCBnDEL\n"
			+ "MAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5n\n"
			+ "dG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJD\n"
			+ "SDE3MDUGA1UEAwwudXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lGYWN0\n"
			+ "b3J5VGVzdF9FRTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABFJPdS+24WA4Sxm/\n"
			+ "epMXeQkakSAw2Nb+2nr8Rbhhy9eB2BJU/okoItKoKYsB80C6i26+joJLoz8ii4aL\n"
			+ "JPfs98kwCgYIKoZIzj0EAwIDSAAwRQIhAJ8gA7jzNZkbp7stenQo0zq1Rc9Ka+CY\n"
			+ "vj38pDbHJRxqAiAs2IKxKscJefQ8FmJQp5x8jmDcRNyVkYLog+z5cYA0LQ==\n" //
			+ "-----END CERTIFICATE-----\n";

	@BeforeAll
	public static void setupClass() {
		final var verifier = PkiFactory.trust(privateKeyPrincipal(SELF_SIGNED_PK + SELF_SIGNED_EE));
		// cover verifier property methods
		assertNull(verifier.getAuthScheme());
		assertNull(verifier.getAuthenticationEndpoint());
		assertSame(PkiPrincipal.class, verifier.getType());
		AuthConfig.register(verifier);

		AuthConfig
				.register(PkiFactory.trust(privateKeyPrincipal(CA_ROOT), PemEncoded.parse(CA_ROOT_CRL).next().asCRL()));

		AuthConfig.seal();
	}

	@BeforeEach
	public void setup() {
	}

	@AfterEach
	public void teardown() {
	}

	@SuppressWarnings("deprecation")
	private static IuPrivateKeyPrincipal privateKeyPrincipal(WebKey jwk) {
		final var pkpBuilder = IuJson.object().add("type", "pki");
		switch (jwk.getType()) {
		case EC_P256:
		case EC_P384:
		case EC_P521:
			pkpBuilder.add("alg", Algorithm.ES256.alg);
			pkpBuilder.add("encrypt_alg", Algorithm.ECDH_ES.alg);
			pkpBuilder.add("enc", Encryption.A128GCM.enc);
			break;

		case ED25519:
		case ED448:
			pkpBuilder.add("alg", Algorithm.EDDSA.alg);
			break;

		case RSA:
			pkpBuilder.add("alg", Algorithm.RS256.alg);
			pkpBuilder.add("encrypt_alg", Algorithm.RSA1_5.alg);
			pkpBuilder.add("enc", Encryption.A128GCM.enc);
			break;

		case RSASSA_PSS:
			pkpBuilder.add("alg", Algorithm.PS256.alg);
			break;

		case X25519:
		case X448:
			pkpBuilder.add("encrypt_alg", Algorithm.ECDH_ES.alg);
			pkpBuilder.add("enc", Encryption.A128GCM.enc);

		case RAW:
		default:
			break;
		}

		pkpBuilder.add("jwk", WebKey.JSON.toJson(jwk));

		return (IuPrivateKeyPrincipal) IuAuthenticationRealm.JSON.fromJson(pkpBuilder.build());
	}

	private static IuPrivateKeyPrincipal privateKeyPrincipal(String pem) {
		return privateKeyPrincipal(WebKey.pem(pem));
	}

	@Test
	public void testInvalidPkiPrincipal() {
		assertEquals("Missing X.509 certificate chain", assertThrows(NullPointerException.class,
				() -> PkiFactory.from(privateKeyPrincipal(WebKey.builder(Type.EC_P256).pem(SELF_SIGNED_PK).build())))
				.getMessage());
	}

	@Test
	public void testSelfSignedEE() throws Exception {
		final var pki = (PkiPrincipal) PkiFactory.from(privateKeyPrincipal(SELF_SIGNED_PK + SELF_SIGNED_EE));

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki#PkiFactoryTest; trustAnchor: CN=urn:example:iu-java-auth-pki\\#PkiFactoryTest,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		assertTrue(IuPrincipalIdentity.verify(pki, pki.getName()));

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(2, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();
		final var publicId = PkiFactory.from(privateKeyPrincipal(wellKnown));

		// must have private key to verify as authoritative
		assertThrows(IllegalArgumentException.class, () -> IuPrincipalIdentity.verify(publicId, pki.getName()));

		final var cert = wellKnown.getCertificateChain()[0];
		assertEquals(pki.getName(), X500Utils.getCommonName(cert.getSubjectX500Principal()));
		assertNotNull(sub.getPrivateCredentials(WebKey.class).iterator().next().getPrivateKey());
	}

	@Test
	public void testSelfSignedEECertOnly() throws Exception {
		final var id = privateKeyPrincipal(SELF_SIGNED_EE);
		final var pki = (PkiPrincipal) PkiFactory.from(id);
		final var verifier = PkiFactory.trust(id);
		AuthConfig.register(verifier);
		AuthConfig.seal();

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:verify:urn:example:iu-java-auth-pki#PkiFactoryTest; trustAnchor: CN=urn:example:iu-java-auth-pki\\#PkiFactoryTest,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		assertFalse(IuPrincipalIdentity.verify(pki, pki.getName()));

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();
		final var publicId = PkiFactory.from(privateKeyPrincipal(wellKnown));

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:verify:urn:example:iu-java-auth-pki#PkiSpiTest; trustAnchor: CN=urn:example:iu-java-auth-pki\\#PkiSpiTest,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		IuPrincipalIdentity.verify(publicId, pki.getName());

		final var cert = wellKnown.getCertificateChain()[0];
		assertEquals(pki.getName(), X500Utils.getCommonName(cert.getSubjectX500Principal()));

		assertTrue(sub.getPrivateCredentials(WebKey.class).isEmpty());
	}

	@Test
	public void testSelfSignedEEForEncrypt() throws Exception {
		assertThrows(NullPointerException.class,
				() -> PkiFactory.from(privateKeyPrincipal(SELF_SIGNED_PK + DATAENC_EE)));
		assertThrows(NullPointerException.class, () -> PkiFactory.from(privateKeyPrincipal(DATAENC_EE)));

		final var id = privateKeyPrincipal(SELF_SIGNED_PK + KEYENC_EE);
		final var pki = PkiFactory.from(id);
		AuthConfig.register(PkiFactory.trust(id));
		AuthConfig.seal();

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki#keyEncrypt; trustAnchor: CN=urn:example:iu-java-auth-pki\\#keyEncrypt,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		IuPrincipalIdentity.verify(pki, pki.getName());
		final var key = pki.getSubject().getPrivateCredentials(WebKey.class).iterator().next();
		assertNull(key.getUse());
		assertEquals(Set.of(Operation.WRAP, Operation.UNWRAP), key.getOps());

		final var pubkey = PkiFactory.from(privateKeyPrincipal(KEYENC_EE)).getSubject()
				.getPublicCredentials(WebKey.class).iterator().next();
		assertNull(pubkey.getUse());
		assertEquals(Set.of(Operation.WRAP), pubkey.getOps());
	}

	@Test
	public void testSelfSignedEEForKeyAgreement() throws Exception {
		final var id = privateKeyPrincipal(SELF_SIGNED_PK + KEYAGREE_EE);
		final var pki = PkiFactory.from(id);
		AuthConfig.register(PkiFactory.trust(id));
		AuthConfig.seal();

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki#keyAgreement; trustAnchor: CN=urn:example:iu-java-auth-pki\\#keyAgreement,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		IuPrincipalIdentity.verify(pki, pki.getName());
		final var key = pki.getSubject().getPrivateCredentials(WebKey.class).iterator().next();
		assertNull(key.getUse());
		assertEquals(Set.of(Operation.DERIVE_KEY), key.getOps());
		PkiFactory.from(privateKeyPrincipal(key.wellKnown())); // from full params
	}

	@Test
	public void testExpiredEE() throws Exception {
		final var id = privateKeyPrincipal(SELF_SIGNED_PK + EXPIRED_EE);
		final var pki = PkiFactory.from(id);
		AuthConfig.register(PkiFactory.trust(id));
		AuthConfig.seal();

		assertInstanceOf(CertificateExpiredException.class,
				assertInstanceOf(CertPathValidatorException.class, assertThrows(IllegalStateException.class,
						() -> IuPrincipalIdentity.verify(
								PkiFactory.from(privateKeyPrincipal(SELF_SIGNED_PK + EXPIRED_EE)), pki.getName()))
						.getCause()).getCause());
	}

	@Test
	public void testNoFragmentEE() throws Exception {
		final var id = privateKeyPrincipal(SELF_SIGNED_PK + NOFRAGMENT_EE);
		final var pki = PkiFactory.from(id);
		AuthConfig.register(PkiFactory.trust(id));
		AuthConfig.seal();

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki-notag; trustAnchor: CN=urn:example:iu-java-auth-pki-notag,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		IuPrincipalIdentity.verify(pki, pki.getName());

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();
		assertEquals("verify", wellKnown.getKeyId());
	}

	@Test
	public void testPublicCA() throws Exception {
		final CertPath iuEdu;
		final X509Certificate inCommon;
		final CRL[] crl;
		try {
			final var http = HttpClient.newHttpClient();
			final var resp = http.send(HttpRequest.newBuilder(URI.create("https://www.iu.edu/index.html"))
					.method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.discarding());

			final var x509 = CertificateFactory.getInstance("X.509");
			iuEdu = x509.generateCertPath(List.of(resp.sslSession().get().getPeerCertificates()));
			inCommon = (X509Certificate) x509.generateCertificate(http.send(
					HttpRequest.newBuilder(URI.create("http://crt.usertrust.com/USERTrustRSAAddTrustCA.crt")).build(),
					BodyHandlers.ofInputStream()).body());

			crl = new CRL[] {
					x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.incommon-rsa.org/InCommonRSAServerCA.crl")).build(),
							BodyHandlers.ofInputStream()).body()),
					x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.usertrust.com/USERTrustRSACertificationAuthority.crl"))
							.build(), BodyHandlers.ofInputStream()).body()) };
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort("unable to read public key data for verifying iu.edu " + e);
			return;
		}

		AuthConfig.register(PkiFactory
				.trust(privateKeyPrincipal(WebKey.builder(inCommon.getPublicKey()).cert(inCommon).build()), crl));
		AuthConfig.seal();

		final var iuEduPem = new StringBuilder();
		PemEncoded.serialize(iuEdu.getCertificates().toArray(X509Certificate[]::new))
				.forEachRemaining(iuEduPem::append);
		final var iuEduId = (PkiPrincipal) PkiFactory.from(privateKeyPrincipal(iuEduPem.toString()));
		assertEquals("iu.edu", iuEduId.getName());

		IuPrincipalIdentity.verify(iuEduId, "USERTrust RSA Certification Authority");

		assertThrows(IllegalArgumentException.class,
				() -> IuPrincipalIdentity.verify(PkiFactory.from(privateKeyPrincipal(SELF_SIGNED_PK + KEYAGREE_EE)),
						"USERTrust RSA Certification Authority"));
	}

	@Test
	public void testPrivateCA() throws Exception {
		final var caRoot = privateKeyPrincipal(CA_ROOT);
		assertEquals("ID certificate must be an end-entity",
				assertThrows(IllegalArgumentException.class, () -> PkiFactory.from(caRoot)).getMessage());

		final var id = privateKeyPrincipal(CA_SIGNED);
		final var pki = (PkiPrincipal) PkiFactory.from(id);

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:verify:urn:example:iu-java-auth-pki#PkiSpiTest_EE; trustAnchor: CN=urn:example:iu-java-auth-pki\\#PkiSpiTest_CA,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");

		IuPrincipalIdentity.verify(pki,
				X500Utils.getCommonName(caRoot.getJwk().getCertificateChain()[0].getSubjectX500Principal()));
	}

	private static final String PK2 = "-----BEGIN PRIVATE KEY-----\n" //
			+ "MC4CAQAwBQYDK2VwBCIEIKDugq7tgDXBWvu24W0Flikh7URBRwFpjKmsq3+Qhv07\n" //
			+ "-----END PRIVATE KEY-----\n";

	private static final String PK2_CERT = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICIzCCAdWgAwIBAgIUXFUgBaS/LWo1dyWhIosURCkGYo4wBQYDK2VwMIGAMQsw\n"
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
			+ "MRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNwazIwIBcNMjQwNTE3MjM0MjI2WhgP\n"
			+ "MjEyNDA1MTgyMzQyMjZaMIGAMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFu\n"
			+ "YTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVy\n"
			+ "c2l0eTEPMA0GA1UECwwGU1RBUkNIMRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNw\n"
			+ "azIwKjAFBgMrZXADIQC4mwCSvPxK+vYOWLvNGRzMPnNgiNigJcH8n9VLCPHnhaNd\n"
			+ "MFswHQYDVR0OBBYEFHjhN0OongfMsuWHyahJ+hnKL3zpMB8GA1UdIwQYMBaAFHjh\n"
			+ "N0OongfMsuWHyahJ+hnKL3zpMAwGA1UdEwQFMAMCAf8wCwYDVR0PBAQDAgEGMAUG\n"
			+ "AytlcANBANwdh+Vz5664X7YCUrLYf0XdKoMNliKQ4BHsFclKOujMKlYsWdgrSKjB\n"
			+ "NwZduVMNjT7aGsDrYocJJc+ATzRsHQU=\n" //
			+ "-----END CERTIFICATE-----\n";

	private static final String PK3 = "-----BEGIN PRIVATE KEY-----\n" //
			+ "MC4CAQAwBQYDK2VwBCIEIClnfQHZGjmICLWmZx6kEeazRnOuO8DLTOWLDSPCX/v+\n" //
			+ "-----END PRIVATE KEY-----\n";

	private static final String PK3_CERT = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICIzCCAdWgAwIBAgIULGJHtoFDBItb5Ci9TBj9TT/ZV3AwBQYDK2VwMIGAMQsw\n"
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
			+ "MRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNwazIwIBcNMjQwNTE3MjM0OTU3WhgP\n"
			+ "MjEyNDA1MTgyMzQ5NTdaMIGAMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFu\n"
			+ "YTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVy\n"
			+ "c2l0eTEPMA0GA1UECwwGU1RBUkNIMRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNw\n"
			+ "azIwKjAFBgMrZXADIQCOewWrIjwt6urqjpzwsHESUieYAVEtFP7hfscNEei+gaNd\n"
			+ "MFswHQYDVR0OBBYEFK8BIOvGpWr7758g4xUgrboy0ocaMB8GA1UdIwQYMBaAFK8B\n"
			+ "IOvGpWr7758g4xUgrboy0ocaMAwGA1UdEwQFMAMCAf8wCwYDVR0PBAQDAgEGMAUG\n"
			+ "AytlcANBAM4sIMHRi6S+mcqxEHM5uCaRjini+cOGAwTKKRhTq4zbv3RMz6tItLgq\n"
			+ "sy0UXhHVpR/ZVMT9w3ORm8vfbCmWewk=\n" //
			+ "-----END CERTIFICATE-----\n";

	@Test
	public void testPrivateKeyRealmMismatch() throws IuAuthenticationException {
		final var id = privateKeyPrincipal(SELF_SIGNED_PK + SELF_SIGNED_EE);
		final var pki = PkiFactory.from(id);
		AuthConfig.register(PkiFactory.trust(id));
		AuthConfig.seal();

		final var pk2 = PkiFactory.from(privateKeyPrincipal(PK2 + PK2_CERT));
		assertThrows(IllegalArgumentException.class, () -> IuPrincipalIdentity.verify(pk2, pki.getName()));
	}

	@Test
	public void testPrivateKeyMismatch() throws IuAuthenticationException {
		final var id = privateKeyPrincipal(PK2 + PK2_CERT);
		final var pki = PkiFactory.from(id);
		AuthConfig.register(PkiFactory.trust(id));
		AuthConfig.seal();

		final var pk2 = PkiFactory.from(privateKeyPrincipal(PK3 + PK3_CERT));
		assertThrows(IllegalArgumentException.class, () -> IuPrincipalIdentity.verify(pk2, pki.getName()));
	}

}
