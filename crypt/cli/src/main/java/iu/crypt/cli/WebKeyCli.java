package iu.crypt.cli;

import java.io.PrintStream;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import edu.iu.IdGenerator;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.client.IuJson;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.X500Utils;
import jakarta.json.stream.JsonGenerator;

/**
 * CLI tool for generating and manipulating {@link WebKey}s.
 */
public class WebKeyCli {

	private WebKeyCli() {
	}

	/**
	 * CLI Usage Document, printed before stack trace if an error occurs.
	 */
	static final String USAGE;

	static {
		final var usage = new StringBuilder("""

				Usage jwk [create|print]

				  Inspect a JSON Web Key
				    jwk print < (jwk) : Validate and print JWK as human-readable
				    jwk export [serial] < (jwk) : Export an X.509 certificate from the JWK
				      - serial: serial number to match; export the full cert chain if omitted

				  Create a JSON Web Key
				    jwk create [type] [alg] [size] [kid] : Create new JWK
				      - type: key type, required if alg is not provided
				      - alg: algorithm, required if type is not provided
				      - size: key size for RSA

				    """);
		usage.append("  Key Types:");
		for (final var keyType : WebKey.Type.values())
			if (!keyType.equals(WebKey.Type.RAW))
				usage.append(" ").append(keyType);

		usage.append(System.lineSeparator()).append("  Algorithms:");
		for (final var alg : Algorithm.values())
			if (!alg.equals(Algorithm.DIRECT))
				usage.append(" ").append(alg.alg);

		usage.append(System.lineSeparator()).append(System.lineSeparator());
		USAGE = usage.toString();
	}

	private static final HexFormat HEX = HexFormat.of();
	private static final HexFormat HEX_UPPER = HEX.withUpperCase();
	private static final HexFormat HEX_OSSL = HexFormat.ofDelimiter(":");

	/**
	 * Parses a serial number from hex format consistent with OpenSSL.
	 * 
	 * @param serialNumber hex-encoded serial number
	 * @return parsed serial number, should match
	 *         {@link X509Certificate#getSerialNumber()}
	 */
	static BigInteger parseSerial(String serialNumber) {
		try {
			return new BigInteger(HEX_OSSL.parseHex(serialNumber));
		} catch (Throwable e) {
			try {
				return new BigInteger(HEX_UPPER.parseHex(serialNumber));
			} catch (Throwable e2) {
				e.addSuppressed(e2);
				try {
					return new BigInteger(Base64.getUrlDecoder().decode(serialNumber));
				} catch (Throwable e3) {
					e.addSuppressed(e3);
					throw new IllegalArgumentException("Invalid serial number", e);
				}
			}
		}
	}

