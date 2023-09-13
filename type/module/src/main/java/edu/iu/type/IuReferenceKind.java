package edu.iu.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

/**
 * Describes the kind of reference used to refer to a generic type.
 */
public enum IuReferenceKind {

	/**
	 * The type referred to is a {@link Class}.
	 */
	BASE(null, false, false),

	/**
	 * The type referred to is a generic abstract class or interface in the referent
	 * type's hierarchy.
	 */
	SUPER(IuType.class, false, false),

	/**
	 * The type was referred to by {@link ParameterizedType#getRawType()}.
	 */
	RAW(IuType.class, false, false),

	/**
	 * The type was referred to by {@link GenericDeclaration#getTypeParameters()}
	 * from a {@link ParameterizedType}.
	 */
	TYPE_PARAM(IuType.class, true, false),

	/**
	 * The type was referred to by {@link GenericDeclaration#getTypeParameters()}
	 * from a {@link Method}.
	 */
	METHOD_PARAM(IuMethod.class, true, false),

	/**
	 * The type was referred to by {@link GenericDeclaration#getTypeParameters()}
	 * from a {@link Constructor}.
	 */
	CONSTRUCTOR_PARAM(IuConstructor.class, true, false),

	/**
	 * The type was referred to by {@link TypeVariable#getBounds()}.
	 */
	BOUNDS(IuType.class, false, true),

	/**
	 * The type was referred to by {@link WildcardType#getUpperBounds()}.
	 */
	UPPER_BOUNDS(IuType.class, false, true),

	/**
	 * The type was referred to by {@link WildcardType#getLowerBounds()}.
	 */
	LOWER_BOUNDS(IuType.class, false, true),

	/**
	 * The type was referred to by {@link Class#getComponentType()} or
	 * {@link GenericArrayType#getGenericComponentType()}.
	 */
	COMPONENT_TYPE(IuType.class, false, false),

	/**
	 * The type was referred to by {@link Field#getGenericType()}.
	 */
	FIELD(IuField.class, true, false),

	/**
	 * The type was referred to by {@link Method#getGenericReturnType()} on a
	 * property read method.
	 */
	PROPERTY(IuProperty.class, true, false),

	/**
	 * The type was referred to by {@link Executable#getGenericParameterTypes()}.
	 */
	PARAMETER(IuExecutable.class, false, true),

	/**
	 * The type was referred to by {@link Method#getGenericReturnType()}.
	 */
	RETURN_TYPE(IuMethod.class, true, false);

	private final Class<? extends IuAnnotatedElement> referrerType;
	private final boolean named;
	private final boolean indexed;

	private IuReferenceKind(Class<? extends IuAnnotatedElement> referrerType, boolean named, boolean indexed) {
		this.referrerType = referrerType;
		this.named = named;
		this.indexed = indexed;
	}

	/**
	 * Gets the type of the referrer.
	 * 
	 * @return referrer type; may be null if the reference is a base type.
	 */
	public Class<? extends IuAnnotatedElement> getReferrerType() {
		return referrerType;
	}

	/**
	 * Determines if the reference was obtained by name.
	 * 
	 * <p>
	 * Only only of {@link #isNamed()} and {@link #isIndexed()} may return true,
	 * both may return false.
	 * </p>
	 * 
	 * @return true if the reference is a name; else false.
	 */
	public boolean isNamed() {
		return named;
	}

	/**
	 * Determines if the reference was obtained by index.
	 * 
	 * <p>
	 * Only only of {@link #isNamed()} and {@link #isIndexed()} may return true,
	 * both may return false.
	 * </p>
	 * 
	 * @return true if the reference is an index; else false.
	 */
	public boolean isIndexed() {
		return indexed;
	}

}
