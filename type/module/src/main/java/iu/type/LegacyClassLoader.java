package iu.type;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

class LegacyClassLoader extends URLClassLoader {

	private final Set<String> endorsed;

	LegacyClassLoader(Set<String> endorsed, URL[] classpath, LegacyClassLoader parent) {
		super(classpath, parent);
		this.endorsed = endorsed;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> rv = this.findLoadedClass(name);
			if (rv != null)
				return rv;

			if (endorsed.contains(name)) {
				rv = findClass(name);
				if (resolve)
					resolveClass(rv);
			} else
				rv = super.loadClass(name, resolve);

			return rv;
		}
	}

}
