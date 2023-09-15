package iu.type;

import java.lang.module.ModuleFinder;

import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;

class Component implements IuComponent {

	final ModuleFinder moduleFinder;
	final ModuleLayer moduleLayer;

	Component(ModuleFinder moduleFinder, ModuleLayer moduleLayer) {
		this.moduleFinder = moduleFinder;
		this.moduleLayer = moduleLayer;
	}

	@Override
	public ClassLoader classLoader() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuType<?>> interfaces(Module module) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuType<?>> annotatedTypes(Module module) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public Iterable<IuResource<?>> resources(Module module) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

}
