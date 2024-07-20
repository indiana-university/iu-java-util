package iu.client;

import java.beans.Introspector;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.function.Function;

import edu.iu.IuException;
import edu.iu.IuObject;
import edu.iu.client.IuJson;
import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;
import jakarta.json.JsonObject;

/**
 * Converts from a JavaBeans business object to JSON.
 */
public final class JsonSerializer {
	static {
		IuObject.assertNotOpen(JsonSerializer.class);
	}

	private JsonSerializer() {
	}

	/**
	 * Formats a property name for JSON serialization.
	 * 
	 * @param propertyName       property name
	 * @param propertyNameFormat format
	 * @return formatted property name
	 */
	static String formatPropertyName(String propertyName, IuJsonPropertyNameFormat propertyNameFormat) {
		switch (propertyNameFormat) {
		case LOWER_CASE_WITH_UNDERSCORES:
			return JsonProxy.convertToSnakeCase(propertyName);

		case UPPER_CASE_WITH_UNDERSCORES:
			return JsonProxy.convertToSnakeCase(propertyName).toUpperCase();

		default:
		case IDENTITY:
			return propertyName;
		}
	}

	/**
	 * Serializes a business object as JSON.
	 * 
	 * @param <T>                value type
	 * @param value              business object to serialize
	 * @param propertyNameFormat property name format
	 * @param adapt              adapter function
	 * @return {@link JsonObject}
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> JsonObject serialize(T value, IuJsonPropertyNameFormat propertyNameFormat,
			Function<Type, IuJsonAdapter<?>> adapt) {

		final var valueClass = value.getClass();
		if (Proxy.isProxyClass(valueClass)) {
			final var invocationHandler = Proxy.getInvocationHandler(value);
			if (invocationHandler instanceof JsonProxy)
				return JsonProxy.unwrap(value);
		}

		final var builder = IuJson.object();

		for (final var propertyDescriptor : IuException.unchecked(() -> Introspector.getBeanInfo(value.getClass()))
				.getPropertyDescriptors()) {
			final var readMethod = propertyDescriptor.getReadMethod();
			if (readMethod == null || readMethod.getDeclaringClass() == Object.class)
				continue;

			final var propertyName = formatPropertyName(propertyDescriptor.getName(), propertyNameFormat);
			final var propertyValue = IuException.uncheckedInvocation(() -> readMethod.invoke(value));
			final var adapter = adapt.apply(readMethod.getGenericReturnType());
			IuJson.add(builder, propertyName, () -> propertyValue, (IuJsonAdapter) adapter);
		}

		return builder.build();
	}

}
