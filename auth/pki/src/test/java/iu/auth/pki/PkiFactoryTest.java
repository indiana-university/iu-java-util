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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuIterable;
import edu.iu.auth.config.IuAuthenticationRealm;
import edu.iu.auth.config.IuCertificateAuthority;
import edu.iu.auth.config.IuPrivateKeyPrincipal;
import edu.iu.auth.config.X500Utils;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class PkiFactoryTest {

//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String SELF_SIGNED_PK = "-----BEGIN PRIVATE KEY-----\n"
//			+ "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAeYE6IDMu0y3wqHVcT\n" //
//			+ "+9G8+cxu33efYn7uzVqVPwefoA==\n" //
//			+ "-----END PRIVATE KEY-----\n";
//
//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String SELF_SIGNED_EE = "-----BEGIN CERTIFICATE-----\n"
//			+ "MIICkzCCAjigAwIBAgIUKegWOIws1N0VWFVEnMKN0ZtPi8IwCgYIKoZIzj0EAwIw\n"
//			+ "gZkxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9v\n"
//			+ "bWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZT\n"
//			+ "VEFSQ0gxNDAyBgNVBAMMK3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtp\n"
//			+ "RmFjdG9yeVRlc3QwIBcNMjQwNjE4MTMzOTA4WhgPMjEyNDA2MTkxMzM5MDhaMIGZ\n"
//			+ "MQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21p\n"
//			+ "bmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RB\n"
//			+ "UkNIMTQwMgYDVQQDDCt1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraUZh\n"
//			+ "Y3RvcnlUZXN0MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEUk91L7bhYDhLGb96\n"
//			+ "kxd5CRqRIDDY1v7aevxFuGHL14HYElT+iSgi0qgpiwHzQLqLbr6OgkujPyKLhosk\n"
//			+ "9+z3yaNaMFgwHQYDVR0OBBYEFJVT6uuqy1cWXtzZ8TVON458QwlsMB8GA1UdIwQY\n"
//			+ "MBaAFJVT6uuqy1cWXtzZ8TVON458QwlsMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgWg\n"
//			+ "MAoGCCqGSM49BAMCA0kAMEYCIQC+G+S486N8OqsCZd6jsHBsDzVnRtCsZemxqo4W\n"
//			+ "HEoq4wIhAMwi6ZSWplcAJLhMJ1hGGOQLFy+EpFVM65FEd34chWJC\n" //
//			+ "-----END CERTIFICATE-----\n";
//
//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String EXPIRED_EE = "-----BEGIN CERTIFICATE-----\n" //
//			+ "MIICjTCCAg2gAwIBAgIULgtpCpGnSH76irCqvzsohlr9wXUwBQYDK2VxMIGSMQsw\n" //
//			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
//			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
//			+ "MS0wKwYDVQQDDCR1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2V4cGlyZWQw\n" //
//			+ "HhcNMjQwNDIyMTE1NjU4WhcNMjQwNDIzMTE1NjU4WjCBkjELMAkGA1UEBhMCVVMx\n" //
//			+ "EDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQK\n" //
//			+ "DBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEtMCsGA1UEAwwk\n" //
//			+ "dXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNleHBpcmVkMEMwBQYDK2VxAzoA\n" //
//			+ "QiBG7vOAhJFDUM1lSoR9k1XrQRUfuGu60+FxVwKulhooFdSxz9ffZk7dOpMGybAH\n" //
//			+ "WiK6cBKAM0OAo1owWDAdBgNVHQ4EFgQUag2+Gg9UTkObj0vyxu547xLv3/gwHwYD\n" //
//			+ "VR0jBBgwFoAUag2+Gg9UTkObj0vyxu547xLv3/gwCQYDVR0TBAIwADALBgNVHQ8E\n" //
//			+ "BAMCA4gwBQYDK2VxA3MAk3KX8t3tzKA2N3mgclkkZAXJuavgzAMGlOgtZ8C0jTtP\n" //
//			+ "/QAqxSwII2VyjZjXkqQOJ8rZ70pxppuAPeUr6nxms/YXxsmL16VfiPmzVV2twIv0\n" //
//			+ "f5ISsvJY0jfEdwOnrO5c27KtbfL218KVOxKDzkOObzQA\n" //
//			+ "-----END CERTIFICATE-----";
//
//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String NOFRAGMENT_EE = "-----BEGIN CERTIFICATE-----\n" //
//			+ "MIICizCCAgugAwIBAgIUe6o1zGeHH0C/9ILnEFX4+hKEe2YwBQYDK2VxMIGQMQsw\n" //
//			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
//			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
//			+ "MSswKQYDVQQDDCJ1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpLW5vdGFnMCAX\n" //
//			+ "DTI0MDQyMjEzMjkxMFoYDzIxMjQwNDIzMTMyOTEwWjCBkDELMAkGA1UEBhMCVVMx\n" //
//			+ "EDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQK\n" //
//			+ "DBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDErMCkGA1UEAwwi\n" //
//			+ "dXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraS1ub3RhZzBDMAUGAytlcQM6AEIg\n" //
//			+ "Ru7zgISRQ1DNZUqEfZNV60EVH7hrutPhcVcCrpYaKBXUsc/X32ZO3TqTBsmwB1oi\n" //
//			+ "unASgDNDgKNaMFgwHQYDVR0OBBYEFGoNvhoPVE5Dm49L8sbueO8S79/4MB8GA1Ud\n" //
//			+ "IwQYMBaAFGoNvhoPVE5Dm49L8sbueO8S79/4MAkGA1UdEwQCMAAwCwYDVR0PBAQD\n" //
//			+ "AgZAMAUGAytlcQNzAGBm77RvSiPLw4OtQbUoTXJhgYXOUfv2/7D5arBIfMUKeU7m\n" //
//			+ "snIeeT3F+9kQ6jwEz1dUmw+/OvqtAAIPPYRmgWSZ4z+APPP8CImhVHmq6Jzhtm99\n" //
//			+ "1n05O3vYY3gLSLxGcf2wekycLtbNvhkqIm07ZrY/AA==\n" //
//			+ "-----END CERTIFICATE-----";
//
//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String DATAENC_EE = "-----BEGIN CERTIFICATE-----\n"
//			+ "MIIClzCCAhegAwIBAgIUCJ6chGs4gSHia6CKXVQe1SkGT90wBQYDK2VxMIGWMQsw\n"
//			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
//			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
//			+ "MTEwLwYDVQQDDCh1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2RhdGFFbmNy\n"
//			+ "eXB0MCAXDTI0MDQyMjEzNDcwNVoYDzIxMjQwNDIzMTM0NzA1WjCBljELMAkGA1UE\n"
//			+ "BhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRsw\n"
//			+ "GQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDExMC8G\n"
//			+ "A1UEAwwodXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNkYXRhRW5jcnlwdDBD\n"
//			+ "MAUGAytlcQM6AEIgRu7zgISRQ1DNZUqEfZNV60EVH7hrutPhcVcCrpYaKBXUsc/X\n"
//			+ "32ZO3TqTBsmwB1oiunASgDNDgKNaMFgwHQYDVR0OBBYEFGoNvhoPVE5Dm49L8sbu\n"
//			+ "eO8S79/4MB8GA1UdIwQYMBaAFGoNvhoPVE5Dm49L8sbueO8S79/4MAkGA1UdEwQC\n"
//			+ "MAAwCwYDVR0PBAQDAgQQMAUGAytlcQNzAJ2FAMhwAYZidSkd9wuDgqHegUL4pmeg\n"
//			+ "OlZMFM8D/ILmol9MEXlT3/qf8ndPpgjB6vSfFfJKiuDOAKnkdI/jHgy0T0VnSmkN\n"
//			+ "WJ/AWekK4zVa7w/IdjeFRuYG/olWP5MSPauz+XAXoZQPNJQtVpdAL5wtAA==\n" //
//			+ "-----END CERTIFICATE-----";
//
//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String KEYENC_EE = "-----BEGIN CERTIFICATE-----\n"
//			+ "MIIClTCCAhWgAwIBAgIUdgj1+9770pRrQTtK7B7igdTDJB8wBQYDK2VxMIGVMQsw\n"
//			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
//			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
//			+ "MTAwLgYDVQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2tleUVuY3J5\n"
//			+ "cHQwIBcNMjQwNDIyMTM1MjQ3WhgPMjEyNDA0MjMxMzUyNDdaMIGVMQswCQYDVQQG\n"
//			+ "EwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZ\n"
//			+ "BgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTAwLgYD\n"
//			+ "VQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2tleUVuY3J5cHQwQzAF\n"
//			+ "BgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV1LHP199m\n"
//			+ "Tt06kwbJsAdaIrpwEoAzQ4CjWjBYMB0GA1UdDgQWBBRqDb4aD1ROQ5uPS/LG7njv\n"
//			+ "Eu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DAJBgNVHRMEAjAA\n"
//			+ "MAsGA1UdDwQEAwIFIDAFBgMrZXEDcwDlj87FyC+xVzPClrMGQZqT9GGgTE6Du4+N\n"
//			+ "vSfksPtRKMgO8KSTWhMgrgQ+BDTJ2wvlBU4LeOtP/AB81c5/qZQoTBZ1POgokhyP\n"
//			+ "YEP1yOMcXcVyP3/6geBTGNuWBELol2TPdNvRTrq96IKMUHwvZ78OCQA=\n" + //
//			"-----END CERTIFICATE-----";
//
//	/**
//	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
//	 */
//	private static final String KEYAGREE_EE = "-----BEGIN CERTIFICATE-----\n"
//			+ "MIICmTCCAhmgAwIBAgIUXRm8V31mpTVQl+QbETjKqpxXlgYwBQYDK2VxMIGXMQsw\n"
//			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
//			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
//			+ "MTIwMAYDVQQDDCl1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2tleUFncmVl\n"
//			+ "bWVudDAgFw0yNDA0MjIxMzU3MTJaGA8yMTI0MDQyMzEzNTcxMlowgZcxCzAJBgNV\n"
//			+ "BAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEb\n"
//			+ "MBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAw\n"
//			+ "BgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5QWdyZWVtZW50\n"
//			+ "MEMwBQYDK2VxAzoAQiBG7vOAhJFDUM1lSoR9k1XrQRUfuGu60+FxVwKulhooFdSx\n"
//			+ "z9ffZk7dOpMGybAHWiK6cBKAM0OAo1owWDAdBgNVHQ4EFgQUag2+Gg9UTkObj0vy\n"
//			+ "xu547xLv3/gwHwYDVR0jBBgwFoAUag2+Gg9UTkObj0vyxu547xLv3/gwCQYDVR0T\n"
//			+ "BAIwADALBgNVHQ8EBAMCAwgwBQYDK2VxA3MAsow9D9ubkO7pAbGKS3AsA5DGvcEN\n"
//			+ "IbXa6h6i80jcF/boR2weaEJ717oGhXExGMAul1QXWq2RDY2A5e6PhEIHorFeOhxP\n"
//			+ "Gwk7a00JaJj//CtHMARLbjvGJ/itJUq+DI/F0h4Yx8EVotvwkbRq7/1FHw4A\n" //
//			+ "-----END CERTIFICATE-----\n";
//
//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String CA_ROOT = "-----BEGIN CERTIFICATE-----\n"
//			+ "MIICojCCAiKgAwIBAgIUMtgQ5nTm/LD1IFWnGhysbEBb3dswBQYDK2VxMIGYMQswC\n"
//			+ "QYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b2\n"
//			+ "4xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTM\n"
//			+ "wMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3Rf\n"
//			+ "Q0EwHhcNMjQwNzAzMTUzNjQ2WhcNMjcxMDExMTUzNjQ2WjCBmDELMAkGA1UEBhMCV\n"
//			+ "VMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQ\n"
//			+ "QKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEzMDEGA1UEAww\n"
//			+ "qdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lTcGlUZXN0X0NBMEMwBQYD\n"
//			+ "K2VxAzoAdnVjexMQn88Nu6JhqFXWY3PpfY2WHDYHcSJ6I+07uT3lunH9qfW0trD0d\n"
//			+ "YcifJ03EVu59tbOtZ2Ao2MwYTAdBgNVHQ4EFgQUYp7LU6azUCOpvv9qYWru5FZdJb\n"
//			+ "IwHwYDVR0jBBgwFoAUYp7LU6azUCOpvv9qYWru5FZdJbIwEgYDVR0TAQH/BAgwBgE\n"
//			+ "B/wIBADALBgNVHQ8EBAMCAQYwBQYDK2VxA3MAfIc5swe9KU++LuWcbobZAPGS3hJn\n"
//			+ "Xm6jVmMkjlgqIOSYwVpblPChshS8hRVnzG8Hjzx5fc4EwBiA8r6L6o5w478fFwFg7\n"
//			+ "yfsw09AdQQvce3HSu2utlkZhgTPeRMxV3hD+g009mAeu1RVUriZ2bMm1TIA\n" + "-----END CERTIFICATE-----\n";
//
//	/**
//	 * <p>
//	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
//	 * 
//	 * <pre>
//	$ touch /tmp/crl_index
//	$ cat /tmp/cnf
//	[ ca ]
//	default_ca = a
//	
//	[ a ]
//	database = /tmp/crl_index
//	
//	$ openssl ca -gencrl -keyfile /tmp/k -cert /tmp/ca -config /tmp/cnf -crldays 36525
//	 * </pre>
//	 */
//	private static final String CA_ROOT_CRL = "-----BEGIN X509 CRL-----\n"
//			+ "MIIBPzCBwDAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hM\n"
//			+ "RQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaX\n"
//			+ "R5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmE\n"
//			+ "tYXV0aC1wa2kjUGtpU3BpVGVzdF9DQRcNMjQwNzAzMTgxMDAyWhcNMjcxMDEwMTgx\n"
//			+ "MDAyWjAFBgMrZXEDcwDQSY92XTAWYKOvnR1FGKr5FsrYsHq3OPTb1pE4gNoYG/3mr\n"
//			+ "jUDVPF9Ct4LoGkvQKAbAIJdr7lXhAC5PEB7dk6ETa9xmDB3zTdeBshJkc1zucFQCC\n"
//			+ "o7zPZw6xf+ZWSbll4c/TxnsgzTTaYVBdXgwXdCEgA=\n" + "-----END X509 CRL-----\n";
//
//	/** For verification and demonstration purposes only. NOT FOR PRODUCTION USE */
//	private static final String CA_SIGNED = "-----BEGIN CERTIFICATE-----\n"
//			+ "MIICgDCCAgCgAwIBAgIUDSgbBK9kirBSoL3hb7KJHgijArIwBQYDK2VxMIGYMQswC\n"
//			+ "QYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b2\n"
//			+ "4xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTM\n"
//			+ "wMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3Rf\n"
//			+ "Q0EwHhcNMjQwNzAzMTgxMDAxWhcNMjYxMDExMTgxMDAxWjCBmDELMAkGA1UEBhMCV\n"
//			+ "VMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQ\n"
//			+ "QKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEzMDEGA1UEAww\n"
//			+ "qdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lTcGlUZXN0X0VFMCowBQYD\n"
//			+ "K2VwAyEAmme900fSQvgf7jOJMHSVg50L8BhEViCIRgJ8oIDygTOjWjBYMAkGA1UdE\n"
//			+ "wQCMAAwCwYDVR0PBAQDAgeAMB0GA1UdDgQWBBRZZSBfxFrus7GBqP3yUuRytxMjaD\n"
//			+ "AfBgNVHSMEGDAWgBRinstTprNQI6m+/2phau7kVl0lsjAFBgMrZXEDcwDLiHo4y5E\n"
//			+ "l39a5GWddlu7Hf0tn/WJfiY2AKvnRu5Jky2o6uTehsQaNRerztJhY12FSneLNKBqG\n"
//			+ "fIDYsRB7tOVzR6lV1ezdclP79LSaYz/HjayKUf5yue3RY/ohzXY1x/3IruiYgpux+\n" + "Ez4qqBnWgG4FwA=\n"
//			+ "-----END CERTIFICATE-----\n";

	@BeforeEach
	public void setup() {
	}

	@AfterEach
	public void teardown() {
	}

//	@SuppressWarnings("deprecation")
//	private static IuPrivateKeyPrincipal privateKeyPrincipal(WebKey jwk) {
//		final var pkpBuilder = IuJson.object().add("type", "pki");
//		switch (jwk.getType()) {
//		case EC_P256:
//		case EC_P384:
//		case EC_P521:
//			pkpBuilder.add("alg", Algorithm.ES256.alg);
//			pkpBuilder.add("encrypt_alg", Algorithm.ECDH_ES.alg);
//			pkpBuilder.add("enc", Encryption.A128GCM.enc);
//			break;
//
//		case ED25519:
//		case ED448:
//			pkpBuilder.add("alg", Algorithm.EDDSA.alg);
//			break;
//
//		case RSA:
//			pkpBuilder.add("alg", Algorithm.RS256.alg);
//			pkpBuilder.add("encrypt_alg", Algorithm.RSA1_5.alg);
//			pkpBuilder.add("enc", Encryption.A128GCM.enc);
//			break;
//
//		case RSASSA_PSS:
//			pkpBuilder.add("alg", Algorithm.PS256.alg);
//			break;
//
//		case X25519:
//		case X448:
//			pkpBuilder.add("encrypt_alg", Algorithm.ECDH_ES.alg);
//			pkpBuilder.add("enc", Encryption.A128GCM.enc);
//
//		case RAW:
//		default:
//			break;
//		}
//
//		pkpBuilder.add("jwk", WebKey.JSON.toJson(jwk));
//
//		return (IuPrivateKeyPrincipal) IuAuthenticationRealm.JSON.fromJson(pkpBuilder.build());
//	}
//
//	private static IuPrivateKeyPrincipal privateKeyPrincipal(String pem) {
//		return privateKeyPrincipal(WebKey.pem(pem));
//	}
//

	private IuPrivateKeyPrincipal pkp(String pkp) {
		return (IuPrivateKeyPrincipal) IuAuthenticationRealm.JSON.fromJson(IuJson.parse(pkp));
	}

	private IuCertificateAuthority ca(String ca) {
		return (IuCertificateAuthority) IuAuthenticationRealm.JSON.fromJson(IuJson.parse(ca));
	}

	@Test
	public void testInvalidPkiPrincipal() {
		assertEquals("Missing X.509 certificate chain",
				assertThrows(NullPointerException.class, () -> PkiFactory.from(pkp("{\n" //
						// For verification and demonstration purposes only. NOT FOR PRODUCTION USE
						+ "    \"type\": \"pki\",\n" //
						+ "    \"alg\": \"ES256\",\n" //
						+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
						+ "    \"enc\": \"A128GCM\",\n" //
						+ "    \"jwk\": {\n" //
						+ "        \"kid\": \"test\",\n" //
						+ "        \"kty\": \"EC\",\n" //
						+ "        \"crv\": \"P-256\",\n" //
						+ "        \"x\": \"u2MPWm8R_845R8yo47gYs6IUJf1epqb6fa9LYRfm-eQ\",\n" //
						+ "        \"y\": \"YAIJtyv-p02S9ywOFDD33KyzN59Y9cjMnepKXU1Zs4g\",\n" //
						+ "        \"d\": \"pDW-ejrVMf9zqsPaihKgLJyI0jqWhELrBG0RyZT9mTo\"\n" //
						+ "    }\n" //
						+ "}"))).getMessage());
	}

	@Test
	public void testSelfSignedEE() throws Exception {
		final var pki = (PkiPrincipal) PkiFactory.from(pkp("{\n" //
				// For verification and demonstration purposes only. NOT FOR PRODUCTION USE
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiFactoryTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICkDCCAjagAwIBAgIUDoojq5F1j4C6vuzuXltYSBaT6AcwCgYIKoZIzj0EAwIwgZkxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNDAyBgNVBAMMK3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpRmFjdG9yeVRlc3QwHhcNMjQwNzAzMjEwNzIxWhcNMjYxMDExMjEwNzIxWjCBmTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE0MDIGA1UEAwwrdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lGYWN0b3J5VGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABCj2P4U0FEWXR1sQrzjFusY3kh6Pk6lrM0FaoWa7lM7tUNkWA6szETXMdXaPb4oZ31xpB9Otq6Gyn9PTDpCPSsyjWjBYMB0GA1UdDgQWBBQVLSdUYatMocbVy8NbwB4c04XtEjAfBgNVHSMEGDAWgBQVLSdUYatMocbVy8NbwB4c04XtEjAJBgNVHRMEAjAAMAsGA1UdDwQEAwIDiDAKBggqhkjOPQQDAgNIADBFAiA0ctHEyTmLaEb65HEPLoWy0oni8VsBzgdZ12UJefL8qgIhAMQXzaoX12cdmYd56hWZVj02Np0tdIgHMIokBXMfBixg\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"KPY_hTQURZdHWxCvOMW6xjeSHo-TqWszQVqhZruUzu0\",\n" //
				+ "        \"y\": \"UNkWA6szETXMdXaPb4oZ31xpB9Otq6Gyn9PTDpCPSsw\",\n" //
				+ "        \"d\": \"3NvDDfCLunHDjl48Qt3LrkpfHKbG4TxubRNt2orCuH8\"\n" //
				+ "    }\n" //
				+ "}\n"));

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki#PkiFactoryTest; trustAnchor: CN=urn:example:iu-java-auth-pki\\#PkiFactoryTest,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");

		final var verifier = (PkiVerifier) PkiFactory.trust(pki);
		assertTrue(verifier.isAuthoritative());
		verifier.verify(pki);

		final var publicId = (PkiPrincipal) PkiFactory.from(pkp("{\n" //
				// For verification and demonstration purposes only. NOT FOR PRODUCTION USE
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"ES256\",\n" //
				+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
				+ "    \"enc\": \"A128GCM\",\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiFactoryTest\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICkDCCAjagAwIBAgIUDoojq5F1j4C6vuzuXltYSBaT6AcwCgYIKoZIzj0EAwIwgZkxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxNDAyBgNVBAMMK3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpRmFjdG9yeVRlc3QwHhcNMjQwNzAzMjEwNzIxWhcNMjYxMDExMjEwNzIxWjCBmTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE0MDIGA1UEAwwrdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lGYWN0b3J5VGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABCj2P4U0FEWXR1sQrzjFusY3kh6Pk6lrM0FaoWa7lM7tUNkWA6szETXMdXaPb4oZ31xpB9Otq6Gyn9PTDpCPSsyjWjBYMB0GA1UdDgQWBBQVLSdUYatMocbVy8NbwB4c04XtEjAfBgNVHSMEGDAWgBQVLSdUYatMocbVy8NbwB4c04XtEjAJBgNVHRMEAjAAMAsGA1UdDwQEAwIDiDAKBggqhkjOPQQDAgNIADBFAiA0ctHEyTmLaEb65HEPLoWy0oni8VsBzgdZ12UJefL8qgIhAMQXzaoX12cdmYd56hWZVj02Np0tdIgHMIokBXMfBixg\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"EC\",\n" //
				+ "        \"crv\": \"P-256\",\n" //
				+ "        \"x\": \"KPY_hTQURZdHWxCvOMW6xjeSHo-TqWszQVqhZruUzu0\",\n" //
				+ "        \"y\": \"UNkWA6szETXMdXaPb4oZ31xpB9Otq6Gyn9PTDpCPSsw\"\n" //
				+ "    }\n" //
				+ "}\n"));

		// must have private key to verify as authoritative
		assertThrows(IllegalArgumentException.class, () -> verifier.verify(publicId));

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(2, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();

		final var cert = wellKnown.getCertificateChain()[0];
		assertEquals(pki.getName(), X500Utils.getCommonName(cert.getSubjectX500Principal()));
		assertNotNull(sub.getPrivateCredentials(WebKey.class).iterator().next().getPrivateKey());
	}

	@Test
	public void testSelfSignedEECertOnly() throws Exception {
		final var id = pkp( // For verification and demonstration purposes only. NOT FOR PRODUCTION USE
				"{\n" //
						+ "    \"type\": \"pki\",\n" //
						+ "    \"alg\": \"EdDSA\",\n" //
						+ "    \"encrypt_alg\": null,\n" //
						+ "    \"enc\": null,\n" //
						+ "    \"jwk\": {\n" //
						+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiFactoryTest\",\n" //
						+ "        \"x5c\": [\n" //
						+ "            \"MIICUjCCAgSgAwIBAgIUKy7YHtZt0R79XTxlhuB0pojOwKIwBQYDK2VwMIGZMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTQwMgYDVQQDDCt1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraUZhY3RvcnlUZXN0MCAXDTI0MDcwMzIxNDkyMloYDzIxMjQwNzA0MjE0OTIyWjCBmTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE0MDIGA1UEAwwrdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lGYWN0b3J5VGVzdDAqMAUGAytlcAMhACWxmZAFt2MMJR7bP9/wJGPmBa3j6gsy6VatfViWqXhpo1owWDAdBgNVHQ4EFgQUQO3UhTeQgJtlMAXgwgif8XDxaZ4wHwYDVR0jBBgwFoAUQO3UhTeQgJtlMAXgwgif8XDxaZ4wCQYDVR0TBAIwADALBgNVHQ8EBAMCB4AwBQYDK2VwA0EAhsWSEWSIwPi/LPaF/S98hrl6w1hWZOSvOzUuqI9TB5Azs8CT6ASWbqDheMpLj6PhPles2oYoCDvnTVRJjqFRBw==\"\n"
						+ "        ],\n" //
						+ "        \"kty\": \"OKP\",\n" //
						+ "        \"crv\": \"Ed25519\",\n" //
						+ "        \"x\": \"JbGZkAW3YwwlHts_3_AkY-YFrePqCzLpVq19WJapeGk\"\n" //
						+ "    }\n" //
						+ "}\n");
		final var pki = (PkiPrincipal) PkiFactory.from(id);
		final var verifier = (PkiVerifier) PkiFactory.trust(id);
		assertFalse(verifier.isAuthoritative());

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:verify:urn:example:iu-java-auth-pki#PkiFactoryTest; trustAnchor: CN=urn:example:iu-java-auth-pki\\#PkiFactoryTest,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		verifier.verify(pki);

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size(), pub::toString);

		final var wellKnown = (WebKey) pub.iterator().next();

		final var cert = wellKnown.getCertificateChain()[0];
		assertEquals(pki.getName(), X500Utils.getCommonName(cert.getSubjectX500Principal()));

		assertTrue(sub.getPrivateCredentials(WebKey.class).isEmpty());
	}

	@Test
	public void testRejectDataEncipherment() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> PkiFactory.from( //
				pkp( // For verification and demonstration purposes only. NOT FOR PRODUCTION USE
						"{\n" //
								+ "    \"type\": \"pki\",\n" //
								+ "    \"alg\": \"EdDSA\",\n" //
								+ "    \"encrypt_alg\": null,\n" //
								+ "    \"enc\": null,\n" //
								+ "    \"jwk\": {\n" //
								+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiFactoryTest\",\n" //
								+ "        \"x5c\": [\n" //
								+ "            \"MIICUjCCAgSgAwIBAgIUUByF6y/anhjoOXd/oHLHdTzS0qowBQYDK2VwMIGZMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTQwMgYDVQQDDCt1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraUZhY3RvcnlUZXN0MCAXDTI0MDcwMzIzMDExNVoYDzIxMjQwNzA0MjMwMTE1WjCBmTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE0MDIGA1UEAwwrdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lGYWN0b3J5VGVzdDAqMAUGAytlcAMhALjg0xEgLmEDh3XmW9pX7cyeEam4sEITVX0J7nMw2Ih/o1owWDAdBgNVHQ4EFgQUDaY0cXaMVv3Dc85/jNCsa7wSl94wHwYDVR0jBBgwFoAUDaY0cXaMVv3Dc85/jNCsa7wSl94wCQYDVR0TBAIwADALBgNVHQ8EBAMCBBAwBQYDK2VwA0EAh0oHDfBXO0Uoshre2dT/UPXY/vXcxejy8PMeZXGvqGLSeg8rt0vv5qnAJKxoFbYYK8NKulfjyo75vJGI8s8xCw==\"\n"
								+ "        ],\n" //
								+ "        \"kty\": \"OKP\",\n" //
								+ "        \"crv\": \"Ed25519\",\n" //
								+ "        \"x\": \"uODTESAuYQOHdeZb2lftzJ4RqbiwQhNVfQnuczDYiH8\",\n" //
								+ "        \"d\": \"Su1H7AYRpmr6OYv3HpvINiHS3ynvtBwkdan-3kZdgn8\"\n" //
								+ "    }\n" //
								+ "}\n" //
								+ "")));
	}

	@Test
	public void testRejectDataEnciphermentPublic() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> PkiFactory.from( //
				pkp( // For verification and demonstration purposes only. NOT FOR PRODUCTION USE
						"{\n" //
								+ "    \"type\": \"pki\",\n" //
								+ "    \"alg\": \"EdDSA\",\n" //
								+ "    \"encrypt_alg\": null,\n" //
								+ "    \"enc\": null,\n" //
								+ "    \"jwk\": {\n" //
								+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiFactoryTest\",\n" //
								+ "        \"x5c\": [\n" //
								+ "            \"MIICUjCCAgSgAwIBAgIUUByF6y/anhjoOXd/oHLHdTzS0qowBQYDK2VwMIGZMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTQwMgYDVQQDDCt1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraUZhY3RvcnlUZXN0MCAXDTI0MDcwMzIzMDExNVoYDzIxMjQwNzA0MjMwMTE1WjCBmTELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDE0MDIGA1UEAwwrdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lGYWN0b3J5VGVzdDAqMAUGAytlcAMhALjg0xEgLmEDh3XmW9pX7cyeEam4sEITVX0J7nMw2Ih/o1owWDAdBgNVHQ4EFgQUDaY0cXaMVv3Dc85/jNCsa7wSl94wHwYDVR0jBBgwFoAUDaY0cXaMVv3Dc85/jNCsa7wSl94wCQYDVR0TBAIwADALBgNVHQ8EBAMCBBAwBQYDK2VwA0EAh0oHDfBXO0Uoshre2dT/UPXY/vXcxejy8PMeZXGvqGLSeg8rt0vv5qnAJKxoFbYYK8NKulfjyo75vJGI8s8xCw==\"\n"
								+ "        ],\n" //
								+ "        \"kty\": \"OKP\",\n" //
								+ "        \"crv\": \"Ed25519\",\n" //
								+ "        \"x\": \"uODTESAuYQOHdeZb2lftzJ4RqbiwQhNVfQnuczDYiH8\"\n" //
								+ "    }\n" //
								+ "}\n" //
								+ "")));
	}

	@Test
	public void testSelfSignedEEForEncrypt() throws Exception {
		final var id = pkp( // For verification and demonstration purposes only. NOT FOR PRODUCTION USE
				"{\n" //
						+ "    \"type\": \"pki\",\n" //
						+ "    \"alg\": \"RS256\",\n" //
						+ "    \"encrypt_alg\": \"RSA-OAEP\",\n" //
						+ "    \"enc\": \"A256GCM\",\n" //
						+ "    \"jwk\": {\n" //
						+ "        \"kid\": \"urn:example:iu-java-auth-pki#keyEncrypt\",\n" //
						+ "        \"x5c\": [\n" //
						+ "            \"MIIEFjCCAv6gAwIBAgIUa1Fl4jU8gjGmvYISCGugNQH9LpgwDQYJKoZIhvcNAQELBQAwgZUxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMDAuBgNVBAMMJ3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5RW5jcnlwdDAgFw0yNDA3MDMyMzIwNTNaGA8yMTI0MDcwNDIzMjA1M1owgZUxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMDAuBgNVBAMMJ3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5RW5jcnlwdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANqs1T9z+pEPoZpnLo64HmGszLBjNesUVT2Np2ehdtkQvIHVhhsGfGnWcSeuxgi72M2lufN/nvpOL/rc5yN6q1WddBdNm+iYCHD8fiB6RglKHB3YsbmF0UGptVNkO8dp3rqIL0ywnEqWb6lY/yhKaY9w1z4TfY8NNma9ToDHefrrV5S7EiNJsJvTGv7Fu6XVmb9xmof0EslwqS54yETK3mco7pkXxmt112mBOPWplUV7tQ8aq2XZPzvIP8ODETj3vV+un9NFxitW94T1ys9dv6/BkcI0uCUMhJ7YLwoFzw3pi2p0KFZ318Aypx7eZleFqwTjk/RdFBdWouJErhopPTECAwEAAaNaMFgwHQYDVR0OBBYEFP8BfA+o/5ewMscNDrkX9WGHhIQiMB8GA1UdIwQYMBaAFP8BfA+o/5ewMscNDrkX9WGHhIQiMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgWgMA0GCSqGSIb3DQEBCwUAA4IBAQCGkeeu6h2W2ale6fAJSEEDjUvtEgV0taHWIKRVCJhOefQoyPv4d5pmjU9LJ/7Pz97KK5nCgKkEPwFWOaJ5ghrRYhPbdj0NtsLpolvZnjjTHGdiqE4PveeYABlMroTDcBYjH1sPaE1z6qJa4Rsqwe/Sij/P+md7eBKs0OIegLZh6h3tiGMdeliyxTN7evirr0JswK68Os0rP3abDem+sRQiztHQ6ujJWb8fkef94/ebvyRyc5JDDvqpT8AT9LTgfy56xC7aCfuyjFpbUezq9s0ZfZtiohkRXOdaiWcUuqFqY31/6c2XhqHuRklv8o9n1URH979631kEtyvyi+sX2vl8\"\n"
						+ "        ],\n" //
						+ "        \"kty\": \"RSA\",\n" //
						+ "        \"n\": \"2qzVP3P6kQ-hmmcujrgeYazMsGM16xRVPY2nZ6F22RC8gdWGGwZ8adZxJ67GCLvYzaW583-e-k4v-tznI3qrVZ10F02b6JgIcPx-IHpGCUocHdixuYXRQam1U2Q7x2neuogvTLCcSpZvqVj_KEppj3DXPhN9jw02Zr1OgMd5-utXlLsSI0mwm9Ma_sW7pdWZv3Gah_QSyXCpLnjIRMreZyjumRfGa3XXaYE49amVRXu1DxqrZdk_O8g_w4MROPe9X66f00XGK1b3hPXKz12_r8GRwjS4JQyEntgvCgXPDemLanQoVnfXwDKnHt5mV4WrBOOT9F0UF1ai4kSuGik9MQ\",\n"
						+ "        \"e\": \"AQAB\",\n" //
						+ "        \"d\": \"Ba53ZOxnvnScQ4V6R3F_spR5hfyx5i9zToPbbV0b2CRv7WSllRasTVipvHj1Qr1UsvUjGXE_qWu2Ieuy4rBZesI6Ra-5xQeMMplQ0pmyr6OaCul6JqKULwNQh3_jcLkutQR8TB8LlIGHmawLBxo112j8YqvBd6HFf9Jb04s7P1ppKxWTgySztFflm-iEyBUgaT4IN53XvkVJWm0RDEf272ZgBrhYsZIa_zdSAee3t9t1z7UQT1n3OaiB0dMGETK2qYTMhyXJOHqISKB_1TkFeVtB2daQUyIjTs7Pnpa74NvRcXWTXZdPpC2UoS4xXiiRziXiZ6hSSNrwsT6PiXxQ8Q\",\n"
						+ "        \"p\": \"_eK38ao2eBgktWN1ZpMRk4QltyYowBss08jbiUiAfy2Q0Ofruk2n1cX_uPBL8DSVE3Q72R399nXiZHXMy0LI1wHDX0b6SNkvVkk5n_JRJ8f9Vpot3MtTDE5WNo1ERTs5cr-w5bmG8XCjyY601jNFbxRhJX_ZxrmpO8jTmqMkv8k\",\n"
						+ "        \"q\": \"3H8Ly3UYp_No8dUtbHltdtJ6FnoagC494FcUlhiEEmtGMGVN9o3qyYybEA4gjG-NyiKmlvt6OZttlX5fRwSv13xBxMjWM-AK392dM9At09-wNDnkoPyqodr-xJiERxfuB9yjCZty1oazin7v3GOOOyex-FpRnzPFwGBncwCRVik\",\n"
						+ "        \"dp\": \"qSup7-DAXLORuj_kmY3Dt2zliK1nl-JDs3byOf7SiGu_RERVQZW_EOXXKM8Neqg-8XCQ9HJUqCYSzWflJ0d_9ixZl6H-4g29yhwOxrI7O2u6NjuT9byRwPBt5_mnlQ4KkJiEcf52mWi56nMpslUFnieRN-CCJzXNO2XtAexVb2k\",\n"
						+ "        \"dq\": \"LYHzFzzFp_Qu1qqew2KUWw-5ruXojkf88U5Hq2rH511IkTh3TMmhiZOBdWDVCucDJpLcOxEP-s-_YDlCxM-M0zaMBGdG_lHhLK76gPUYYEAsh7rjGu7K8LjYGA86Tcn8kJbS92qj2u7WI2Frc9sAQelBa_aIHgl7aOb8stmNJXE\",\n"
						+ "        \"qi\": \"sgmbDXwxqB73kOrRya0qXEyaAM-0H4BWtTDWdUroCfMmJuCBmCQIMHNX5GldmYLH8wGVxVzRewkeoTNm1c1krCTblma0W4bFz08u1pprjUIO7j42w-jZuCi99jPMDGISSX1aFtIbq7fOladhmr0Mxj2ZcMZBGm3TBSybBQdbOUY\"\n"
						+ "    }\n" //
						+ "}\n");
		final var pki = (PkiPrincipal) PkiFactory.from(id);
		final var verifier = (PkiVerifier) PkiFactory.trust(id);

		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki#keyEncrypt; trustAnchor: CN=urn:example:iu-java-auth-pki\\#keyEncrypt,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
		assertDoesNotThrow(() -> verifier.verify(pki));

		final var keys = pki.getSubject().getPrivateCredentials(WebKey.class);
		assertEquals(2, keys.size(), keys::toString);

		final var sign = keys.stream().filter(a -> "verify".equals(a.getKeyId())).findFirst().get();
		assertNull(sign.getUse());
		assertEquals(Set.of(Operation.VERIFY, Operation.SIGN), sign.getOps());

		final var decrypt = keys.stream().filter(a -> "encrypt".equals(a.getKeyId())).findFirst().get();
		assertNull(decrypt.getUse());
		assertEquals(Set.of(Operation.WRAP, Operation.UNWRAP), decrypt.getOps());

		final var pubpki = PkiFactory.from(pkp(
				// For verification and demonstration purposes only. NOT FOR PRODUCTION USE
				"{\n" //
						+ "    \"type\": \"pki\",\n" //
						+ "    \"alg\": \"RS256\",\n" //
						+ "    \"encrypt_alg\": \"RSA-OAEP\",\n" //
						+ "    \"enc\": \"A256GCM\",\n" //
						+ "    \"jwk\": {\n" //
						+ "        \"kid\": \"urn:example:iu-java-auth-pki#keyEncrypt\",\n" //
						+ "        \"x5c\": [\n" //
						+ "            \"MIIEFjCCAv6gAwIBAgIUa1Fl4jU8gjGmvYISCGugNQH9LpgwDQYJKoZIhvcNAQELBQAwgZUxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMDAuBgNVBAMMJ3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5RW5jcnlwdDAgFw0yNDA3MDMyMzIwNTNaGA8yMTI0MDcwNDIzMjA1M1owgZUxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMDAuBgNVBAMMJ3VybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5RW5jcnlwdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANqs1T9z+pEPoZpnLo64HmGszLBjNesUVT2Np2ehdtkQvIHVhhsGfGnWcSeuxgi72M2lufN/nvpOL/rc5yN6q1WddBdNm+iYCHD8fiB6RglKHB3YsbmF0UGptVNkO8dp3rqIL0ywnEqWb6lY/yhKaY9w1z4TfY8NNma9ToDHefrrV5S7EiNJsJvTGv7Fu6XVmb9xmof0EslwqS54yETK3mco7pkXxmt112mBOPWplUV7tQ8aq2XZPzvIP8ODETj3vV+un9NFxitW94T1ys9dv6/BkcI0uCUMhJ7YLwoFzw3pi2p0KFZ318Aypx7eZleFqwTjk/RdFBdWouJErhopPTECAwEAAaNaMFgwHQYDVR0OBBYEFP8BfA+o/5ewMscNDrkX9WGHhIQiMB8GA1UdIwQYMBaAFP8BfA+o/5ewMscNDrkX9WGHhIQiMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgWgMA0GCSqGSIb3DQEBCwUAA4IBAQCGkeeu6h2W2ale6fAJSEEDjUvtEgV0taHWIKRVCJhOefQoyPv4d5pmjU9LJ/7Pz97KK5nCgKkEPwFWOaJ5ghrRYhPbdj0NtsLpolvZnjjTHGdiqE4PveeYABlMroTDcBYjH1sPaE1z6qJa4Rsqwe/Sij/P+md7eBKs0OIegLZh6h3tiGMdeliyxTN7evirr0JswK68Os0rP3abDem+sRQiztHQ6ujJWb8fkef94/ebvyRyc5JDDvqpT8AT9LTgfy56xC7aCfuyjFpbUezq9s0ZfZtiohkRXOdaiWcUuqFqY31/6c2XhqHuRklv8o9n1URH979631kEtyvyi+sX2vl8\"\n"
						+ "        ],\n" //
						+ "        \"kty\": \"RSA\",\n" //
						+ "        \"n\": \"2qzVP3P6kQ-hmmcujrgeYazMsGM16xRVPY2nZ6F22RC8gdWGGwZ8adZxJ67GCLvYzaW583-e-k4v-tznI3qrVZ10F02b6JgIcPx-IHpGCUocHdixuYXRQam1U2Q7x2neuogvTLCcSpZvqVj_KEppj3DXPhN9jw02Zr1OgMd5-utXlLsSI0mwm9Ma_sW7pdWZv3Gah_QSyXCpLnjIRMreZyjumRfGa3XXaYE49amVRXu1DxqrZdk_O8g_w4MROPe9X66f00XGK1b3hPXKz12_r8GRwjS4JQyEntgvCgXPDemLanQoVnfXwDKnHt5mV4WrBOOT9F0UF1ai4kSuGik9MQ\",\n"
						+ "        \"e\": \"AQAB\"\n" //
						+ "    }\n" //
						+ "}\n"));
		final var pubkeys = pubpki.getSubject().getPublicCredentials(WebKey.class);
		assertEquals(2, pubkeys.size(), pubkeys::toString);

		final var verify = pubkeys.stream().filter(a -> "verify".equals(a.getKeyId())).findFirst().get();
		assertNull(verify.getUse());
		assertEquals(Set.of(Operation.VERIFY), verify.getOps());

		final var encrypt = pubkeys.stream().filter(a -> "encrypt".equals(a.getKeyId())).findFirst().get();
		assertNull(encrypt.getUse());
		assertEquals(Set.of(Operation.WRAP), encrypt.getOps());
	}

	@Test
	public void testSelfSignedEEForKeyAgreement() throws Exception {
		final var id = pkp(
				// For verification and demonstration purposes only. NOT FOR PRODUCTION USE
				"{\n" //
						+ "    \"type\": \"pki\",\n" //
						+ "    \"alg\": \"ES256\",\n" //
						+ "    \"encrypt_alg\": \"ECDH-ES\",\n" //
						+ "    \"enc\": \"A128GCM\",\n" //
						+ "    \"jwk\": {\n" //
						+ "        \"kid\": \"urn:example:iu-java-auth-pki#keyAgreement\",\n" //
						+ "        \"x5c\": [\n" //
						+ "            \"MIICjDCCAjKgAwIBAgIUFizHe9J0fnp/xnhiQPIERlk/oL0wCgYIKoZIzj0EAwIwgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5QWdyZWVtZW50MB4XDTI0MDcwMzE5MDIzOVoXDTI2MTAxMTE5MDIzOVowgZcxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMjAwBgNVBAMMKXVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kja2V5QWdyZWVtZW50MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELZG5cnjhF0l7JDEJYMFA9mSdV0Qh+VnmWKCXr6Hdxuj1MvNzT6Ky8FGdWdhu1G7Dartb7N1MzlM38VKOfaMWmqNaMFgwHQYDVR0OBBYEFEaYJoQIfYpjwxLdvtrifEnuFou9MB8GA1UdIwQYMBaAFEaYJoQIfYpjwxLdvtrifEnuFou9MAkGA1UdEwQCMAAwCwYDVR0PBAQDAgOIMAoGCCqGSM49BAMCA0gAMEUCICvwFb8rQoRvDhSmJ7vDKYC15k3aZQdq4oFPxidX6XSMAiEAydiku2EXHKd8kWov0c2SsUeVTY9KRyTsAFsw344DyBo=\"\n" //
						+ "        ],\n" //
						+ "        \"kty\": \"EC\",\n" //
						+ "        \"crv\": \"P-256\",\n" //
						+ "        \"x\": \"LZG5cnjhF0l7JDEJYMFA9mSdV0Qh-VnmWKCXr6Hdxug\",\n" //
						+ "        \"y\": \"9TLzc0-isvBRnVnYbtRuw2q7W-zdTM5TN_FSjn2jFpo\",\n" //
						+ "        \"d\": \"eEWPRKp8j0l3oCr1SQNmOJCCZvZyE8qhYp0BoLc-_rA\"\n" //
						+ "    }\n" //
						+ "}\n");

		final var pki = (PkiPrincipal) PkiFactory.from(id);
		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
				"pki:auth:urn:example:iu-java-auth-pki#keyAgreement; trustAnchor: CN=urn:example:iu-java-auth-pki\\#keyAgreement,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");

		final var verifier = (PkiVerifier) PkiFactory.trust(pki);
		assertTrue(verifier.isAuthoritative());
		assertDoesNotThrow(() -> verifier.verify(pki));

		final var keys = pki.getSubject().getPrivateCredentials(WebKey.class);
		final var key = keys.stream().filter(a -> "encrypt".equals(a.getKeyId())).findAny().get();
		assertNull(key.getUse());
		assertEquals(Set.of(Operation.DERIVE_KEY), key.getOps());
	}

	@Test
	public void testExpiredEE() throws Exception {
		final var id = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"EdDSA\",\n" //
				+ "    \"encrypt_alg\": null,\n" //
				+ "    \"enc\": null,\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#expired\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICjTCCAg2gAwIBAgIULgtpCpGnSH76irCqvzsohlr9wXUwBQYDK2VxMIGSMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMS0wKwYDVQQDDCR1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI2V4cGlyZWQwHhcNMjQwNDIyMTE1NjU4WhcNMjQwNDIzMTE1NjU4WjCBkjELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEtMCsGA1UEAwwkdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNleHBpcmVkMEMwBQYDK2VxAzoAQiBG7vOAhJFDUM1lSoR9k1XrQRUfuGu60+FxVwKulhooFdSxz9ffZk7dOpMGybAHWiK6cBKAM0OAo1owWDAdBgNVHQ4EFgQUag2+Gg9UTkObj0vyxu547xLv3/gwHwYDVR0jBBgwFoAUag2+Gg9UTkObj0vyxu547xLv3/gwCQYDVR0TBAIwADALBgNVHQ8EBAMCA4gwBQYDK2VxA3MAk3KX8t3tzKA2N3mgclkkZAXJuavgzAMGlOgtZ8C0jTtP/QAqxSwII2VyjZjXkqQOJ8rZ70pxppuAPeUr6nxms/YXxsmL16VfiPmzVV2twIv0f5ISsvJY0jfEdwOnrO5c27KtbfL218KVOxKDzkOObzQA\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"OKP\",\n" //
				+ "        \"crv\": \"Ed448\",\n" //
				+ "        \"x\": \"QiBG7vOAhJFDUM1lSoR9k1XrQRUfuGu60-FxVwKulhooFdSxz9ffZk7dOpMGybAHWiK6cBKAM0OA\"\n" //
				+ "    }\n" //
				+ "}\n");
		final var pki = (PkiPrincipal) PkiFactory.from(id);
		final var verifier = (PkiVerifier) PkiFactory.trust(id);

		assertInstanceOf(CertificateExpiredException.class, assertInstanceOf(CertPathValidatorException.class,
				assertThrows(IllegalStateException.class, () -> verifier.verify(pki)).getCause()).getCause());
	}

