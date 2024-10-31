package iu.auth.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.auth.config.IuSessionConfiguration;
import edu.iu.client.IuJsonAdapter;

@SuppressWarnings("javadoc")
public class SessionAdapterFactoryTest {

	public interface TestResource {
		URI getUri();

		String getValue();
	}

	public interface TestSession {
		Iterable<TestResource> getResources();

		void setResources(Iterable<TestResource> resources);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testApply() {
		final Queue<TestResource> resources = new ArrayDeque<>();
		final var uri = URI.create(IdGenerator.generateId());
		final var value = IdGenerator.generateId();
		final var resource = mock(TestResource.class);
		when(resource.getUri()).thenReturn(uri);
		when(resource.getValue()).thenReturn(value);
		resources.add(resource);
		final var session = mock(TestSession.class);
		when(session.getResources()).thenReturn(resources);

		final var factory = new SessionAdapterFactory<>(TestSession.class);
		final var adapter = (IuJsonAdapter) factory.apply(TestSession.class);
		final var serialized = adapter.toJson(session);
		final var copy = (TestSession) adapter.fromJson(serialized);
		final var copyResources = copy.getResources();
		final var copyResource = copyResources.iterator().next();
		assertEquals(uri, copyResource.getUri());
		assertEquals(value, copyResource.getValue());
	}

	public interface IllegalSession {
		IuSessionConfiguration getConfiguration(); // disallowed, not same module
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testIllegalSession() {
		final var factory = new SessionAdapterFactory<>(IllegalSession.class);
        final var adapter = (IuJsonAdapter) factory.apply(IllegalSession.class);
        final var session = mock(IllegalSession.class);
        assertThrows(UnsupportedOperationException.class, () -> adapter.toJson(session));
	}
	
}
