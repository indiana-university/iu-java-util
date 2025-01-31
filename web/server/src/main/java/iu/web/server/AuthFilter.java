package iu.web.server;

import java.io.IOException;
import java.security.Principal;
import java.util.Objects;

import javax.security.auth.Subject;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import edu.iu.web.IuWebAuthenticator;

class AuthFilter extends Filter {

	private static final ThreadLocal<Subject> AUTH_SUBJECT = new ThreadLocal<>();

	private final IuWebAuthenticator authenticator;

	AuthFilter(IuWebAuthenticator authenticator) {
		this.authenticator = authenticator;
	}

	/**
	 * Gets the authenticated {@link Principal} for the current request.
	 * 
	 * @return {@link Principal}
	 */
	static Subject getAuthenticatedSubject() {
		return Objects.requireNonNull(AUTH_SUBJECT.get(), "not active");
	}

	@Override
	public String description() {
		return "Authentication filter";
	}

	@Override
	public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
		try {
			AUTH_SUBJECT.set(authenticator.authenticate(exchange));
			chain.doFilter(exchange);
		} finally {
			AUTH_SUBJECT.remove();
		}
	}

}