//	@Test
//	public void testNoFragmentEE() throws Exception {
//		final var id = privateKeyPrincipal(SELF_SIGNED_PK + NOFRAGMENT_EE);
//		final var pki = PkiFactory.from(id);
//
//		IuTestLogger.expect("iu.auth.pki.PkiVerifier", Level.INFO,
//				"pki:auth:urn:example:iu-java-auth-pki-notag; trustAnchor: CN=urn:example:iu-java-auth-pki-notag,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");
//		IuPrincipalIdentity.verify(pki, pki.getName());
//
//		final var sub = pki.getSubject();
//		assertEquals(Set.of(pki), sub.getPrincipals());
//
//		final var pub = sub.getPublicCredentials();
//		assertEquals(1, pub.size());
//		final var wellKnown = (WebKey) pub.iterator().next();
//		assertEquals("verify", wellKnown.getKeyId());
//	}
//
	@SuppressWarnings("deprecation")
	@Test
	public void testPublicCA() throws Exception {
		final CertPath iuEdu;
		final X509Certificate userTrust;
		final Iterable<X509CRL> crl;
		try {
			final var http = HttpClient.newHttpClient();
			final var resp = http.send(HttpRequest.newBuilder(URI.create("https://www.iu.edu/index.html"))
					.method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.discarding());

			final var x509 = CertificateFactory.getInstance("X.509");
			iuEdu = x509.generateCertPath(List.of(resp.sslSession().get().getPeerCertificates()));
			userTrust = (X509Certificate) x509.generateCertificate(http.send(
					HttpRequest.newBuilder(URI.create("http://crt.usertrust.com/USERTrustRSAAddTrustCA.crt")).build(),
					BodyHandlers.ofInputStream()).body());

			crl = IuIterable.iter(
					(X509CRL) x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.incommon-rsa.org/InCommonRSAServerCA.crl")).build(),
							BodyHandlers.ofInputStream()).body()),
					(X509CRL) x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.usertrust.com/USERTrustRSACertificationAuthority.crl"))
							.build(), BodyHandlers.ofInputStream()).body()) //
			);
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort("unable to read public key data for verifying iu.edu " + e);
			return;
		}

		final var iuEduCert = (X509Certificate) iuEdu.getCertificates().get(0);
		final var jwkBuilder = WebKey.builder(iuEduCert.getPublicKey());
		jwkBuilder.keyId(X500Utils.getCommonName(iuEduCert.getSubjectX500Principal()));
		jwkBuilder.cert(iuEdu.getCertificates().toArray(X509Certificate[]::new));

		final var iuEduPkpBuilder = IuJson.object().add("type", "pki");
		IuJson.add(iuEduPkpBuilder, "alg", Algorithm.RS256, Algorithm.JSON);
		IuJson.add(iuEduPkpBuilder, "encrypt_alg", Algorithm.RSA_OAEP, Algorithm.JSON);
		IuJson.add(iuEduPkpBuilder, "enc", Encryption.A128GCM, Encryption.JSON);
		IuJson.add(iuEduPkpBuilder, "jwk", jwkBuilder.build(), WebKey.JSON);

		final var iuEduId = (PkiPrincipal) PkiFactory.from(pkp(iuEduPkpBuilder.build().toString()));
		assertEquals("iu.edu", iuEduId.getName());

		final var verifier = new CaVerifier(new IuCertificateAuthority() {
			@Override
			public Iterable<X509CRL> getCrl() {
				return crl;
			}

			@Override
			public X509Certificate getCertificate() {
				return userTrust;
			}
		});

		IuTestLogger.expect("iu.auth.pki.CaVerifier", Level.INFO,
				"ca:verify:iu.edu; trustAnchor: CN=USERTrust RSA Certification Authority,O=The USERTRUST Network,L=Jersey City,ST=New Jersey,C=US");
		verifier.verify(iuEduId);
	}

	@Test
	public void testPrivateCA() throws Exception {
		final var ca = ca("{\n" //
				+ "    \"type\": \"ca\",\n" //
				+ "    \"certificate\": \"MIICojCCAiKgAwIBAgIUaIhkhmrEMsACboaTb5IQ7QuQnxswBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3RfQ0EwHhcNMjQwNzA0MTAwNTIxWhcNMjcxMDEyMTAwNTIxWjCBmDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEzMDEGA1UEAwwqdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lTcGlUZXN0X0NBMEMwBQYDK2VxAzoA88etT1drSiXcMsvPqofFKQyhyDcdVPUxmmUwaejRIbaHTTk7YSUYux2Z/CH8F014+zuAopfLkTyAo2MwYTAdBgNVHQ4EFgQUX3Kml++39stE417fW/FH6d2fDeUwHwYDVR0jBBgwFoAUX3Kml++39stE417fW/FH6d2fDeUwEgYDVR0TAQH/BAgwBgEB/wIBADALBgNVHQ8EBAMCAQYwBQYDK2VxA3MA1eaxlxrGFLs4x0rL4H8diEl2T++Oge8ifWTUg3YE+kCdWY8XiubpsoaVBj5kfXWOaKUTdV2KV5kA7rT+cEDStKSjcrXWfmYCq8BOQZatQPYIkqYQoQ6VOXaOqLhkH1/JVqXFiBfPQOS9LH+Ig332XycA\",\n"
				+ "    \"crl\": \"MIIBPzCBwDAFBgMrZXEwgZgxCzAJBgNVBAYTAlVTMRAwDgYDVQQIDAdJbmRpYW5hMRQwEgYDVQQHDAtCbG9vbWluZ3RvbjEbMBkGA1UECgwSSW5kaWFuYSBVbml2ZXJzaXR5MQ8wDQYDVQQLDAZTVEFSQ0gxMzAxBgNVBAMMKnVybjpleGFtcGxlOml1LWphdmEtYXV0aC1wa2kjUGtpU3BpVGVzdF9DQRcNMjQwNzA0MTAwNjQ4WhcNMjcxMDExMTAwNjQ4WjAFBgMrZXEDcwDNDmap+lNLtyU/kl0Zy/5QPdVeHe7vtkkVq5r7YBrOqQkhJIZi0gnQoQ3pbw+Ik/4+ouA+0H+KSIAX9reRtTSOIx6wL6/nK1EotJRLj9hRBMcBiZCcVDwn5i3mhzWovs91aiueuElQpb4S4EN5XcwwBwA=\"\n"
				+ "}\n");

		final var id = pkp("{\n" //
				+ "    \"type\": \"pki\",\n" //
				+ "    \"alg\": \"EdDSA\",\n" //
				+ "    \"encrypt_alg\": null,\n" //
				+ "    \"enc\": null,\n" //
				+ "    \"jwk\": {\n" //
				+ "        \"kid\": \"urn:example:iu-java-auth-pki#PkiSpiTest_EE\",\n" //
				+ "        \"x5c\": [\n" //
				+ "            \"MIICgDCCAgCgAwIBAgIUU2lj6zaZYSkXCYSih68Cl1ki0xswBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3RfQ0EwHhcNMjQwNzA0MTAwNjQ4WhcNMjYxMDEyMTAwNjQ4WjCBmDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEzMDEGA1UEAwwqdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lTcGlUZXN0X0VFMCowBQYDK2VwAyEARqVKthPcj7VR5Lke4niNTNdrcG4CIIGaNW0Ise1xF7SjWjBYMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgeAMB0GA1UdDgQWBBQtje2r8sIa5CcDACX/3Yxay0ZagzAfBgNVHSMEGDAWgBRfcqaX77f2y0TjXt9b8Ufp3Z8N5TAFBgMrZXEDcwDHO7Nrdi01EXUwEu9ZMthcYx5bsfX1m/5I0CTKA5ArXU7LFw9zJ8Yd/VdUXE1XLDKZaxqeI3ofeYCQ1fUrzr1U+Bhf6zpUbpBjuDRavXg8s4N3e/AX365yfrGeKRovTJ31+qA0S8RwdODGCosew4xACwA=\",\n"
				+ "            \"MIICojCCAiKgAwIBAgIUaIhkhmrEMsACboaTb5IQ7QuQnxswBQYDK2VxMIGYMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3RfQ0EwHhcNMjQwNzA0MTAwNTIxWhcNMjcxMDEyMTAwNTIxWjCBmDELMAkGA1UEBhMCVVMxEDAOBgNVBAgMB0luZGlhbmExFDASBgNVBAcMC0Jsb29taW5ndG9uMRswGQYDVQQKDBJJbmRpYW5hIFVuaXZlcnNpdHkxDzANBgNVBAsMBlNUQVJDSDEzMDEGA1UEAwwqdXJuOmV4YW1wbGU6aXUtamF2YS1hdXRoLXBraSNQa2lTcGlUZXN0X0NBMEMwBQYDK2VxAzoA88etT1drSiXcMsvPqofFKQyhyDcdVPUxmmUwaejRIbaHTTk7YSUYux2Z/CH8F014+zuAopfLkTyAo2MwYTAdBgNVHQ4EFgQUX3Kml++39stE417fW/FH6d2fDeUwHwYDVR0jBBgwFoAUX3Kml++39stE417fW/FH6d2fDeUwEgYDVR0TAQH/BAgwBgEB/wIBADALBgNVHQ8EBAMCAQYwBQYDK2VxA3MA1eaxlxrGFLs4x0rL4H8diEl2T++Oge8ifWTUg3YE+kCdWY8XiubpsoaVBj5kfXWOaKUTdV2KV5kA7rT+cEDStKSjcrXWfmYCq8BOQZatQPYIkqYQoQ6VOXaOqLhkH1/JVqXFiBfPQOS9LH+Ig332XycA\"\n"
				+ "        ],\n" //
				+ "        \"kty\": \"OKP\",\n" //
				+ "        \"crv\": \"Ed25519\",\n" //
				+ "        \"x\": \"RqVKthPcj7VR5Lke4niNTNdrcG4CIIGaNW0Ise1xF7Q\",\n" //
				+ "        \"d\": \"gkuk81cJdSAnMQEzWG9IwTvZFBCQrzT7Y5BbPf_zK_Y\"\n" //
				+ "    }\n" //
				+ "}\n");
		final var pki = (PkiPrincipal) PkiFactory.from(id);

		IuTestLogger.expect("iu.auth.pki.CaVerifier", Level.INFO,
				"ca:verify:urn:example:iu-java-auth-pki#PkiSpiTest_EE; trustAnchor: CN=urn:example:iu-java-auth-pki\\#PkiSpiTest_CA,OU=STARCH,O=Indiana University,L=Bloomington,ST=Indiana,C=US");

		final var verifier = new CaVerifier(ca);
		assertFalse(verifier.isAuthoritative());
		verifier.verify(pki);
	}

