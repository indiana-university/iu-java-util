package iu.type.container;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.function.Supplier;

import edu.iu.IuException;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;
import iu.type.container.spi.IuEnvironment;

/**
 * Encapsulates a reference to a resource defined by {@link IuEnvironment}.
 * 
 * @param <T> resource type
 */
class EnvironmentResource<T> implements IuResource<T> {

	private final IuEnvironment env;
	private final String name;
	private final IuType<?, T> type;
	private final T defaultValue;

	/**
	 * Constructor.
	 * 
	 * @param env          {@link IuEnvironment}
	 * @param defaultValue default value
	 * @param name         resource name
	 * @param type         resource type
	 * 
	 */
	public EnvironmentResource(IuEnvironment env, String name, IuType<?, T> type, T defaultValue) {
		this.env = env;
		this.name = name;
		this.type = type;
		this.defaultValue = defaultValue;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public IuType<?, T> type() {
		return type;
	}

	@Override
	public boolean needsAuthentication() {
		return false;
	}

	@Override
	public boolean shared() {
		return true;
	}

	@Override
	public int priority() {
		return 0;
	}

	@Override
	public Supplier<?> factory() {
		return this::get;
	}

	@Override
	public void factory(Supplier<?> factory) {
		throw new UnsupportedOperationException();
	}

	@Override
	public T get() {
		final var check = new Consumer<String>() {
			T value;
			Throwable error;

			@Override
			public void accept(String name) {
				IuException.suppress(error, () -> value = env.resolve(name, type.erasedClass()));
			}
		};

		check.accept(name);
		if (check.value != null)
			return check.value;

		int slash = name.lastIndexOf('/');
		if (slash != -1) {
			final var base = name.substring(slash + 1);

			final Deque<String> prefixes = new ArrayDeque<>();
			prefixes.push("");

			final var context = name.substring(0, slash);
			slash = context.indexOf('/');
			while (slash != -1) {
				final var next = slash + 1;
				check.accept(name.substring(next));
				if (check.value != null)
					return check.value;

				prefixes.offer("/" + name.substring(0, next));
				slash = context.indexOf('/', next);
			}
			
			while (!prefixes.isEmpty()) {
				final var prefix = prefixes.pop();
				check.accept(prefix + base);
				if (check.value != null)
					return check.value;
			}
		}

		if (defaultValue != null)
			return defaultValue;

		throw new IllegalArgumentException("Missing environment entry for " + name + "!" + type.name(), check.error);
	}

}
