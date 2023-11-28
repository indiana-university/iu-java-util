package iu.type;

import edu.iu.type.IuAttribute;

/**
 * Refines internal attribute return types.
 * 
 * @param <D> declaring type
 * @param <T> attribute type
 */
public interface DeclaredAttribute<D, T> extends IuAttribute<D, T> {

	@Override
	TypeFacade<?, D> declaringType();

	@Override
	TypeFacade<?, T> type();

}
