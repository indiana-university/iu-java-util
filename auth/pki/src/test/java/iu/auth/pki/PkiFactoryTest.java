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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import edu.iu.IuException;
import edu.iu.auth.IuPrincipalIdentity;
import edu.iu.auth.config.AuthConfig;
import edu.iu.auth.config.IuAuthConfig;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;

@SuppressWarnings("javadoc")
public class PkiFactoryTest {

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl genpkey -algorithm ed448 | tee /tmp/k
	 * </pre>
	 */
	private static final String SELF_SIGNED_PK = "-----BEGIN PRIVATE KEY-----\n" //
			+ "MEcCAQAwBQYDK2VxBDsEOTJhHRjuRVDowCMKWslwironn8lKFWPw5ShatWk8vjgB\n" //
			+ "C4xaM8unbSd02KYIjhisyRIyQX++Ph2QOA==\n" //
			+ "-----END PRIVATE KEY-----\n";

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl req -x509 -key /tmp/k -addext basicConstraints=pathlen:-1 \
	-addext keyUsage=keyCertSign,cRLSign -days 821
	 * </pre>
	 */
	private static final String SELF_SIGNED_EE = "-----BEGIN CERTIFICATE-----\r\n" //
			+ "MIICmDCCAhigAwIBAgIULhCA4AFQAEpGt5LgLFk8XWjbevgwBQYDK2VxMIGVMQsw\r\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\r\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\r\n" //
			+ "MTAwLgYDVQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRl\r\n" //
			+ "c3QwIBcNMjQwNDE0MjMxMjM5WhgPMjEyNDA0MTUyMzEyMzlaMIGVMQswCQYDVQQG\r\n" //
			+ "EwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24xGzAZ\r\n" //
			+ "BgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTAwLgYD\r\n" //
			+ "VQQDDCd1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3QwQzAF\r\n" //
			+ "BgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV1LHP199m\r\n" //
			+ "Tt06kwbJsAdaIrpwEoAzQ4CjXTBbMB0GA1UdDgQWBBRqDb4aD1ROQ5uPS/LG7njv\r\n" //
			+ "Eu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DAMBgNVHRMEBTAD\r\n" //
			+ "AgH/MAsGA1UdDwQEAwIHgDAFBgMrZXEDcwAp8GWXdQB9zGsXyFalPKXdDzxqccGY\r\n" //
			+ "UHigHjvIXAfDzoypRFJXmTBiotbJ719wbBsSijAaiXkMqoCUwY7PUTv9mtfTeXQg\r\n" //
			+ "0gXa+OUW+5/C0tFm4khOPNe50GFwrnlAdV+UCFSU3+ZHluXWSrVVWbHRJwA=\r\n" //
			+ "-----END CERTIFICATE-----\r\n";

	/**
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 */
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

	/**
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 */
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

	/**
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 */
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

	/**
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 */
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
			+ "YEP1yOMcXcVyP3/6geBTGNuWBELol2TPdNvRTrq96IKMUHwvZ78OCQA=\n" + "-----END CERTIFICATE-----";

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

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl req -x509 -key /tmp/k -addext basicConstraints=critical,CA:true,pathlen:0 \
	 -addext keyUsage=keyCertSign,cRLSign -days 821 | tee /tmp/ca
	 * </pre>
	 */
	private static final String CA_ROOT = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICpDCCAiSgAwIBAgIUTA+Skb7j/4Km5D/xt7kssBM/Kk8wBQYDK2VxMIGYMQsw\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
			+ "MTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRl\n" //
			+ "c3RfQ0EwIBcNMjQwNDE2MDk0ODM2WhgPMjEyNDA0MTcwOTQ4MzZaMIGYMQswCQYD\n" //
			+ "VQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24x\n" //
			+ "GzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMw\n" //
			+ "MQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3Rf\n" //
			+ "Q0EwQzAFBgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV\n" //
			+ "1LHP199mTt06kwbJsAdaIrpwEoAzQ4CjYzBhMB0GA1UdDgQWBBRqDb4aD1ROQ5uP\n" //
			+ "S/LG7njvEu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DASBgNV\n" //
			+ "HRMBAf8ECDAGAQH/AgEAMAsGA1UdDwQEAwIBBjAFBgMrZXEDcwAxIP6HDFL5cxNO\n" //
			+ "PqH0L1Vkk6xbqjmK1hGr79W6OCvfjlcaKhvC4ivnQzxJQV6CHCfGVlkix3m084Ce\n" //
			+ "p6NSHLht5UOW+CeNzF4B8I6y3EJjxzUc/PvLy4Q5VRwJ64Aol1lttLgmIyr1Ww2w\n" //
			+ "UvHVFEgfEQA=\n" //
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

