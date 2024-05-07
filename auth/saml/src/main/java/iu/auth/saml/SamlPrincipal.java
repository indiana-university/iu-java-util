package iu.auth.saml;

import javax.security.auth.Subject;

import edu.iu.IuObject;
import edu.iu.auth.saml.IuSamlPrincipal;

/**
 * Implementation of {@link IuSamlPrincipal}
 */
public class SamlPrincipal implements IuSamlPrincipal {

	/**
	 * TODO
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * TODO
	 */
	private final String name;
	
	/**
	 * TODO
	 */
	private final String displayName;
	
	/**
	 * TODO
	 */
	private final String emailAddress;

	/**
	 * Constructor
	 * 
	 * @param name eduPersonPrincipalName 
	 * @param displayName display name
	 * @param emailAddress email address
	 */
	public SamlPrincipal(String name, String displayName, String emailAddress) {
		this.name = name;
		this.displayName = displayName;
		this.emailAddress = emailAddress;
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

	@Override
	public int hashCode() {
		return IuObject.hashCode(name, displayName, emailAddress);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		SamlPrincipal other = (SamlPrincipal) obj;
		return IuObject.equals(name, other.name) //
				&& IuObject.equals(displayName, other.displayName) //
				&& IuObject.equals(emailAddress, other.emailAddress);
	}

	@Override
	public String toString() {
		return "OIDC Principal ID [" + name + "; " + displayName + "; " + emailAddress + "] ";
	}

}
