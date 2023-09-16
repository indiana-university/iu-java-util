package iu.type;

import java.lang.ModuleLayer.Controller;
import java.lang.module.ModuleFinder;

import edu.iu.type.IuComponent;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;

class Component implements IuComponent {

	final Component parent;
	private final ModuleFinder moduleFinder;
	private final Controller controller;

	Component(Component parent, ModuleFinder moduleFinder, Controller controller) {
		this.parent = parent;
		this.moduleFinder = moduleFinder;
		this.controller = controller;
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
