package iu.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import edu.iu.type.IuConstructor;
import edu.iu.type.IuField;
import edu.iu.type.IuMethod;
import edu.iu.type.IuProperty;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

class TypeFacade<T> implements IuType<T> {

	// IuType#of(Type): All fields MUST be final
	private final Type type;
	private final IuTypeReference<T> reference;

	TypeFacade(Type type) {
		this.type = type;
		this.reference = null;
	}

	@Override
	public IuTypeReference<?> reference() {
		return reference;
	}

	@Override
	public Type deref() {
		return type;
	}

	@Override
	public String name() {
		return baseClass().getName();
	}

	@Override
	public IuType<T> base() {
		if (reference() == null && (type instanceof Class))
			return this;
		else
			// TODO Auto-generated method stub
			throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuType<?> declaringType() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Map<Class<? extends Annotation>, ? extends Annotation> annotations() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Map<String, IuType<?>> typeParameters() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuType<?>> hierarchy() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuType<?> referTo(Type referentType) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuType<?>> enclosedTypes() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuConstructor<T>> constructors() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuConstructor<T> constructors(Type... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuConstructor<T> constructor(IuType<?>... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuField<T>> fields() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuField<?> field(String name) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuProperty<T>> properties() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuProperty<?> property(String name) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuMethod<?>> methods() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuMethod<?> methods(String name, Type... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuMethod<?> method(String name, IuType<?>... parameterTypes) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
