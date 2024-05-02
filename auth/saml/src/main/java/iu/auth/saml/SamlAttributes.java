package iu.auth.saml;

/**
 * SAML attributes return from SAML response 
 */
public class SamlAttributes {
	private String entityId;
	private String eduPersonPrincipalName;
	private String displayName;
	private String mail;
	private String inResponseTo;

	/**
	 * default constructor
	 */
	public SamlAttributes() {
		// TODO Auto-generated constructor stub
	}
	/**
	 * Gets service provider entity id
	 * Return service provider entity id
	 * @return entity id 
	 */
	public String getEntityId() {
		return entityId;
	}
	
	/**
	 * Set the service provider entity id
	 * @param entityId SP entity id
	 */

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	/**
	 * Gets principal primary name
	 * @return  principal primary name
	 */
	public String getEduPersonPrincipalName() {
		return eduPersonPrincipalName;
	}

	/**
	 * Set principal primary name
	 * @param eduPersonPrincipalName principal name
	 */
	public void setEduPersonPrincipalName(String eduPersonPrincipalName) {
		this.eduPersonPrincipalName = eduPersonPrincipalName;
	}

	/**
	 * Gets principal display name
	 * @return principal display name
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Set principal display name
	 * @param displayName principal display name
	 */
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * Gets the principal email address
	 * @return principal mail
	 */
	public String getMail() {
		return mail;
	}

	/**
	 * Set principal email address
	 * @param mail principal email address
	 */
	public void setMail(String mail) {
		this.mail = mail;
	}

	/**
	 * Gets session id 
	 * @return session id
	 */
	public String getInResponseTo() {
		return inResponseTo;
	}

	/**
	 * Set the session id from SAML response
	 * @param inResponseTo session id
	 */
	public void setInResponseTo(String inResponseTo) {
		this.inResponseTo = inResponseTo;
	}

}
