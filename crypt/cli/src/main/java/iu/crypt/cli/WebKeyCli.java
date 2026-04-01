package iu.crypt.cli;

import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.logging.Logger;

import edu.iu.IdGenerator;
import edu.iu.IuException;
import edu.iu.IuIterable;
import edu.iu.IuObject;
import edu.iu.IuRuntimeEnvironment;
import edu.iu.IuText;
import edu.iu.client.IuJson;
import edu.iu.crypt.PemEncoded;
import edu.iu.crypt.WebKey;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Use;
import edu.iu.crypt.X500Utils;
import edu.iu.crypt.X509CertificateAuthority;
import iu.crypt.CryptJsonAdapters;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;

/**
 * CLI tool for generating and manipulating {@link WebKey}s.
 */
public class WebKeyCli {

	private static final Logger LOG = Logger.getLogger(WebKeyCli.class.getName());

	private WebKeyCli() {
	}

	/**
	 * CLI Usage Document, printed before stack trace if an error occurs.
	 */
	static final String USAGE;

	static {
		final var usage = new StringBuilder("""

				Usage jwk [ca|cert|create|export|import|print|public|req|revoke|self|sign]

				  Create a JSON Web Key
				    jwk create [type] [alg] [size] [kid] : Create new JWK
				      - type: key type, required if alg is not provided
				      - alg: algorithm, required if type is not provided
				      - size: key size for RSA
				    jwk import < (pem) : Create a JWK from a PEM-encoded X.509 certificate
				                         and optional private key

				  Inspect a JSON Web Key
				    jwk print < (jwk|ca)  : Validate and print JWK or CA as human-readable
				    jwk public < (jwk|ca) : Strip private key information from a JWK or CA

				  X509 Certificate Operations
				    jwk self < (jwk)               : Self-sign a JWK as an end entity
				    jwk ca < (jwk)                 : Self-sign a JWK as a new private CA
				    jwk req < (jwk)                : Generate a new CSR from a JWK
				    jwk sign (csr) < (ca)          : Self-sign a JWK as a new private CA
				      - csr: file containing a certificate request (i.e., output from jwk req)
				    jwk cert (cert) < (jwk)        : Replace the X.509 certiticate chain
					jwk export [serial] < (ca) : Export an X.509 certificate
				      - serial: serial number to match; export the full cert chain if omitted
				    jwk revoke (serial) < (ca)     : Self-sign a JWK as a new private CA

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
	 * Formats a serial number in hex format consistent with OpenSSL.
	 * 
	 * @param serialNumber from {@link X509Certificate#getSerialNumber()}
	 * @return hex-encoded serial number
	 */
	static String formatSerialOSSL(BigInteger serialNumber) {
		return HEX_UPPER.formatHex(serialNumber.toByteArray());
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
			}
			out.println();
		}
	}

	/**
	 * Prints human readable CA metadata.
	 * 
	 * @param out stream to print on
	 * @param ca  CA to print
	 */
	static void print(PrintStream out, X509CertificateAuthority ca) {
		out.println("X.509 Certificate Authority");
		out.println();
		print(out, ca.getJwk());

		final var database = ca.getDatabase();
		if (database != null) {
			out.println("Database: " + database.length + " bytes");
			out.println();
		}

		final var issuedCerts = ca.getCertificates();
		if (issuedCerts != null) {
			final var issuedCertIterator = issuedCerts.iterator();
			if (issuedCertIterator.hasNext()) {
				out.println("Issued Certificates:");
				while (issuedCertIterator.hasNext()) {
					final var issuedCert = issuedCertIterator.next();
					out.println("  " //
							+ issuedCert.getNotAfter() + " " //
							+ formatSerial(issuedCert.getSerialNumber()) //
							+ " " + X500Utils.getCommonName(issuedCert.getSubjectX500Principal()) + " "
							+ formatPki(issuedCert.getBasicConstraints(), issuedCert.getKeyUsage()));
				}
				out.println();
			}
		}

		final var crls = ca.getCrl();
		for (final var crl : crls) {
			out.println("CRL Issuer: " + crl.getIssuerX500Principal().getName());
			out.println("Update Due: " + crl.getNextUpdate());

			final var entries = crl.getRevokedCertificates();
			if (entries == null)
				out.println("** no certificates revoked **");
			else
				for (final var entry : entries)
					out.println("  " + formatSerial(entry.getSerialNumber()) + " " + entry.getRevocationDate());

			out.println();
		}
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
	 * Exports an PEM-encpded X509 certificate chain for a CA-signed cert.
	 * 
	 * @param out    stream to print the certificate chain on
	 * @param ca     CA to export the certificate from
	 * @param serial serial number of certificate to export; null to export the full
	 *               certificate chain.
	 */
	static void export(PrintStream out, X509CertificateAuthority ca, BigInteger serial) {
		var found = false;
		for (final var cert : ca.getCertificates())
			if (cert.getSerialNumber().equals(serial)) {
				IuProcess.pem(out, cert);
				found = true;
				break;
			}

		if (!found)
			throw new IllegalArgumentException("Invalid serial number");

		for (final var cert : ca.getJwk().getCertificateChain())
			IuProcess.pem(out, cert);
	}

	/**
	 * Replaces the X509 certificate chain of a JWK.
	 * 
	 * @param jwk                  JWK to update
	 * @param certificateChainFile concatenated PEM-encoded certificate chain file
	 * @return Updated JWK
	 */
	static WebKey cert(WebKey jwk, Path certificateChainFile) {
		final Queue<X509Certificate> newCertificateQueue = new ArrayDeque<>();
		final var pemIter = PemEncoded.parse(IuException.unchecked(() -> Files.readString(certificateChainFile)));
		while (pemIter.hasNext())
			newCertificateQueue.offer(pemIter.next().asCertificate());
		final var newCertificateChain = newCertificateQueue.toArray(X509Certificate[]::new);

		final var keyType = jwk.getType();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");

		return WebKey.builder(keyType) //
				.keyId(IuObject.once(jwk.getKeyId(),
						X500Utils.getCommonName(newCertificateChain[0].getSubjectX500Principal()))) //
				.use(jwk.getUse()) //
				.algorithm(jwk.getAlgorithm()) //
				.key(privateKey) //
				.key(jwk.getPublicKey()) //
				.cert(newCertificateChain) //
				.build();
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
	 * Gets the number of days self- and CA-signed certs are valid.
	 * 
	 * @return Environment variable IU_CRYPT_CLI_PKI_DAYS, system property
	 *         iu.crypt.cli.pki.days, or default of 120.
	 */
	static int days() {
		return Integer.parseInt(Objects.requireNonNullElse( //
				IuRuntimeEnvironment.envOptional("iu.crypt.cli.pki.days"), //
				"120"));
	}

	/**
	 * Gets the number of days a CA signing certificate will be valid.
	 * 
	 * @return Environment variable IU_CRYPT_CLI_PKI_CA_DAYS, system property
	 *         iu.crypt.cli.pki.ca.days, or default of 830.
	 */
	static int caDays() {
		return Integer.parseInt(Objects.requireNonNullElse( //
				IuRuntimeEnvironment.envOptional("iu.crypt.cli.pki.ca.days"), //
				"120"));
	}

	/**
	 * Adds a self-signed X509 certificate to the JWK.
	 * 
	 * @param jwk JWK
	 * @return updated JWK
	 */
	static WebKey self(WebKey jwk) {
		final var keyType = jwk.getType();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(IuProcess::pem, privateKey);
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", Integer.toString(days()), //
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
	 * Creates a new private CA.
	 * 
	 * @param pwk CA signing key
	 * @return {@link X509CertificateAuthority}
	 */
	static X509CertificateAuthority ca(WebKey pwk) {
		final var commonName = pwk.getKeyId();
		final var privateKeyFile = IuProcess.temp(IuProcess::pem, pwk.getPrivateKey());
		final var pemCert = IuProcess.exec( //
				"openssl", "req", "-x509", "-key", privateKeyFile.toString(), "-days", Integer.toString(caDays()), //
				"-subj", subjectOrg() + "/CN=" + commonName.replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=critical,CA:true,pathlen:0", //
				"-addext", "keyUsage=keyCertSign,cRLSign" //
		);

		final var jwk = WebKey.builder(pwk.getType()) //
				.keyId(commonName) //
				.key(pwk.getPrivateKey()) //
				.key(pwk.getPublicKey()) //
				.pem(pemCert) //
				.build();

		final var databaseFile = IuProcess.temp(PrintStream::print, "");
		final var newCertsDir = IuProcess.createTempDirectory();
		final var caConfig = caConfig(privateKeyFile, databaseFile, newCertsDir, jwk);
		final var crl = PemEncoded.parse(IuProcess.exec( //
				"openssl", "ca", "-gencrl", "-config", caConfig.toString(), "-crldays", Integer.toString(caDays()) //
		)).next().asCRL();

		final var database = IuException.unchecked(() -> Files.readAllBytes(databaseFile));
		IuProcess.deleteTempFiles();

		return new X509CertificateAuthority() {
			@Override
			public WebKey getJwk() {
				return jwk;
			}

			@Override
			public byte[] getDatabase() {
				return database;
			}

			@Override
			public Iterable<X509Certificate> getCertificates() {
				return IuIterable.empty();
			}

			@Override
			public Iterable<X509CRL> getCrl() {
				return IuIterable.iter(crl);
			}
		};
	}

	private static String replacePropertyValue(String template, String name, Path path) {
		return template.replace("${" + name + "}", path.toString().replace('\\', '/'));
	}

	private static Path caConfig(Path privateKeyFile, Path databaseFile, Path newCertsDir, WebKey jwk) {
		final var certificateFile = IuProcess.temp(IuProcess::pem, jwk.getCertificateChain()[0]);
		IuProcess.exec( //
				"openssl", "x509", "-text", "-in", certificateFile.toString() //
		);
		var caConfigContents = """
				[ ca ]
				default_ca = a

				[ a ]
				private_key = ${private_key}
				certificate = ${certificate}
				database = ${database}
				new_certs_dir = ${new_certs_dir}
				copy_extensions = copyall
				rand_serial = yes
				policy = b

				[ b ]
				countryName = optional
				stateOrProvinceName = optional
				localityName = optional
				organizationName = optional
				organizationalUnitName = optional
				commonName = supplied
				emailAddress = optional
				""";
		caConfigContents = replacePropertyValue(caConfigContents, "private_key", privateKeyFile);
		caConfigContents = replacePropertyValue(caConfigContents, "certificate", certificateFile);
		caConfigContents = replacePropertyValue(caConfigContents, "database", databaseFile);
		caConfigContents = replacePropertyValue(caConfigContents, "new_certs_dir", newCertsDir);
		LOG.fine("OpenSSL CA config\n" + caConfigContents);
		return IuProcess.temp(PrintStream::print, caConfigContents);
	}

	/**
	 * Generates a new certificate request.
	 * 
	 * @param out stream to print the CSR on
	 * @param jwk key to generate a CSR for
	 */
	static void req(PrintStream out, WebKey jwk) {
		final var privateKeyFile = IuProcess.temp(IuProcess::pem, jwk.getPrivateKey());
		final var csr = IuProcess.exec( //
				"openssl", "req", "-new", "-key", privateKeyFile.toString(), //
				"-subj", subjectOrg() + "/CN=" + jwk.getKeyId().replaceAll("([+=/])", "\\\\$1"), //
				"-addext", "basicConstraints=CA:false", //
				"-addext", "keyUsage=" + keyUsage(jwk) //
		);

		IuProcess.deleteTempFiles();

		out.println(csr);
	}

	/**
	 * Returns a copy of a CA with a new signed certificate appended to the valid
	 * certificate store.
	 * 
	 * @param ca      CA
	 * @param csrFile {@link Path} to a valid certificate signing request
	 * @return Updated {@link X509CertificateAuthority}
	 */
	static X509CertificateAuthority sign(X509CertificateAuthority ca, Path csrFile) {
		final var jwk = ca.getJwk();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(IuProcess::pem, privateKey);
		final var databaseFile = IuProcess.createTempFile();
		IuException.unchecked(() -> Files.write(databaseFile, ca.getDatabase()));
		final var newCertsDir = IuProcess.createTempDirectory();
		final var caConfig = caConfig(privateKeyFile, databaseFile, newCertsDir, jwk);

		final var caOut = IuProcess.exec( //
				"openssl", "ca", "-batch", "-in", csrFile.toString(), //
				"-config", caConfig.toString(), "-days", Integer.toString(days()) //
		);

		final var pemCert = PemEncoded.parse(caOut.substring(caOut.indexOf("-----BEGIN"))).next().asCertificate();
		final Queue<X509Certificate> newCertificates = new ArrayDeque<>();
		final var certs = ca.getCertificates();
		certs.forEach(newCertificates::offer);
		newCertificates.offer(pemCert);

		final var crlDays = Long.toString(
				Duration.between(Instant.now(), jwk.getCertificateChain()[0].getNotAfter().toInstant()).toDays());
		final var newCrl = PemEncoded.parse(IuProcess.exec( //
				"openssl", "ca", "-gencrl", "-config", caConfig.toString(), "-crldays", crlDays //
		)).next().asCRL();
		final var database = IuException.unchecked(() -> Files.readAllBytes(databaseFile));

		IuProcess.deleteTempFiles();
		IuException.unchecked(() -> {
			Files.deleteIfExists(Path.of(databaseFile + ".attr"));
			Files.deleteIfExists(Path.of(databaseFile + ".old"));
		});

		return new X509CertificateAuthority() {

			@Override
			public WebKey getJwk() {
				return jwk;
			}

			@Override
			public byte[] getDatabase() {
				return database;
			}

			@Override
			public Iterable<X509Certificate> getCertificates() {
				return newCertificates;
			}

			@Override
			public Iterable<X509CRL> getCrl() {
				return IuIterable.iter(newCrl);
			}
		};
	}

	/**
	 * Returns a copy of a CA with a certificate revoked.
	 * 
	 * @param ca     CA to revoke the certificate from
	 * @param serial serial number of the certificate to revoke
	 * @return Updated CA
	 */
	static X509CertificateAuthority revoke(X509CertificateAuthority ca, BigInteger serial) {
		final var certificates = Objects.requireNonNull(ca.getCertificates(), "Missing certificate chain");

		final Queue<X509Certificate> newCertificates = new ArrayDeque<>();
		final var iter = certificates.iterator();
		final X509Certificate certToRevoke;
		{
			X509Certificate selectedCert = null;
			while (iter.hasNext()) {
				final var cert = iter.next();
				if (serial.equals(cert.getSerialNumber()))
					selectedCert = IuObject.once(selectedCert, cert);
				else
					newCertificates.offer(cert); // retain all other certs
			}
			certToRevoke = Objects.requireNonNull(selectedCert, "Invalid serial number");
		}

		final var newCertsDir = IuProcess.createTempDirectory();
		final var certFileToRevoke = newCertsDir.resolve(WebKeyCli.formatSerialOSSL(serial) + ".pem");
		IuException.unchecked(() -> {
			try (final var out = Files.newOutputStream(certFileToRevoke); //
					final var ps = new PrintStream(out)) {
				IuProcess.pem(ps, certToRevoke);
			}
		});

		final var jwk = ca.getJwk();
		final var privateKey = Objects.requireNonNull(jwk.getPrivateKey(), "Missing private key");
		final var privateKeyFile = IuProcess.temp(IuProcess::pem, privateKey);

		final var databaseFile = IuProcess.createTempFile();
		IuException.unchecked(() -> Files.write(databaseFile, //
				// ensure windows line endings have not corrupted the database
				IuText.utf8(IuText.utf8(ca.getDatabase()).replace("\r\n", "\n")) //
		));

		final var caConfig = caConfig(privateKeyFile, databaseFile, newCertsDir, jwk);
		IuProcess.exec( //
				"openssl", "ca", "-batch", "-revoke", certFileToRevoke.toString(), //
				"-config", caConfig.toString() //
		);

		final var caCert = jwk.getCertificateChain()[0];
		final var crlDays = Long
				.toString(Duration.between(Instant.now(), caCert.getNotAfter().toInstant()).toDays() + 1L);
		final var newCrl = PemEncoded.parse(IuProcess.exec( //
				"openssl", "ca", "-gencrl", "-config", caConfig.toString(), "-crldays", crlDays //
		)).next().asCRL();
		final var database = IuException.unchecked(() -> Files.readAllBytes(databaseFile));

		IuProcess.deleteTempFiles();
		IuException.unchecked(() -> {
			Files.deleteIfExists(Path.of(databaseFile + ".attr"));
			Files.deleteIfExists(Path.of(databaseFile + ".old"));
		});

		return new X509CertificateAuthority() {
			@Override
			public WebKey getJwk() {
				return jwk;
			}

			@Override
			public byte[] getDatabase() {
				return database;
			}

			@Override
			public Iterable<X509CRL> getCrl() {
				return IuIterable.iter(newCrl);
			}

			@Override
			public Iterable<X509Certificate> getCertificates() {
				return newCertificates;
			}
		};
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

			WebKey key = null;
			X509CertificateAuthority ca = null;
			if (cmd.equals("create"))
				key = create(arg);
			else {
				WebKey inputKey = null;
				X509CertificateAuthority inputCa = null;
				final var json = IuJson.parse(IuProcess.read()).asJsonObject();
				if (json.containsKey("kty"))
					inputKey = CryptJsonAdapters.WEBKEY.fromJson(json);
				else
					inputCa = CryptJsonAdapters.CA.fromJson(json);

				if (cmd.equals("ca")) {
					ca = ca(inputKey);
				} else if (cmd.equals("export")) {
					export(System.out, inputCa, parseSerial(arg[1]));
					return;
				} else if (cmd.equals("print")) {
					if (inputKey != null)
						print(System.out, inputKey);
					else
						print(System.out, inputCa);
					return;
				} else if (cmd.equals("public")) {
					if (inputKey != null)
						key = inputKey.wellKnown();
					else {
						final var jwk = inputCa.getJwk().wellKnown();
						final var crl = inputCa.getCrl();
						ca = new X509CertificateAuthority() {

							@Override
							public WebKey getJwk() {
								return jwk;
							}

							@Override
							public byte[] getDatabase() {
								return null;
							}

							@Override
							public Iterable<X509Certificate> getCertificates() {
								return null;
							}

							@Override
							public Iterable<X509CRL> getCrl() {
								return crl;
							}
						};
					}
				} else if (cmd.equals("self"))
					key = self(inputKey);
				else if (cmd.equals("req")) {
					req(System.out, inputKey);
					return;
				} else if (cmd.equals("sign"))
					ca = sign(inputCa, Path.of(arg[1]));
				else if (cmd.equals("cert"))
					key = cert(inputKey, Path.of(arg[1]));
				else if (cmd.equals("revoke"))
					ca = revoke(inputCa, parseSerial(arg[1]));
				else
					throw new IllegalArgumentException("invalid command " + cmd);
			}

			final JsonValue json;
			if (key != null)
				json = CryptJsonAdapters.WEBKEY.toJson(key);
			else
				json = CryptJsonAdapters.CA.toJson(ca);

			IuJson.PROVIDER.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true)).createWriter(System.out)
					.write(json);
			System.out.println();

		} catch (RuntimeException | Error e) {
			System.err.print(USAGE);
			throw e;
		}
	}

}
