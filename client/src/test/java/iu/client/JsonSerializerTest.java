package iu.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;

import edu.iu.IdGenerator;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;

@SuppressWarnings("javadoc")
public class JsonSerializerTest {

	@Test
	public void testFormatPropertyName() {
		final var name = IdGenerator.generateId();
		final var convertedName = IdGenerator.generateId();
		try (final var mockJsonProxy = mockStatic(JsonProxy.class)) {
			mockJsonProxy.when(() -> JsonProxy.convertToSnakeCase(name)).thenReturn(convertedName);

			assertEquals(convertedName,
					JsonSerializer.formatPropertyName(name, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES));
			assertEquals(convertedName.toUpperCase(),
					JsonSerializer.formatPropertyName(name, IuJsonPropertyNameFormat.UPPER_CASE_WITH_UNDERSCORES));
		}
	}

	@Test
	public void testSerializePlainObject() {
		assertEquals(IuJson.object().build(),
				JsonSerializer.serialize(new Object(), IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

	@Test
	public void testSerializeBean() {
		class Bean {
			String id;

			@SuppressWarnings("unused")
			public String getId() {
				return id;
			}

			@SuppressWarnings("unused")
			public void setNothing(String nothing) {
				// covers readMethod == null case
			}
		}
		final var bean = new Bean();
		bean.id = IdGenerator.generateId();
		assertEquals(IuJson.object().add("id", bean.id).build(),
				JsonSerializer.serialize(bean, IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

	interface TestInterface {
	}

	@Test
	public void testUnwrapProxy() {
		final var wrapped = IuJson.object().build();
		final var proxy = IuJson.wrap(wrapped, TestInterface.class);
		assertSame(wrapped, JsonSerializer.serialize(proxy, IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

	@Test
	public void testDoesntUnwrapNonJsonProxy() {
		final var value = Proxy.newProxyInstance(TestInterface.class.getClassLoader(),
				new Class<?>[] { TestInterface.class }, (proxy, method, args) -> fail());
		assertEquals(IuJson.object().build(),
				JsonSerializer.serialize(value, IuJsonPropertyNameFormat.IDENTITY, IuJsonAdapter::of));
	}

}
