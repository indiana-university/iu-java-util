package iu.type;

import edu.iu.type.IuAnnotatedElement;

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

	private StaticDependencyHelper() {
	}

}
