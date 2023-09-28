package iu.type;

import java.util.function.Supplier;

import edu.iu.type.IuResource;
import edu.iu.type.IuType;

class ComponentResource<T> implements IuResource<T> {
	
	private final boolean shared;
	private final String name;
	private final IuType<T> type;
	private final Supplier<T> factory;
	
	ComponentResource(boolean shared, String name, IuType<T> type, Supplier<T> factory) {
		this.shared = shared;
		this.name = name;
		this.type = type;
		this.factory = factory;
	}

	@Override
	public boolean shared() {
		return shared;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public IuType<T> type() {
		return type;
	}

	@Override
	public T get() {
		return factory.get();
	}

}