//
//	private static final String PK2 = "-----BEGIN PRIVATE KEY-----\n" //
//			+ "MC4CAQAwBQYDK2VwBCIEIKDugq7tgDXBWvu24W0Flikh7URBRwFpjKmsq3+Qhv07\n" //
//			+ "-----END PRIVATE KEY-----\n";
//
//	private static final String PK2_CERT = "-----BEGIN CERTIFICATE-----\n" //
//			+ "MIICIzCCAdWgAwIBAgIUXFUgBaS/LWo1dyWhIosURCkGYo4wBQYDK2VwMIGAMQsw\n"
//			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
//			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
//			+ "MRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNwazIwIBcNMjQwNTE3MjM0MjI2WhgP\n"
//			+ "MjEyNDA1MTgyMzQyMjZaMIGAMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFu\n"
//			+ "YTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVy\n"
//			+ "c2l0eTEPMA0GA1UECwwGU1RBUkNIMRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNw\n"
//			+ "azIwKjAFBgMrZXADIQC4mwCSvPxK+vYOWLvNGRzMPnNgiNigJcH8n9VLCPHnhaNd\n"
//			+ "MFswHQYDVR0OBBYEFHjhN0OongfMsuWHyahJ+hnKL3zpMB8GA1UdIwQYMBaAFHjh\n"
//			+ "N0OongfMsuWHyahJ+hnKL3zpMAwGA1UdEwQFMAMCAf8wCwYDVR0PBAQDAgEGMAUG\n"
//			+ "AytlcANBANwdh+Vz5664X7YCUrLYf0XdKoMNliKQ4BHsFclKOujMKlYsWdgrSKjB\n"
//			+ "NwZduVMNjT7aGsDrYocJJc+ATzRsHQU=\n" //
//			+ "-----END CERTIFICATE-----\n";
//
//	private static final String PK3 = "-----BEGIN PRIVATE KEY-----\n" //
//			+ "MC4CAQAwBQYDK2VwBCIEIClnfQHZGjmICLWmZx6kEeazRnOuO8DLTOWLDSPCX/v+\n" //
//			+ "-----END PRIVATE KEY-----\n";
//
//	private static final String PK3_CERT = "-----BEGIN CERTIFICATE-----\n" //
//			+ "MIICIzCCAdWgAwIBAgIULGJHtoFDBItb5Ci9TBj9TT/ZV3AwBQYDK2VwMIGAMQsw\n"
//			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n"
//			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n"
//			+ "MRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNwazIwIBcNMjQwNTE3MjM0OTU3WhgP\n"
//			+ "MjEyNDA1MTgyMzQ5NTdaMIGAMQswCQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFu\n"
//			+ "YTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVy\n"
//			+ "c2l0eTEPMA0GA1UECwwGU1RBUkNIMRswGQYDVQQDDBJQa2lGYWN0b3J5VGVzdCNw\n"
//			+ "azIwKjAFBgMrZXADIQCOewWrIjwt6urqjpzwsHESUieYAVEtFP7hfscNEei+gaNd\n"
//			+ "MFswHQYDVR0OBBYEFK8BIOvGpWr7758g4xUgrboy0ocaMB8GA1UdIwQYMBaAFK8B\n"
//			+ "IOvGpWr7758g4xUgrboy0ocaMAwGA1UdEwQFMAMCAf8wCwYDVR0PBAQDAgEGMAUG\n"
//			+ "AytlcANBAM4sIMHRi6S+mcqxEHM5uCaRjini+cOGAwTKKRhTq4zbv3RMz6tItLgq\n"
//			+ "sy0UXhHVpR/ZVMT9w3ORm8vfbCmWewk=\n" //
//			+ "-----END CERTIFICATE-----\n";
//
//	@Test
//	public void testPrivateKeyRealmMismatch() throws IuAuthenticationException {
//		final var id = privateKeyPrincipal(SELF_SIGNED_PK + SELF_SIGNED_EE);
//		final var pki = PkiFactory.from(id);
//		final var pk2 = PkiFactory.from(privateKeyPrincipal(PK2 + PK2_CERT));
//		assertThrows(IllegalArgumentException.class, () -> IuPrincipalIdentity.verify(pk2, pki.getName()));
//	}
//
//	@Test
//	public void testPrivateKeyMismatch() throws IuAuthenticationException {
//		final var id = privateKeyPrincipal(PK2 + PK2_CERT);
//		final var pki = (PkiPrincipal) PkiFactory.from(id);
//		final var pk2 = (PkiVerifier) PkiFactory
//				.trust((PkiPrincipal) PkiFactory.from(privateKeyPrincipal(PK3 + PK3_CERT)));
//
//		assertThrows(IllegalArgumentException.class, () -> pk2.verify(pki));
//	}

}
