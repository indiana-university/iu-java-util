package edu.iu.crypt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import edu.iu.crypt.WebEncryption.Encryption;
import edu.iu.crypt.WebKey.Algorithm;
import edu.iu.crypt.WebKey.Operation;
import edu.iu.crypt.WebKey.Type;
import edu.iu.crypt.WebKey.Use;

public final class JwkTool {

	public static void main(String[] args) {
		// verify args length, might need to be different based on the value of the
		// second argument
		if (args.length != 10) {
			System.out.println("Usage: JwkTool verbose ephemeral no-builder overwrite type algorithm encryption use ops outFile");
			System.exit(1);
		}
		String verboseStr = args[0];
		String ephemeralStr = args[1];
		String noBuilderStr = args[2];
		String overwriteStr = args[3];
		String typeStr = args[4];
		String algorithmStr = args[5];
		String encryptionStr = args[6];
		String useStr = args[7];
		String opsStr = args[8];
		String outFile = args[9];

		System.out.println("Inputs:");
		System.out.println("  verbose: " + verboseStr);
		System.out.println("  ephemeral: " + ephemeralStr);
		System.out.println("  noBuilder: " + noBuilderStr);
		System.out.println("  overwrite: " + overwriteStr);
		System.out.println("  type: " + typeStr);
		System.out.println("  algorithm: " + algorithmStr);
		System.out.println("  encryption: " + encryptionStr);
		System.out.println("  use: " + useStr);
		System.out.println("  ops: " + opsStr);
		System.out.println("  outFile: " + outFile);

		boolean hasType = typeStr != null && !typeStr.isBlank() && !"NONE".equals(typeStr);
		boolean hasAlgorithm = algorithmStr != null && !algorithmStr.isBlank() && !"NONE".equals(algorithmStr);
		boolean hasEncryption = encryptionStr != null && !encryptionStr.isBlank() && !"NONE".equals(encryptionStr);
		Type type = (hasType) ? Type.valueOf(typeStr) : null;
		Algorithm algorithm = (hasAlgorithm) ? Algorithm.valueOf(algorithmStr) : null;
		Encryption encryption = (hasEncryption) ? Encryption.valueOf(encryptionStr) : null;
		Use use = useStr != null && !"NONE".equals(useStr) ? Use.valueOf(useStr) : null;
		// TODO: what should verbose mean in this context? Or should it just be used to
		// pass a verbose command flag to the java call from the shell script?
		boolean verbose = Boolean.parseBoolean(verboseStr);
		boolean ephemeral = Boolean.parseBoolean(ephemeralStr);
		boolean noBuilder = Boolean.parseBoolean(noBuilderStr);
		boolean overwrite = Boolean.parseBoolean(overwriteStr);
		List<Operation> opsList = opsStr != null && !"NONE".equals(opsStr) //
				? Arrays.asList(opsStr.split(" ")).stream().map(o -> Operation.valueOf(o)).collect(Collectors.toList()) //
				: null;
		Operation[] ops = opsList != null ? opsList.toArray(new Operation[opsList.size()]) : null;
		File file = new File(outFile);

		System.out.println("");
		System.out.println("Inputs Translated to:");
		System.out.println("  verbose: " + verbose);
		System.out.println("  ephemeral: " + ephemeral);
		System.out.println("  noBuilder: " + noBuilder);
		System.out.println("  overwrite: " + overwrite);
		System.out.println("  type: " + type);
		System.out.println("  algorithm: " + algorithm);
		System.out.println("  encryption: " + encryption);
		System.out.println("  use: " + use);
		System.out.println("  ops: " + ops);
		System.out.println("  file: " + file.getAbsolutePath() + "/" + file.getName());
		System.out.println("");
		System.out.println("Has Value Checks:");
		System.out.println("  hasType: " + hasType);
		System.out.println("  hasAlgorithm: " + hasAlgorithm);
		System.out.println("  hasEncryption: " + hasEncryption);
		System.out.println("");

		if (file.exists() && !overwrite) {
			System.out.println("File exists and no overwrite options was given. Exiting.");
			System.exit(1);
		}

		/*
		 * Rules: - if no-builder is specified, ephemeral must be true, and algorithm or
		 * encryption must be specified - else - type, algorithm, ops, and use are all
		 * required. encryption is optional (or should it not be used?) - ops may be an
		 * array (comman separated list? or should it be space separated? how will
		 * get_opt interpret each?) - add all available options to builder - build -
		 * verify - output key if no errors
		 */
		WebKey key = null;
		if (noBuilder) {
			if (!validNoBuilderArgs(ephemeral, hasAlgorithm, hasEncryption)) {
				System.out.println("no builder can only be used with ephemeral and either an algorithm or encryption");
				System.exit(1);
			}
			key = hasAlgorithm ? WebKey.ephemeral(algorithm) : WebKey.ephemeral(encryption);
		} else {
			// Objects.requireNonNull(webKey.getType(), "Key type is required");
			if (!validBuilderArgs(hasType, hasAlgorithm, use, ops)) {
				System.out.println("type or algorithm must be provided. use and ops must be provided.");
				System.exit(1);
			}

			if (hasAlgorithm)
				if (hasType)
					if (ephemeral)
						key = WebKey.builder(type).algorithm(algorithm).ephemeral().use(use).ops(ops).build();
					else
						key = WebKey.builder(type).algorithm(algorithm).use(use).ops(ops).build();
				else if (ephemeral)
					key = WebKey.builder(algorithm).ephemeral().use(use).ops(ops).build();
				else
					key = WebKey.builder(algorithm).use(use).ops(ops).build();
		}

		printKeyInfo(key);
		writeToFile(key, file, overwrite);
		// WebKey.builder(Type.EC_P256).algorithm(Algorithm.ES256).ephemeral().use(Use.SIGN).build();
	}
	
	private static void printKeyInfo(WebKey key) {
		System.out.println("key:");
		System.out.println(key);
		System.out.println("Public Key:");
		System.out.println(key.getPublicKey());
		System.out.println("Private Key:");
		System.out.println(key.getPrivateKey());
		System.out.println("Certificate Chain:");
		System.out.println(key.getCertificateChain());
		System.out.println("kid:");
		System.out.println(key.getKeyId());
		System.out.println("cert URI:");
		System.out.println(key.getCertificateUri());
	}
	
	private static void writeToFile(WebKey key, File file, boolean overwrite) {
		try {
			if (!file.exists()) {
				boolean created = file.createNewFile();
				if (!created && !overwrite) {
					System.out.println("File exists and no overwrite options was given. Exiting.");
					System.exit(1);		
				}
				if (file.isDirectory() || !file.canWrite()) {
					System.out.println("file must not be a directory and must be writeable");
					System.exit(1);
				}
			}
			FileWriter writer = new FileWriter(file);
			Iterator<PemEncoded> pemIter = PemEncoded.serialize(new KeyPair(key.getPublicKey(), key.getPrivateKey()), new X509Certificate[0]);
			System.out.println("Printing PemEncodeds");
			while (pemIter.hasNext()) {
				PemEncoded pem = pemIter.next();
				System.out.println(pem.toString());
				writer.write(pem.toString());
			}
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static boolean validNoBuilderArgs(boolean ephemeral, boolean hasAlgorithm, boolean hasEncryption) {
		return ephemeral && //
				(hasAlgorithm || //
						hasEncryption);
	}

	private static boolean validBuilderArgs(boolean hasType, boolean hasAlgorithm, Use use, Operation[] ops) {
		return (hasType || //
				hasAlgorithm) && //
				use != null && //
				ops != null;
	}
}
