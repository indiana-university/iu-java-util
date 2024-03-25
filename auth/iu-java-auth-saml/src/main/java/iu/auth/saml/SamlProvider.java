package iu.auth.saml;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.ServiceConfigurationError;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.config.XMLConfigurator;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.saml.config.SAMLConfiguration;
import org.opensaml.saml.metadata.resolver.ChainingMetadataResolver;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.xmlsec.config.DecryptionParserPool;
import org.w3c.dom.Element;

import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;
import net.shibboleth.shared.component.ComponentInitializationException;
import net.shibboleth.shared.resolver.CriteriaSet;
import net.shibboleth.shared.resolver.ResolverException;
import net.shibboleth.shared.xml.ParserPool;

public class SamlProvider implements IuSamlProvider {
	private IuSamlClient client;
	private MetadataResolver metadataResolver;
	private long lastMetadataUpdate;
	private String ssoLocation;

	static {
		ConfigurationService.register(XMLObjectProviderRegistry.class, new XMLObjectProviderRegistry());
		ConfigurationService.register(SAMLConfiguration.class, new SAMLConfiguration());
		ConfigurationService.register(DecryptionParserPool.class, new DecryptionParserPool(new SamlParserPool()));

		Thread current = Thread.currentThread();
		ClassLoader currentLoader = current.getContextClassLoader();
		try {
			ClassLoader scl = SamlConnectSpi.class.getClassLoader();
			current.setContextClassLoader(scl);

			XMLConfigurator config = new XMLConfigurator();
			config.load(scl.getResourceAsStream("default-config.xml"));
			config.load(scl.getResourceAsStream("schema-config.xml"));
			config.load(scl.getResourceAsStream("saml-ec-gss-config.xml"));
			config.load(scl.getResourceAsStream("saml1-assertion-config.xml"));
			config.load(scl.getResourceAsStream("saml1-metadata-config.xml"));
			config.load(scl.getResourceAsStream("saml1-protocol-config.xml"));
			config.load(scl.getResourceAsStream("saml2-assertion-config.xml"));
			config.load(scl.getResourceAsStream("saml2-assertion-delegation-restriction-config.xml"));
			config.load(scl.getResourceAsStream("saml2-channel-binding-config.xml"));
			config.load(scl.getResourceAsStream("saml2-ecp-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-algorithm-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-attr-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-idp-discovery-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-query-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-reqinit-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-rpi-config.xml"));
			config.load(scl.getResourceAsStream("saml2-metadata-ui-config.xml"));
			config.load(scl.getResourceAsStream("saml2-protocol-aslo-config.xml"));
			config.load(scl.getResourceAsStream("saml2-protocol-config.xml"));
			config.load(scl.getResourceAsStream("saml2-protocol-thirdparty-config.xml"));
			config.load(scl.getResourceAsStream("signature-config.xml"));
			config.load(scl.getResourceAsStream("encryption-config.xml"));

			XMLObjectProviderRegistrySupport.setParserPool(new SamlParserPool());
		} catch (Exception e) {
			throw new ExceptionInInitializerError(e);
		} finally {
			current.setContextClassLoader(currentLoader);
		}
	}

	public SamlProvider(IuSamlClient client) {
		this.client = client;
		this.metadataResolver = getMetadata(client.getMetaDataUrls(), client.getMetadataTtl());
		this.ssoLocation = getSingleSignOnLocation(client.getIdentityProviderURL().toString());
	}
	
	private String getSingleSignOnLocation(String idpUrl) {
		EntityDescriptor entity = getEntity(idpUrl);
		IDPSSODescriptor idp = entity.getIDPSSODescriptor("urn:oasis:names:tc:SAML:2.0:protocol");
		if (idp == null)
			throw new IllegalStateException("Missing SAML 2.0 IDP descriptor for " + idpUrl);
		for (SingleSignOnService sso : idp.getSingleSignOnServices())
			if ("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect".equals(sso.getBinding()))
				return sso.getLocation();
		throw new IllegalStateException("Missing SAML 2.0 Redirect Binding in IDP descriptor for " + idpUrl);
	}

	private EntityDescriptor getEntity(String primaryLdp) {
		try {
			EntityDescriptor entity = metadataResolver.resolveSingle(new CriteriaSet(new EntityIdCriterion(primaryLdp)));
			if (entity == null)
				throw new IllegalArgumentException("Entity " + primaryLdp + " not found in SAML metadata");
			return entity;
		} catch (ResolverException e) {
			throw new IllegalArgumentException("Failed to resolve SAML metadata for " + primaryLdp, e);
		}
	}

	 synchronized MetadataResolver getMetadata(String[] metadataUrls, Duration metadataTtl) {
		if (metadataResolver != null && lastMetadataUpdate >=
		 (System.currentTimeMillis() - metadataTtl.toMillis()))
		 return metadataResolver;

		Queue<Throwable> failures = new ArrayDeque<>();
		List<MetadataResolver> resolvers = new ArrayList<>();

		ParserPool pp = XMLObjectProviderRegistrySupport.getParserPool();
		for (String metadataUrl : metadataUrls)
			try {
				Element md;
				try (InputStream in = new URL(metadataUrl).openStream()) {
					md = pp.parse(in).getDocumentElement();
				}

				DOMMetadataResolver mdr = new DOMMetadataResolver(md);
				mdr.setId("iu-metadata");
				mdr.setRequireValidMetadata(true);
				mdr.initialize();

				resolvers.add(mdr);

			} catch (Throwable e) {
				failures.add(new ServiceConfigurationError(metadataUrl, e));
			}

		if (!failures.isEmpty()) {
			ServiceConfigurationError error = new ServiceConfigurationError(
					"Failed to load SAML metadata for at least one provider");
			failures.forEach(error::addSuppressed);
			if (resolvers.isEmpty())
				throw error;
			// else
			// LOG.log(Level.WARNING, error, error::getMessage);
		}

		ChainingMetadataResolver cmdr = new ChainingMetadataResolver();
		cmdr.setId("iu-endpoint-SAMLMetadata");
		try {
			cmdr.setResolvers(resolvers);
			cmdr.initialize();
		} catch (ComponentInitializationException | ResolverException e) {
			throw new IllegalStateException(e);
		}

		if (!failures.isEmpty())
			return cmdr;

		metadataResolver = cmdr;
		lastMetadataUpdate = System.currentTimeMillis();

		return metadataResolver;
	}

}
