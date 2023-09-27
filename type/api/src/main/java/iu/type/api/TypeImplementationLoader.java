package iu.type.api;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ServiceLoader;

import edu.iu.type.IuComponent;
import edu.iu.type.IuType;
import edu.iu.type.spi.IuTypeSpi;

/**
 * Loads a single static {@link IuTypeSpi} instance from the same
 * {@link ClassLoader} that defines {@link IuType} and uses it to delegate
 * access to {@link IuType} and {@link IuComponent} instances.
 */
public class TypeImplementationLoader {

	private static final IuTypeSpi SPI = ServiceLoader.load(IuTypeSpi.class, IuType.class.getClassLoader()).iterator()
			.next();

	/**
	 * Resolves an {@link IuType} instance for a generic type.
	 * 
	 * @param type Type
	 * @return Type introspection facade
	 * @see IuType#of(Type)
	 * @see IuType#of(Class)
	 */
	public static IuType<?> resolve(Type type) {
		return SPI.resolveType(type);
	}

	/**
	 * Implements {@link IuComponent#of(InputStream, InputStream...)}.
	 * 
	 * @param componentArchiveSource           component archive
	 * @param providedDependencyArchiveSources provided dependency archives
	 * @return {@link IuComponent} instance
	 * @throws IOException If the <strong>component archive</strong> or any
	 *                     <strong>dependency archives</strong> are unreadable.
	 * 
	 * @see IuComponent
	 */
	public static IuComponent create(InputStream componentArchiveSource,
			InputStream... providedDependencyArchiveSources) throws IOException {
		return SPI.createComponent(componentArchiveSource, providedDependencyArchiveSources);
	}

	private TypeImplementationLoader() {
	}
}
