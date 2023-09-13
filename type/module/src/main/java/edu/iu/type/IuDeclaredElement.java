package edu.iu.type;

import java.lang.reflect.Member;

/**
 * Facade interface for an element declared by a type: a {@link Member}, bean
 * property, or enclosed {@link Class}.
 */
public interface IuDeclaredElement extends IuAnnotatedElement {

	/**
	 * Gets the declaring type.
	 * 
	 * @return declaring type
	 */
	IuType<?> declaringType();

}
