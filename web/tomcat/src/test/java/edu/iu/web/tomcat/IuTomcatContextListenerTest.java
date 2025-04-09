
package edu.iu.web.tomcat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.logging.Level;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.core.StandardContext;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.iu.test.IuTestLogger;

@SuppressWarnings("javadoc")
public class IuTomcatContextListenerTest {

	private IuTomcatContextListener listener;
	private LifecycleEvent event;
	private Context contextSpy;
	private Object data;

	@BeforeEach
	void setUp() {
		IuTestLogger.allow("org.apache.tomcat", Level.FINE);
		listener = new IuTomcatContextListener();
		contextSpy = spy(new StandardContext());
		data = mock(Object.class);
		event = new LifecycleEvent(contextSpy, "before_start", data);
	}

	@Test
	void lifecycleEvent_beforeStart() throws MalformedURLException {
		listener.lifecycleEvent(event);
		verify(contextSpy).setPath("");
		verify(contextSpy).addFilterDef(any(FilterDef.class));
		verify(contextSpy).addFilterMapBefore(any(FilterMap.class));
	}

	@Test
	void lifecycleEvent_withMalformedURLException_throwsIllegalStateException() {
		try (final var mockUri = mockStatic(URI.class, CALLS_REAL_METHODS)) {
			mockUri.when(() -> URI.create("http://localhost").toURL()).thenThrow(new IllegalStateException());
			final var context = mock(StandardContext.class);
			LifecycleEvent malformedEvent = new LifecycleEvent(context, "before_start", data);
			assertThrows(IllegalStateException.class, () -> listener.lifecycleEvent(malformedEvent));
		}
	}

	@Test
	void lifecycleEvent_withPeriodicEventType_logsEvent() {
		LifecycleEvent otherEvent = new LifecycleEvent(mock(Lifecycle.class), "periodic", data);
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatContextListener", Level.FINEST, "LifecycleEvent periodic.*");
		listener.lifecycleEvent(otherEvent);
	}

	@Test
	void lifecycleEvent_withOtherEventType_logsEvent() {
		LifecycleEvent otherEvent = new LifecycleEvent(mock(Lifecycle.class), "other", data);
		IuTestLogger.expect("edu.iu.web.tomcat.IuTomcatContextListener", Level.CONFIG, "LifecycleEvent other.*");
		listener.lifecycleEvent(otherEvent);
	}
}
