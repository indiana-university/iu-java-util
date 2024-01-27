package iu.auth.oauth;

import edu.iu.auth.oauth.IuAuthorizationFailedException;
import edu.iu.auth.oauth.IuAuthorizationGrant;
import edu.iu.auth.oauth.IuAuthorizationSession;

public class AuthorizationSession implements IuAuthorizationSession {

	@Override
	public IuAuthorizationGrant getClientCredentialsGrant() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IuAuthorizationGrant createAuthorizationCodeGrant(String realm) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IuAuthorizationGrant getAuthorizationCodeGrant(String state) throws IuAuthorizationFailedException {
		// TODO Auto-generated method stub
		return null;
	}


}