	/**
	 * <p>
	 * For verification and demonstration purposes only. NOT FOR PRODUCTION USE.
	 * 
	 * <pre>
	$ openssl req -new -out /tmp/req -key /tmp/k
	$ openssl req -x509 -in /tmp/req -addext basicConstraints=CA:false \
		-addext keyUsage=digitalSignature,keyAgreement -CA /tmp/ca -CAkey /tmp/k -days
	 * </pre>
	 */
	private static final String CA_SIGNED = "-----BEGIN CERTIFICATE-----\n" //
			+ "MIICmzCCAhugAwIBAgIUPYscr3NNwWMvs+DxyNZrN472cvUwBQYDK2VxMIGYMQsw\n" //
			+ "CQYDVQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0\n" //
			+ "b24xGzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNI\n" //
			+ "MTMwMQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRl\n" //
			+ "c3RfQ0EwIBcNMjQwNDE2MTAwNDA0WhgPMjEyNDA0MTcxMDA0MDRaMIGYMQswCQYD\n" //
			+ "VQQGEwJVUzEQMA4GA1UECAwHSW5kaWFuYTEUMBIGA1UEBwwLQmxvb21pbmd0b24x\n" //
			+ "GzAZBgNVBAoMEkluZGlhbmEgVW5pdmVyc2l0eTEPMA0GA1UECwwGU1RBUkNIMTMw\n" //
			+ "MQYDVQQDDCp1cm46ZXhhbXBsZTppdS1qYXZhLWF1dGgtcGtpI1BraVNwaVRlc3Rf\n" //
			+ "RUUwQzAFBgMrZXEDOgBCIEbu84CEkUNQzWVKhH2TVetBFR+4a7rT4XFXAq6WGigV\n" //
			+ "1LHP199mTt06kwbJsAdaIrpwEoAzQ4CjWjBYMB0GA1UdDgQWBBRqDb4aD1ROQ5uP\n" //
			+ "S/LG7njvEu/f+DAfBgNVHSMEGDAWgBRqDb4aD1ROQ5uPS/LG7njvEu/f+DAJBgNV\n" //
			+ "HRMEAjAAMAsGA1UdDwQEAwIDiDAFBgMrZXEDcwBX2foR72+bPqirhp/XsG8piwC8\n" //
			+ "HR3PGzh+tOXoLnjVuRARtb6OdO9Mz8NdjMKwseA+xB+YYl0DsIDVqq5IdtEIfgz9\n" //
			+ "Y98CSqcpVOI9Wpmp9bpnrX0+fvlXVw4SWCklCl7FOTSuVeMlmIoyP9otyvaMFQA=\n" //
			+ "-----END CERTIFICATE-----\n" + "";

	private MockedStatic<AuthConfig> mockAuthConfig;
	private Map<String, IuAuthConfig> configs;
	private boolean sealed;

	@BeforeEach
	public void setup() {
		mockAuthConfig = mockStatic(AuthConfig.class);
		configs = new HashMap<>();
		mockAuthConfig.when(() -> AuthConfig.register(any())).then(a -> {
			assertFalse(sealed);

			final IuAuthConfig c = a.getArgument(0);
			configs.put(c.getRealm(), c);
			return null;
		});
		mockAuthConfig.when(() -> AuthConfig.seal()).then(a -> {
			sealed = true;
			return null;
		});
		mockAuthConfig.when(() -> AuthConfig.get(any())).thenAnswer(a -> {
			assertTrue(sealed);
			return Objects.requireNonNull(configs.get(a.getArguments()[0]));
		});
	}

	@AfterEach
	public void teardown() {
		mockAuthConfig.close();
		sealed = false;
		mockAuthConfig = null;
		configs = null;
	}

