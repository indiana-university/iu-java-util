package iu.logging.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;

@SuppressWarnings("javadoc")
public class IuLoggingProxyTest {

	interface I {
		String getValue();
	}

	interface O {
		String getValue();
	}

	class Impl {
		final private String value;

		Impl(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	@Test
	public void testPassThroughNull() {
		assertNull(IuLoggingProxy.adapt(Object.class, null));
	}

	@Test
	public void testPassThroughSame() {
		final var o = new Object();
		assertSame(o, IuLoggingProxy.adapt(Object.class, o));
	}

	@Test
	public void testProxy() {
		final var value = IdGenerator.generateId();
		final var impl = new Impl(value);
		final var i = IuLoggingProxy.adapt(I.class, impl);
		assertEquals(value, i.getValue());
	}

	@Test
	public void testProxySwap() {
		final var value = IdGenerator.generateId();
		final var impl = new Impl(value);
		final var o = IuLoggingProxy.adapt(O.class, IuLoggingProxy.adapt(I.class, impl));
		assertEquals(value, o.getValue());
	}

	@Test
	public void testProxyImpl() {
		final var value = IdGenerator.generateId();
		final var impl = new Impl(value);
		final var o = IuLoggingProxy.adapt(O.class,
				Proxy.newProxyInstance(I.class.getClassLoader(), new Class<?>[] { I.class },
						(proxy, method, args) -> Impl.class.getMethod(method.getName(), method.getParameterTypes())
								.invoke(impl, args)));
		assertEquals(value, o.getValue());
	}

}
