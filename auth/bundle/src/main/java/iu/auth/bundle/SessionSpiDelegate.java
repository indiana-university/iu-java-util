package iu.auth.bundle;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import javax.security.auth.Subject;

import edu.iu.auth.session.IuSessionAttribute;
import edu.iu.auth.session.IuSessionHeader;
import edu.iu.auth.session.IuSessionToken;
import edu.iu.auth.spi.IuSessionSpi;

/**
 * Delegating SPI implementation.
 */
public class SessionSpiDelegate implements IuSessionSpi {

	private static final IuSessionSpi DELEGATE = Bootstrap.load(IuSessionSpi.class);

	/**
	 * Default constructor.
	 */
	public SessionSpiDelegate() {
	}

	@Override
	public String register(Set<String> realm, Subject provider) {
		return DELEGATE.register(realm, provider);
	}

	@Override
	public void register(URI issuer, URI jwksUri, Duration tokenTtl, Duration refreshInterval) {
		DELEGATE.register(issuer, jwksUri, tokenTtl, refreshInterval);
	}

	@Override
	public IuSessionToken create(IuSessionHeader header) {
		return DELEGATE.create(header);
	}

	@Override
	public IuSessionToken refresh(Subject subject, String refreshToken) {
		return DELEGATE.refresh(subject, refreshToken);
	}

	@Override
	public IuSessionToken authorize(String audience, String accessToken) {
		return DELEGATE.authorize(audience, accessToken);
	}

	@Override
	public <T> IuSessionAttribute<T> createAttribute(String name, String attributeName, T attributeValue) {
		return DELEGATE.createAttribute(name, attributeName, attributeValue);
	}

}