	@Test
	public void testInvalidPkiPrincipal() {
		assertEquals("Missing X.509 certificate chain", assertThrows(NullPointerException.class,
				() -> PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK).build())).getMessage());
	}

	@Test
	public void testSelfSignedEE() throws Exception {
		final var pki = PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + SELF_SIGNED_EE).build());
		final var verifier = PkiFactory.trust(pki);
		assertNull(verifier.getAuthScheme());
		assertNull(verifier.getAuthenticationEndpoint());
		assertSame(PkiPrincipal.class, verifier.getType());
		AuthConfig.register(verifier);
		AuthConfig.seal();

		IuPrincipalIdentity.verify(pki, pki.getName());

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();
		final var publicId = PkiFactory.from(wellKnown);
		assertEquals("missing private key",
				assertThrows(IllegalArgumentException.class, () -> IuPrincipalIdentity.verify(publicId, pki.getName()))
						.getMessage());

		final var cert = wellKnown.getCertificateChain()[0];
		assertEquals(pki.getName(), X500Utils.getCommonName(cert.getSubjectX500Principal()));

		assertNotNull(sub.getPrivateCredentials(WebKey.class).iterator().next().getPrivateKey());
		assertSerializable(pki);
	}

	@Test
	public void testSelfSignedEECertOnly() throws Exception {
		final var pki = PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_EE).build());
		final var verifier = PkiFactory.trust(pki);
		AuthConfig.register(verifier);
		AuthConfig.seal();

		assertFalse(IuPrincipalIdentity.verify(pki, pki.getName()));

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();
		final var publicId = PkiFactory.from(wellKnown);
		IuPrincipalIdentity.verify(publicId, pki.getName());

		final var cert = wellKnown.getCertificateChain()[0];
		assertEquals(pki.getName(), X500Utils.getCommonName(cert.getSubjectX500Principal()));

		assertTrue(sub.getPrivateCredentials(WebKey.class).isEmpty());
		assertSerializable(pki);
	}

	@Test
	public void testSelfSignedEEForEncrypt() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + DATAENC_EE).build()));
		assertThrows(IllegalArgumentException.class,
				() -> PkiFactory.from(WebKey.builder(Type.ED448).pem(DATAENC_EE).build()));

		final var pki = PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + KEYENC_EE).build());
		AuthConfig.register(PkiFactory.trust(pki));
		AuthConfig.seal();

		IuPrincipalIdentity.verify(pki, pki.getName());
		final var key = pki.getSubject().getPrivateCredentials(WebKey.class).iterator().next();
		assertEquals(Use.ENCRYPT, key.getUse());
		assertEquals(Set.of(Operation.WRAP, Operation.UNWRAP), key.getOps());

		final var pubkey = PkiFactory.from(WebKey.builder(Type.ED448).pem(KEYENC_EE).build()).getSubject()
				.getPublicCredentials(WebKey.class).iterator().next();
		assertEquals(Use.ENCRYPT, pubkey.getUse());
		assertEquals(Set.of(Operation.WRAP), pubkey.getOps());
	}

	@Test
	public void testSelfSignedEEForKeyAgreement() throws Exception {
		final var pki = PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + KEYAGREE_EE).build());
		AuthConfig.register(PkiFactory.trust(pki));
		AuthConfig.seal();

		IuPrincipalIdentity.verify(pki, pki.getName());
		final var key = pki.getSubject().getPrivateCredentials(WebKey.class).iterator().next();
		assertEquals(Use.ENCRYPT, key.getUse());
		assertEquals(Set.of(Operation.DERIVE_KEY), key.getOps());
		PkiFactory.from(key.wellKnown()); // from full params
	}

	@Test
	public void testExpiredEE() throws Exception {
		final var pki = PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + EXPIRED_EE).build());
		AuthConfig.register(PkiFactory.trust(pki));
		AuthConfig.seal();

		assertInstanceOf(CertificateExpiredException.class,
				assertInstanceOf(CertPathValidatorException.class, assertThrows(IllegalStateException.class,
						() -> IuPrincipalIdentity.verify(
								PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + EXPIRED_EE).build()),
								pki.getName()))
						.getCause()).getCause());
	}

	@Test
	public void testNoFragmentEE() throws Exception {
		final var pki = PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + NOFRAGMENT_EE).build());
		AuthConfig.register(PkiFactory.trust(pki));
		AuthConfig.seal();

		IuPrincipalIdentity.verify(pki, pki.getName());

		final var sub = pki.getSubject();
		assertEquals(Set.of(pki), sub.getPrincipals());

		final var pub = sub.getPublicCredentials();
		assertEquals(1, pub.size());
		final var wellKnown = (WebKey) pub.iterator().next();
		assertEquals(pki.getName(), wellKnown.getKeyId());
	}

	@Test
	public void testPublicCA() throws Exception {
		final CertPath iuEdu;
		final X509Certificate inCommon;
		final CertStore crl;
		try {
			final var http = HttpClient.newHttpClient();
			final var resp = http.send(HttpRequest.newBuilder(URI.create("https://www.iu.edu/index.html"))
					.method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.discarding());

			final var x509 = CertificateFactory.getInstance("X.509");
			iuEdu = x509.generateCertPath(List.of(resp.sslSession().get().getPeerCertificates()));
			inCommon = (X509Certificate) x509.generateCertificate(http.send(
					HttpRequest.newBuilder(URI.create("http://crt.usertrust.com/USERTrustRSAAddTrustCA.crt")).build(),
					BodyHandlers.ofInputStream()).body());

			crl = CertStore.getInstance("Collection", new CollectionCertStoreParameters(Set.of(
					x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.incommon-rsa.org/InCommonRSAServerCA.crl")).build(),
							BodyHandlers.ofInputStream()).body()),
					x509.generateCRL(http.send(HttpRequest
							.newBuilder(URI.create("http://crl.usertrust.com/USERTrustRSACertificationAuthority.crl"))
							.build(), BodyHandlers.ofInputStream()).body()))));
		} catch (Throwable e) {
			e.printStackTrace();
			Assumptions.abort("unable to read public key data for verifying iu.edu " + e);
			return;
		}

		final var anchor = new TrustAnchor(inCommon, null);
		final var pkix = new PKIXParameters(Set.of(anchor));
		pkix.addCertStore(crl);
		final var verifier = PkiFactory.trust(null, pkix);
		assertThrows(IllegalArgumentException.class,
				() -> PkiFactory.trust(PemEncoded.parse(SELF_SIGNED_PK).next().asPrivate("ED448"), pkix));
		AuthConfig.register(verifier);
		AuthConfig.seal();

		final var iuEduPem = new StringBuilder();
		PemEncoded.serialize(iuEdu.getCertificates().toArray(X509Certificate[]::new))
				.forEachRemaining(iuEduPem::append);
		final var iuEduId = PkiFactory.from(WebKey.builder(Type.RSA).pem(iuEduPem.toString()).build());
		assertEquals("iu.edu", iuEduId.getName());

		IuPrincipalIdentity.verify(iuEduId, "USERTrust RSA Certification Authority");

		assertThrows(IllegalArgumentException.class,
				() -> IuPrincipalIdentity.verify(
						PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + KEYAGREE_EE).build()),
						"USERTrust RSA Certification Authority"));

		assertSerializable(iuEduId);
	}

	@Test
	public void testPrivateCA() throws Exception {
		assertEquals("ID certificate must be an end-entity",
				assertThrows(IllegalArgumentException.class,
						() -> PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + CA_ROOT).build()))
						.getMessage());

		final var pki = PkiFactory.from(WebKey.builder(Type.ED448).pem(SELF_SIGNED_PK + CA_SIGNED).build());
		final var caCert = PemEncoded.parse(CA_ROOT).next().asCertificate();
		final var anchor = new TrustAnchor(caCert, null);
		final var pkix = new PKIXParameters(Set.of(anchor));
		pkix.addCertStore(CertStore.getInstance("Collection",
				new CollectionCertStoreParameters(Set.of(PemEncoded.parse(CA_ROOT_CRL).next().asCRL()))));

		final var verifier = PkiFactory.trust(null, pkix);
		AuthConfig.register(verifier);
		AuthConfig.seal();

		assertSerializable(pki);
		IuPrincipalIdentity.verify(pki, X500Utils.getCommonName(caCert.getSubjectX500Principal()));
	}

	private void assertSerializable(IuPrincipalIdentity pki) {
		final var auth = !pki.getSubject().getPrivateCredentials().isEmpty();
		final var pkis = pki.toString();
		if (auth)
			assertTrue(pkis.startsWith("Authoritative "), pkis);
		else
			assertTrue(pkis.startsWith("Well-Known "), pkis);

		final var serialCopy = IuException.unchecked(() -> {
			final var out = new ByteArrayOutputStream();
			try (final var o = new ObjectOutputStream(out)) {
				o.writeObject(pki);
			}
			try (final var o = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()))) {
				return (IuPrincipalIdentity) o.readObject();
			}
		});

		final var wks = serialCopy.toString();
		assertTrue(wks.startsWith("Well-Known "), wks);
		if (auth)
			assertEquals(pkis.substring(14), wks.substring(11));
		else
			assertEquals(pkis, wks);

		final var jwk = PkiFactory.from(pki.getSubject().getPublicCredentials(WebKey.class).iterator().next());
		assertEquals(wks, jwk.toString());
	}

}
