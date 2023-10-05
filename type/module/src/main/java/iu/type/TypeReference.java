package iu.type;

import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

record TypeReference<T, R extends IuAnnotatedElement>(IuReferenceKind kind, R referrer, IuType<T> referent, String name, int index)
		implements IuTypeReference<T, R> {
}
