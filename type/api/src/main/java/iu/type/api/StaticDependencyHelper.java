/*
 * Copyright © 2023 Indiana University
 * All rights reserved.
 *
 * BSD 3-Clause License
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * 
 * - Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package iu.type.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuResource;
import edu.iu.type.IuType;
import jakarta.annotation.Resource;

/**
 * Isolates references static module dependencies to prevent
 * {@link NoClassDefFoundError} when the Jakarta Annotations API is not present
 * in the classpath.
 */
public class StaticDependencyHelper {

	private static final boolean ANNOTATION_SUPPORTED;

	static {
		boolean annotationSupported;
		try {
			@jakarta.annotation.Resource
			class HasResource {
			}
			annotationSupported = new HasResource().getClass().isAnnotationPresent(jakarta.annotation.Resource.class);
		} catch (NoClassDefFoundError e) {
			annotationSupported = false;
		}
		ANNOTATION_SUPPORTED = annotationSupported;
	}

	private static class NullAdaptedResource implements IuResource<Object> {

		private final jakarta.annotation.Resource resource;

		private NullAdaptedResource(Resource resource) {
			this.resource = resource;
		}

		@Override
		public boolean needsAuthentication() {
			return resource.authenticationType().equals(jakarta.annotation.Resource.AuthenticationType.CONTAINER);
		}

		@Override
		public boolean shared() {
			return resource.shareable();
		}

		@Override
		public String name() {
			return resource.name();
		}

		@Override
		public IuType<Object> type() {
			return IuType.of(resource.type());
		}

		@Override
		public Object get() {
			return null;
		}
	}

	/**
	 * Check to see if the Jakarta Annotations API is present in the classpath.
	 * 
	 * @return true if present; false if missing
	 */
	public static boolean isAnnotationSupported() {
		return ANNOTATION_SUPPORTED;
	}

	/**
	 * Determines whether or not access to an annotated element contains the
	 * {@link jakarta.annotation.security.PermitAll} annotation.
	 * 
	 * @param annotatedElement annotated element
	 * @return true if the element contains the
	 *         {@link jakarta.annotation.security.PermitAll} annotation.
	 */
	public static boolean hasPermitAll(IuAnnotatedElement annotatedElement) {
		return isAnnotationSupported() && annotatedElement.hasAnnotation(jakarta.annotation.security.PermitAll.class);
	}

	/**
	 * Returns a set of null-adapted resource ({@link IuResource#get()} returns
	 * null, with no translation for blank name or {@link Object} type defaults)
	 * instances describing all @Resource annotation on a type, if present.
	 * 
	 * @param type Type to inspect
	 * @return Set of null-adapted resources
	 */
	public static Set<IuResource<?>> getResources(Class<?> type) {
		if (!isAnnotationSupported())
			return Collections.emptySet();

		Set<IuResource<?>> resourceSet = new LinkedHashSet<>();
		var resources = type.getAnnotation(jakarta.annotation.Resources.class);
		if (resources != null)
			for (var resource : resources.value())
				resourceSet.add(new NullAdaptedResource(resource));

		var resource = type.getAnnotation(jakarta.annotation.Resource.class);
		if (resource != null)
			resourceSet.add(new NullAdaptedResource(resource));

		return resourceSet;
	}

	private StaticDependencyHelper() {
	}

}
