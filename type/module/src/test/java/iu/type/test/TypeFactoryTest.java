package iu.type.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import edu.iu.type.IuType;

@SuppressWarnings("javadoc")
public class TypeFactoryTest {

//	private static ClassLoader testcomponent;
//	private static Class<?> testBean;
//	private static Class<?> testBeanImpl;
//
//	@BeforeAll
//	public static void setupClass() throws IOException, URISyntaxException, ClassNotFoundException {
//		try (var logger = mockStatic(Logger.class)) {
//			var log = mock(Logger.class);
//			logger.when(() -> Logger.getLogger(IuType.class.getName())).thenReturn(log);
//			IuType.of(Object.class);
//			doNothing().when(log).log(any(Level.class), any(Throwable.class),
//					argThat(new ArgumentMatcher<Supplier<String>>() {
//						@Override
//						public boolean matches(Supplier<String> argument) {
//							assertNotNull(argument.get());
//							return true;
//						}
//					}));
//		}

	@Test
	public void testResolves() {
		var type = IuType.of(Object.class);
		assertNotNull(type);
		assertSame(Object.class, type.deref());
		assertEquals(Object.class.getName(), type.name());
	}

	@Test
	public void testParityWithClass() {
		assertSame(IuType.of(Object.class), IuType.of(Object.class));
	}

}
