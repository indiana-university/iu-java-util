package iu.auth.client;

import java.net.URI;

import edu.iu.auth.client.IuAuthorizationGrant;
import edu.iu.auth.client.IuAuthorizationRequest;
import edu.iu.auth.spi.IuAuthClientSpi;

/**
 * {@link IuAuthClientSpi} service provider implementation.
 */
public class AuthClientSpi implements IuAuthClientSpi {

	@Override
	public IuAuthorizationGrant createClientCredentialsGrant(IuAuthorizationRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public URI initiateAuthorizationCodeGrant(IuAuthorizationRequest request, URI redirectUri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IuAuthorizationGrant completeAuthorizationCodeGrant(URI requestUri, String code, String state) {
		// TODO Auto-generated method stub
		return null;
	}



}
