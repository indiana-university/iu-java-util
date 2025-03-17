package iu.web.server;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.IuException;

@SuppressWarnings("javadoc")
public class UpstreamProxyFilterTest {

	private UpstreamProxyFilter filter;

	private void set(String field, Object value) {
		IuException.unchecked(() -> {
			final var f = UpstreamProxyFilter.class.getDeclaredField(field);
			f.setAccessible(true);
			f.set(filter, value);
		});
	}

	private void setXForwardedForHeader(String xForwardedForHeader) {
		set("xForwardedForHeader", xForwardedForHeader);
	}

	private void setXForwardedHostHeader(String xForwardedHostHeader) {
		set("xForwardedHostHeader", xForwardedHostHeader);
	}

	private void setAllowProxy(Set<String> allowProxy) {
		set("allowProxy", allowProxy);
	}

	private void setExpectForwarded(boolean expectForwarded) {
		set("expectForwarded", expectForwarded);
	}

	@BeforeEach
	public void setup() {
		this.filter = new UpstreamProxyFilter();
	}

	@Test
	public void testInit() {

	}

}
