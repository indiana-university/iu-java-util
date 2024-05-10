package iu.auth.saml;

import java.time.Duration;
import java.util.Map;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.saml.IuSamlPrincipal;

/**
 * Implementation of {@link IuSamlPrincipal}
 */
final class SamlPrincipal implements IuSamlPrincipal {

	/**
	 * serial version
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * name
	 */
	private final String name;

	/**
	 * displayName
	 */
	private final String displayName;

	/**
	 * emailAddress
	 */
	private final String emailAddress;

	/**
	 * identity provider id
	 */
	private final String entityId;

	/**
	 * service provider id
	 */
	private final String realm;
	
	private Map<String, ?> claims;

	//private final Duration NotBefore;
	
	//private final Duration NotOnOrAfter;
	
	
	/**
	 * Constructor
	 * 
	 * @param name         eduPersonPrincipalName
	 * @param displayName  display name
	 * @param emailAddress email address
	 * @param entityId     identity provider URI
	 * @param realm        service provider id
	 */
	public SamlPrincipal(String name, String displayName, String emailAddress, String entityId, String realm, Map<String, ?> claims) {
		this.name = name;
		this.displayName = displayName;
		this.emailAddress = emailAddress;
		this.entityId = entityId;
		this.realm = realm;
		this.claims = claims;
	}
	
    public Map<String, ?> getClaims() {
		return claims;
	}

	@Override
	public String getName() {
		return name;
	}

	/**
	 * Gets principal display name
	 * 
	 * @return principal display name
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Gets the principal email address
	 * 
	 * @return principal email address
	 */
	public String getEmailAddress() {
		return emailAddress;
	}

	@Override
	public Subject getSubject() {
		final var subject = new Subject();
		subject.getPrincipals().add(this);
		subject.setReadOnly();
		return subject;
	}

	/**
	 * Gets the authentication realm.
	 * 
	 * @return authentication realm
	 */
	String realm() {
		return realm;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name, displayName, emailAddress, entityId, realm);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		SamlPrincipal other = (SamlPrincipal) obj;
		return IuObject.equals(name, other.name) //
				&& IuObject.equals(displayName, other.displayName) //
				&& IuObject.equals(emailAddress, other.emailAddress)//
				&& IuObject.equals(entityId, other.entityId)//
				&& IuObject.equals(realm, other.realm);
	}

	@Override
	public String toString() {
		return "SAML Principal ID [" + name + "; " + displayName + "; " + emailAddress + "; " + entityId + "; " + realm
				+ "] ";
	}

}
