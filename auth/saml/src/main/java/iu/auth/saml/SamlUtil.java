package iu.auth.saml;

import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialResolver;
import org.opensaml.security.credential.impl.StaticCredentialResolver;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.X509Certificate;
import org.opensaml.xmlsec.signature.X509Data;



/**
 * Utility class to support SAML implementation. TODO review this class and move
 * code to crypt package as needed
 */
public class SamlUtil {
	private final static Logger LOG = Logger.getLogger(SamlUtil.class.getName());

	private static Key CRYPT_KEY;

	static {
		try {
			CRYPT_KEY = KeyGenerator.getInstance("AES").generateKey();
		} catch (NoSuchAlgorithmException e) {
			throw new SecurityException(e);
		}
	}

	/**
	 * default constructor
	 */
	public SamlUtil() {
	}

	/**
	 * decrypt data
	 * 
	 * @param d string to decrypt
	 * @return decrypted string
	 */
	public static String decrypt(String d) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, CRYPT_KEY);
			String de = new String(cipher.doFinal(Base64.getDecoder().decode(d)), "UTF-8").trim();
			return de;
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to decrpty", e);
		}
	}

	/**
	 * encrypt data
	 * 
	 * @param s string to encrypt
	 * @return encrypted string
	 */
	public static String encrypt(String s) {
		try {
			byte[] dataToEncrypt = s.trim().getBytes("UTF-8");
			int mod = dataToEncrypt.length % 16;
			if (mod != 0)
				dataToEncrypt = Arrays.copyOf(dataToEncrypt, dataToEncrypt.length + 16 - mod);
			Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, CRYPT_KEY);
			return Base64.getEncoder().encodeToString(cipher.doFinal(dataToEncrypt));
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to encrypt", e);
		}
	}

	/**
	 * Get credential resolver for SAML response
	 * 
	 * @param response SAML response
	 * @return credential resolver
	 */
	public static KeyInfoCredentialResolver getKeyInfoCredentialResolver(Response response) {
		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("X.509");
		} catch (CertificateException e) {
			throw new IllegalArgumentException(e);
		}

		List<Credential> certs = new ArrayList<>();
		for (Assertion assertion : response.getAssertions())
			if (assertion.getSignature() != null && assertion.getSignature().getKeyInfo() != null)
				for (X509Data x509data : assertion.getSignature().getKeyInfo().getX509Datas())
					for (X509Certificate x509cert : x509data.getX509Certificates())
						try {
							StringBuilder keyData = new StringBuilder(x509cert.getValue());
							for (int i = 0; i < keyData.length();)
								if (Character.isWhitespace(keyData.charAt(i)))
									keyData.deleteCharAt(i);
								else
									i++;

							certs.add(new BasicX509Credential(
									(java.security.cert.X509Certificate) certFactory.generateCertificate(
											new ByteArrayInputStream(Base64.getDecoder().decode(keyData.toString())))));

						} catch (CertificateException e) {
							LOG.log(Level.WARNING, e,
									() -> "Invalid cert in response data for " + response.getDestination());
						}

		return new StaticKeyInfoCredentialResolver(certs);
	}

	/**
	 * Get private key
	 * 
	 * @param privateKey key to convert to private key
	 * @return private key
	 */
	public static PrivateKey getPrivateKey(String privateKey) {
		PrivateKey parsedPrivateKey = null;
		StringBuilder pk = new StringBuilder(privateKey);
		int i = pk.indexOf("-----BEGIN PRIVATE KEY-----");
		if (i != -1)
			pk.delete(0, i + 28);
		i = pk.indexOf("-----END PRIVATE KEY-----");
		if (i != -1)
			pk.setLength(i);
		for (i = 0; i < pk.length(); i++)
			if (Character.isWhitespace(pk.charAt(i)))
				pk.deleteCharAt(i--);

		PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pk.toString()));
		KeyFactory kf;
		try {
			kf = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try {
			parsedPrivateKey = kf.generatePrivate(spec);
		} catch (InvalidKeySpecException e) {
			throw new SecurityException(e);
		}

		return parsedPrivateKey;
	}

	/**
	 * CredentialResolver
	 * 
	 * @param entity {@link EntityDescriptor}
	 * @return {@link CredentialResolver}
	 */
	public static CredentialResolver getCredentialResolver(EntityDescriptor entity) {
		IDPSSODescriptor idp = entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol");
		CertificateFactory certFactory;
		try {
			certFactory = CertificateFactory.getInstance("X.509");
		} catch (CertificateException e) {
			throw new IllegalArgumentException(e);
		}

		List<Credential> certs = new ArrayList<>();
		for (KeyDescriptor kds : idp.getKeyDescriptors())
			if (kds.getKeyInfo() != null)
				for (X509Data x509data : kds.getKeyInfo().getX509Datas())
					for (org.opensaml.xmlsec.signature.X509Certificate x509cert : x509data.getX509Certificates())
						try {
							StringBuilder keyData = new StringBuilder(x509cert.getValue());
							for (int i = 0; i < keyData.length();)
								if (Character.isWhitespace(keyData.charAt(i)))
									keyData.deleteCharAt(i);
								else
									i++;

							certs.add(new BasicX509Credential(
									(java.security.cert.X509Certificate) certFactory.generateCertificate(
											new ByteArrayInputStream(Base64.getDecoder().decode(keyData.toString())))));

						} catch (CertificateException e) {
							LOG.log(Level.WARNING, e, () -> "Invalid cert in key data");

						}

		return new StaticCredentialResolver(certs);
	}
	
}
