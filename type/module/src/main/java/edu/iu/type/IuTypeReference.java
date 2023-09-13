package edu.iu.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Describes a reference to a generic type.
 * 
 * <p>
 * Each {@link IuType} instance either wraps a plain {@link Class} instance
 * ("base" type) or contains a reference back to the {@link Class},
 * {@link TypeVariable Type Parameter}, {@link Parameter}, {@link Constructor},
 * or {@link Method} that defined a generic and/or component {@link Type}.
 * </p>
 * 
 * @param <T> referent type
 */
public interface IuTypeReference<T> {

	/**
	 * Gets the reference kind.
	 * 
	 * @return reference kind
	 */
	IuReferenceKind getKind();

	/**
	 * Gets the introspection wrapper through which the reference was obtained.
	 * 
	 * @return introspection wrapper
	 */
	IuAnnotatedElement getReferrer();

	/**
	 * Gets the name of the referent type as known by the referrer.
	 * 
	 * @return reference name
	 */
	String getName();

	/**
	 * Gets the ordinal index associated with a parameter reference.
	 * 
	 * @return parameter index
	 */
	int getParameterIndex();

	/**
	 * Gets the referent type.
	 * 
	 * @return referent type
	 */
	IuType<T> getReferent();

}
