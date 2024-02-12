package iu.auth.oauth;

import java.io.Serializable;
import java.security.Principal;

import edu.iu.IdGenerator;
import edu.iu.IuObject;

@SuppressWarnings("javadoc")
class MockPrincipal implements Principal, Serializable {
	private static final long serialVersionUID = 1L;

	private final String name = IdGenerator.generateId();

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCode(name);
	}

	@Override
	public boolean equals(Object obj) {
		if (!IuObject.typeCheck(this, obj))
			return false;
		MockPrincipal other = (MockPrincipal) obj;
		return IuObject.equals(name, other.name);
	}
}