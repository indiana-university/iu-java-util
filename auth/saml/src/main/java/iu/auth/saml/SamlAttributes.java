package iu.auth.saml;

public class SamlAttributes {
	private String entityId;
	private String eduPersonPrincipalName;
	private String displayName;
	private String mail;
	private String inResponseTo;

	public String getEntityId() {
		return entityId;
	}

	public void setEntityId(String entityId) {
		this.entityId = entityId;
	}

	public String getEduPersonPrincipalName() {
		return eduPersonPrincipalName;
	}

	public void setEduPersonPrincipalName(String eduPersonPrincipalName) {
		this.eduPersonPrincipalName = eduPersonPrincipalName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getMail() {
		return mail;
	}

	public void setMail(String mail) {
		this.mail = mail;
	}

	public String getInResponseTo() {
		return inResponseTo;
	}

	public void setInResponseTo(String inResponseTo) {
		this.inResponseTo = inResponseTo;
	}

}