	/**
	 * Formats a serial number as unpadded Base64 URL encoded.
	 * 
	 * @param serialNumber from {@link X509Certificate#getSerialNumber()}
	 * @return hex-encoded serial number
	 */
	static String formatSerial(BigInteger serialNumber) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(serialNumber.toByteArray());
	}

	/**
	 * Formats X509 PKI extensions for a certificate
	 * 
	 * @param basicConstraints basic constraints value
	 * @param keyUsage         key usage statement
	 * @return formatted description
	 */
	static String formatPki(int basicConstraints, boolean[] keyUsage) {
		final var sb = new StringBuilder();
		if (basicConstraints == -1)
			sb.append("EE");
		else
			sb.append("CA pathLen=").append(basicConstraints);

		if (keyUsage[0])
			sb.append(",digitalSignature");
		if (keyUsage[1])
			sb.append(",nonRepudiation");
		if (keyUsage[2])
			sb.append(",keyEncipherment");
		if (keyUsage[3])
			sb.append(",dataEncipherment");
		if (keyUsage[4])
			sb.append(",keyAgreement");
		if (keyUsage[5])
			sb.append(",keyCertSign");
		if (keyUsage[6])
			sb.append(",cRLSign");
		if (keyUsage[7])
			sb.append(",encipherOnly");
		if (keyUsage[8])
			sb.append(",decipherOnly");

		return sb.toString();
	}

	/**
	 * Prints human readable key metadata.
	 * 
	 * @param out stream to print on
	 * @param jwk key to print
	 */
	static void print(PrintStream out, WebKey jwk) {
		final var kid = jwk.getKeyId();
		final var type = jwk.getType();
		final var alg = jwk.getAlgorithm();
		final var privateKey = jwk.getPrivateKey();
		final var publicKey = jwk.getPublicKey();
		final var secretKey = jwk.getKey();

		out.print("JWK ");
		if (secretKey != null)
			out.print("Secret");
		else if (privateKey != null)
			out.print("Private");
		else
			out.print("Public");

		out.println(" Key");
		out.println("-----------------------------");

		if (kid != null)
			out.println("ID:      " + jwk.getKeyId());
		out.print("Type:    " + type.kty);
		if (type.crv != null)
			out.println(" " + type.crv);
		else if (publicKey instanceof RSAPublicKey)
			out.println(" " + ((RSAPublicKey) publicKey).getModulus().bitLength() + "-bit");
		else
			out.println(" " + (secretKey.length * 8) + "-bit");

		if (alg != null)
			System.out.println("Algorithm: " + alg.alg);

		out.println();

		final var certificateChain = jwk.getCertificateChain();
		if (certificateChain != null) {
			out.println("X.509 Certificate Chain");
			out.println("=======================");
			for (var i = 0; i < certificateChain.length; i++) {
				final var cert = certificateChain[i];
				out.println(" " + (i + 1) + ") " //
						+ cert.getNotAfter() + " " //
						+ formatSerial(cert.getSerialNumber()) //
						+ " " + X500Utils.getCommonName(cert.getSubjectX500Principal()) + " "
						+ formatPki(cert.getBasicConstraints(), cert.getKeyUsage()));

//				if (i == certificateChain.length - 1) {
//					if (!cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal()))
//						out.println(
//								"Root Certificate Issued by " + X500Utils.getCommonName(cert.getIssuerX500Principal()));
//					else if (i == 0)
						out.println("    ** Self-Signed Certificate **");
//				} else if (i > 0) {
//					final var last = certificateChain[i - 1];
//					if (!last.getIssuerX500Principal().equals(cert.getSubjectX500Principal()))
//						out.println("    ** Broken X.509 Certificate Chain, issued by "
//								+ X500Utils.getCommonName(last.getIssuerX500Principal()) + " **");
//				}
			}
			out.println();
		}

//		if (crl != null) {
//			out.println("CRL Issuer: " + crl.getIssuerX500Principal().getName());
//			out.println("Update Due: " + crl.getNextUpdate());
//
//			final var entries = crl.getRevokedCertificates();
//			if (entries == null)
//				out.println("** no certificates revoked **");
//			else
//				for (final var entry : entries)
//					out.println("  " + formatSerial(entry.getSerialNumber()) + " " + entry.getRevocationDate());
//
//			out.println();
//		}
	}

	/**
	 * Generates a new key by arguments
	 * 
	 * @param arg arguments
	 * @return generated key
	 */
	static WebKey create(String[] arg) {
		var i = 1;

		if (arg.length < 2)
			throw new IllegalArgumentException("missing key type");

		final var type = arg[i++];
		WebKey.Builder<?> builder;
		Algorithm alg = null;
		try {
			builder = WebKey.builder(WebKey.Type.valueOf(type));
		} catch (IllegalArgumentException e) {
			try {
				builder = WebKey.builder(alg = Algorithm.from(type));
			} catch (Throwable e2) {
				e.addSuppressed(e2);
				throw e;
			}
		}

		String nextArg = null;
		int size = 0;
		if (i < arg.length) {
			nextArg = arg[i++];
			if (alg == null)
				try {
					alg = Algorithm.from(nextArg);
					builder.algorithm(alg);
					if (i < arg.length)
						nextArg = arg[i++];
					else
						nextArg = null;
				} catch (NoSuchElementException e) {
				}

			if (nextArg != null)
				try {
					size = Integer.parseInt(nextArg);
					if (i < arg.length)
						nextArg = arg[i++];
					else
						nextArg = null;
				} catch (NumberFormatException e) {
				}
		}

		if (nextArg != null)
			builder.keyId(nextArg);
		else
			builder.keyId(IdGenerator.generateId());

		if (size > 0)
			return builder.ephemeral(size).build();
		else
			return builder.ephemeral().build();
	}

	/**
	 * Exports an PEM-encpded X509 certificate chain from a JWK.
	 * 
	 * @param jwk    JWK to export the certificate from
	 * @param serial serial number of certificate to export; null to export the full
	 *               certificate chain.
	 */
	static void export(WebKey jwk, BigInteger serial) {
		final var certificateChain = jwk.getCertificateChain();
		if (certificateChain == null)
			throw new IllegalArgumentException("Missing certificate chain");

		try (final var ps = new PrintStream(System.out)) {
			if (serial != null) {
				var found = false;
				for (var i = 0; i < certificateChain.length; i++)
					if (certificateChain[i].getSerialNumber().equals(serial)) {
						IuProcess.pem(ps, certificateChain[i]);
						found = true;
						break;
					}

				if (!found)
					throw new IllegalArgumentException("Invalid serial number");
			} else
				for (final var cert : certificateChain)
					IuProcess.pem(ps, cert);
		}
	}

	/**
	 * Gets parameters for the X509 keyUsage PKI extension.
	 * 
	 * @param jwk key to inspect
	 * @return keyUsage parameters
	 */
	@SuppressWarnings("deprecation")
	static String keyUsage(WebKey jwk) {
		final var alg = jwk.getAlgorithm();
		if (alg != null)
			switch (alg) {
			case ECDH_ES:
			case ECDH_ES_A128KW:
			case ECDH_ES_A192KW:
			case ECDH_ES_A256KW:
				return "keyAgreement";

			case EDDSA:
			case ES256:
			case ES384:
			case ES512:
			case PS256:
			case PS384:
			case PS512:
			case RS256:
			case RS384:
			case RS512:
				return "digitalSignature";

			case RSA1_5:
			case RSA_OAEP:
			case RSA_OAEP_256:
				return "keyEncipherment";

			default:
				throw new IllegalArgumentException("Invalid key algorithm for PKI " + alg);
			}

		final var keyType = jwk.getType();
		final var use = jwk.getUse();
		switch (keyType) {
		case EC_P256:
		case EC_P384:
		case EC_P521:
			if (Use.SIGN.equals(use))
				return "digitalSignature";
			else if (Use.ENCRYPT.equals(use))
				return "keyAgreement";
			else
				return "digitalSignature,keyAgreement";

		case RSASSA_PSS:
		case ED25519:
		case ED448:
			if (Use.ENCRYPT.equals(use))
				throw new IllegalArgumentException("Invalid key use for " + keyType);
			else
				return "digitalSignature";

		case X25519:
		case X448:
			if (Use.SIGN.equals(use))
				throw new IllegalArgumentException("Invalid key use for " + keyType);
			else
				return "keyAgreement";

		case RSA:
			if (Use.SIGN.equals(use))
				return "digitalSignature";
			else if (Use.ENCRYPT.equals(use))
				return "keyEncipherment";
			else
				return "digitalSignature,keyEncipherment";

		default:
			throw new IllegalArgumentException("Invalid key type for PKI " + keyType);
		}
	}

	/**
	 * Reads the X509 subject organization from environment IU_CRYPT_CLI_PKI_ORG or
	 * System property iu.crypt.cli.pki.org.
	 * 
	 * @return X509 subject organization
	 */
	static String subjectOrg() {
		final var subjectOrg = IuRuntimeEnvironment.envOptional("iu.crypt.cli.pki.org");
		if (subjectOrg == null)
			return "";
		IuObject.require(subjectOrg,
				a -> a.matches("(/[\\x20-\\x7e]+=[\\x20-\\x7e]+(\\+[\\x20-\\x7e]+=[\\x20-\\x7e]+)*)+"),
				"Invalid subject RDN in iu.crypt.cli.pki.org");
		return subjectOrg;
	}

	/**
	 * Adds a self-signed X509 certificate to the JWK.
	 * 
	 * <p>
	 * Use environment variable IU_CRYPT_CLI_PKI_DAYS or system property
	 * iu.crypt.cli.pki days to set the number of days the certificate will be valid
	 * for. Default if not set is 120 days.
	 * </p>
	 * 
	 * @param jwk JWK
	 * @return updated JWK
	 */
	static WebKey self(WebKey jwk) {
		final var days = Objects.requireNonNullElse( //
				IuRuntimeEnvironment.envOptional("iu.crypt.cli.pki.days"), //
				"120");

		final var keyType = jwk.getType();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(IuProcess::pem, privateKey);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", days, //
				"-subj", subjectOrg() + "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + keyUsage(jwk) //
		);

		IuProcess.deleteTempFiles();

		return WebKey.builder(keyType) //
				.keyId(jwk.getKeyId()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) //
				.algorithm(jwk.getAlgorithm()) //
				.use(jwk.getUse()) //
				.pem(pemCert) //
				.build();
	}

	/**
	 * Main entry point.
	 * 
	 * @param arg arguments
	 */
	public static void main(String[] arg) {
		if (arg.length == 0) {
			System.err.print(USAGE);
			return;
		}

		try {
			final var cmd = arg[0];
			final WebKey key;
			if (cmd.equals("print")) {
				print(System.out, WebKey.parse(IuProcess.read()));
				return;
			} else if (cmd.equals("export")) {
				export(WebKey.parse(IuProcess.read()), arg.length > 1 ? parseSerial(arg[1]) : null);
				return;
			} else if (cmd.equals("create"))
				key = create(arg);
			else if (cmd.equals("self"))
				key = self(WebKey.parse(IuProcess.read()));
			else
				throw new IllegalArgumentException("invalid command " + cmd);

			final var json = IuJson.parse(key.toString());
			IuJson.PROVIDER.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(System.out)
					.write(json);
			System.out.println();

		} catch (RuntimeException | Error e) {
			System.err.print(USAGE);
			throw e;
		}
	}

}
