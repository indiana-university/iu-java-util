package iu.auth.saml;

/**
 * SAML attributes return from SAML response
 */
public class SamlAttributes {
	private String entityId;
	private String eduPersonPrincipalName;
	private String displayName;
	private String emailAddress;
	private String inResponseTo;

	/**
	 * default constructor
	 */
	public SamlAttributes() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Gets identity provider entity id Return identity provider entity id
	 * 
	 * @return entity id
	 */
	public String getEntityId() {
		return entityId;
	}

	/**
	 * Set the identity provider id
	 * 
	 * @param entityId identity provider entity id
	 */

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	/**
	 * Gets principal primary name
	 * 
	 * @return principal primary name
	 */
	public String getEduPersonPrincipalName() {
		return eduPersonPrincipalName;
	}

	/**
	 * Set principal primary name
	 * 
	 * @param eduPersonPrincipalName principal name
	 */
	public void setEduPersonPrincipalName(String eduPersonPrincipalName) {
		this.eduPersonPrincipalName = eduPersonPrincipalName;
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
	 * Set principal display name
	 * 
	 * @param displayName principal display name
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * Gets the principal email address
	 * 
	 * @return principal email address
	 */
	public String getEmailAddress() {
		return emailAddress;
	}

	/**
	 * Set principal email address
	 * 
	 * @param emailAddress principal email address
	 */
	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}

	/**
	 * Gets session id
	 * 
	 * @return session id
	 */
	public String getInResponseTo() {
		return inResponseTo;
	}

	/**
	 * Set the session id from SAML response
	 * 
	 * @param inResponseTo session id
	 */
	public void setInResponseTo(String inResponseTo) {
		this.inResponseTo = inResponseTo;
	}

}
