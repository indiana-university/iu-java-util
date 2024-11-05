package iu.auth.session;

import java.lang.reflect.Type;
import java.util.function.Function;

import edu.iu.client.IuJsonAdapter;
import edu.iu.client.IuJsonPropertyNameFormat;

/**
 * Session adapter factory.
 * 
 * @param <T> source type
 */
public class SessionAdapterFactory<T> implements Function<T, IuJsonAdapter<?>> {

	private final Class<?> baseType;

	/**
	 * Constructor
	 * 
	 * @param baseType base type
	 */
	public SessionAdapterFactory(Class<?> baseType) {
		this.baseType = baseType;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public IuJsonAdapter<?> apply(T t) {
		if (t instanceof Class) {
			final var c = (Class) t;
			if (c.isInterface() //
					&& c.getModule().isOpen(c.getPackageName(), baseType.getModule()))
				return IuJsonAdapter.from(c, IuJsonPropertyNameFormat.LOWER_CASE_WITH_UNDERSCORES, (Function) this);
		}

		return IuJsonAdapter.of((Type) t, (Function) this);
	}

}
