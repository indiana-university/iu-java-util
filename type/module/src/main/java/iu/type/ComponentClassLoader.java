package iu.type;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class ComponentClassLoader extends ClassLoader {

	private final Map<String, URL> resourcesByName;
	private final ModuleFinder moduleFinder;
	private final ModuleFinder dependencyModuleFinder;

	ComponentClassLoader(ModuleFinder moduleFinder, Component parent) {
		super(parent == null ? ClassLoader.getPlatformClassLoader() : parent.classLoader());

		Map<String, String> openPackagesToModuleName = new LinkedHashMap<>();
		Set<String> moduleNames = new LinkedHashSet<>();
		Consumer<ModuleDescriptor> mapModuleDescriptor = descriptor -> {
			var moduleName = descriptor.name();
			moduleNames.add(moduleName);
			for (var packageName : descriptor.packages())
				openPackagesToModuleName.put(packageName, moduleName);
		};

		var moduleIterator = moduleFinder.findAll().iterator();
		var componentModuleDescriptor = moduleIterator.next().descriptor();
		mapModuleDescriptor.accept(componentModuleDescriptor);

		while (moduleIterator.hasNext()) {
			var descriptor = moduleIterator.next().descriptor();
			mapModuleDescriptor.accept(descriptor);
			moduleNames.add(descriptor.name());
		}

		ClassLoader parentClassLoader;
		ModuleFinder parentModuleFinder;
		ModuleLayer parentModuleLayer;
		if (parent == null) {
			parentClassLoader = ClassLoader.getPlatformClassLoader();
			parentModuleFinder = ModuleFinder.ofSystem();
			parentModuleLayer = ModuleLayer.boot();
		} else {
			parentClassLoader = parent.classLoader();
			parentModuleFinder = parent.moduleFinder;
			parentModuleLayer = parent.moduleLayer;
		}

		var configuration = Configuration.resolveAndBind( //
				moduleFinder, List.of(parentModuleLayer.configuration()), ModuleFinder.of(), moduleNames);

		var controller = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(parentModuleLayer),
				parentClassLoader);

		// TODO Auto-generated constructor stub
		System.out.println(componentModuleDescriptor.name());
		System.out.println(openPackagesToModuleName);
		System.out.println(configuration);
		System.out.println(controller);

		throw new UnsupportedOperationException("TODO");
	}

}
