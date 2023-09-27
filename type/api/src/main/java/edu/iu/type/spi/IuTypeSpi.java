package edu.iu.type.spi;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import edu.iu.type.IuComponent;
import edu.iu.type.IuType;

/**
 * Implementation service provider interface.
 */
public interface IuTypeSpi {

	/**
	 * Resolves an {@link IuType} instance for a generic type.
	 * 
	 * @param type Type
	 * @return Type introspection facade
	 * @see IuType#of(Type)
	 * @see IuType#of(Class)
	 */
	IuType<?> resolveType(Type type);

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
	IuComponent createComponent(InputStream componentArchiveSource, InputStream... providedDependencyArchiveSources)
			throws IOException;

}
