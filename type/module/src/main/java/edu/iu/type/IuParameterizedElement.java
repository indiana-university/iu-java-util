package edu.iu.type;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

/**
 * Facade interface for an element that defines type parameters.
 * 
 * @see ParameterizedType
 * @see GenericDeclaration
 */
public interface IuParameterizedElement {

	/**
	 * Gets type parameters by name.
	 * 
	 * @return map of type parameter facades
	 */
	Map<String, IuType<?>> typeParameters();

	/**
	 * Gets a type parameter by name.
	 * 
	 * @param name parameter name
	 * @return type parameter facade
	 */
	default IuType<?> typeParameter(String name) {
		return typeParameters().get(name);
	}

}
