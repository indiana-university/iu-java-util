package iu.auth.saml;

import java.io.ByteArrayInputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

	/**
	 * default constructor
	 */
	public SamlUtil() {
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
