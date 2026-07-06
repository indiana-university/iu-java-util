package edu.iu;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ServiceLoader;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;

@SuppressWarnings({ "javadoc", "exports" })
public class IuTestListener implements IuListener, BeforeEachCallback, AfterEachCallback {

	static UnsafeConsumer<IuObservableEvent> delegate;
	private ServiceLoader<IuListener> loader;
	@SuppressWarnings("rawtypes")
	private MockedStatic<ServiceLoader> mockSL;

	@Override
	public void accept(IuObservableEvent argument) throws Throwable {
		if (delegate != null)
			delegate.accept(argument);
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		mockSL.close();
		delegate = null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		final IuListener listener = new IuTestListener();
		loader = mock(ServiceLoader.class);
		when(loader.iterator()).thenReturn(IuIterable.iter(listener).iterator());
		mockSL = mockStatic(ServiceLoader.class);
		mockSL.when(() -> ServiceLoader.load(IuListener.class)).thenReturn(loader);
		delegate = mock(UnsafeConsumer.class);
	}

}
