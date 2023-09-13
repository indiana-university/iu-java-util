package iu.type;

import java.lang.reflect.Type;

import edu.iu.type.IuType;

/**
 * Resolves {@link IuType} instances for {@link IuType#of(Type)}.
 */
public final class TypeFactory {

	/**
	 * Resolve an {@link IuType} instance.
	 * 
	 * @param type type to resolve
	 * @return {@link IuType} instance
	 */
	public static IuType<?> resolve(Type type) {
		// TODO: implement
		throw new UnsupportedOperationException("TODO");
	}

	private TypeFactory() {
	}

}
