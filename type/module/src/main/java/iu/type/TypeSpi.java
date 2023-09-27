package iu.type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import edu.iu.type.IuComponent;
import edu.iu.type.IuType;
import edu.iu.type.spi.IuTypeSpi;

/**
 * Service provider implementation.
 * 
 * @see IuTypeSpi
 */
public class TypeSpi implements IuTypeSpi {

	@Override
	public IuType<?> resolveType(Type type) {
		// TODO implementation stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public IuComponent createComponent(InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException {
		return ComponentFactory.createComponent(componentArchiveSource, providedDependencyArchiveSources);
	}

}
