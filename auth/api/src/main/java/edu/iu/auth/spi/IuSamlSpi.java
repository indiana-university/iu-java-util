package edu.iu.auth.spi;



import edu.iu.auth.saml.IuSamlClient;
import edu.iu.auth.saml.IuSamlProvider;


/**
 * Service provider interface for SAML.
 */
public interface IuSamlSpi {
	/**
	 * 
	 * @param client
	 * @return
	 */
	IuSamlProvider getSamlProvider(IuSamlClient client);

}
