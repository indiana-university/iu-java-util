package iu.type;

import java.util.Map;

import edu.iu.type.IuParameterizedElement;

/**
 * Implementation replacement for {@link IuParameterizedElement} to enforce all
 * type parameters to be implemented by {@link TypeFacade}.
 */
interface ParameterizedFacade extends IuParameterizedElement {

	@Override
	Map<String, TypeFacade<?, ?>> typeParameters();

	@Override
	default TypeFacade<?, ?> typeParameter(String name) {
		return typeParameters().get(name);
	}

}
