package iu.type;

import java.util.Objects;

import edu.iu.IuObject;
import edu.iu.type.IuAnnotatedElement;
import edu.iu.type.IuReferenceKind;
import edu.iu.type.IuType;
import edu.iu.type.IuTypeReference;

class TypeReference<T, R extends IuAnnotatedElement> implements IuTypeReference<T, R> {

	private final IuReferenceKind kind;
	private final R referrer;
	private final IuType<T> referent;
	private final String name;
	private final int index;

	TypeReference(IuReferenceKind kind, R referrer, IuType<T> referent) {
		kind.referrerType().cast(Objects.requireNonNull(referrer));
		assert !kind.named() && !kind.indexed();
		this.kind = Objects.requireNonNull(kind);
		this.referrer = referrer;
		this.referent = Objects.requireNonNull(referent);
		this.name = null;
		this.index = -1;
	}

	TypeReference(IuReferenceKind kind, R referrer, IuType<T> referent, String name) {
		kind.referrerType().cast(Objects.requireNonNull(referrer));
		assert kind.named();
		this.kind = Objects.requireNonNull(kind);
		this.referrer = Objects.requireNonNull(referrer);
		this.referent = Objects.requireNonNull(referent);
		this.name = Objects.requireNonNull(name);
		this.index = -1;
	}

	TypeReference(IuReferenceKind kind, R referrer, IuType<T> referent, int index) {
		kind.referrerType().cast(Objects.requireNonNull(referrer));
		assert kind.indexed();
		assert index >= 0;
		this.kind = Objects.requireNonNull(kind);
		this.referrer = Objects.requireNonNull(referrer);
		this.referent = Objects.requireNonNull(referent);
		this.name = null;
		this.index = index;
	}

	@Override
	public IuReferenceKind kind() {
		return kind;
	}

	@Override
	public R referrer() {
		return referrer;
	}

	@Override
	public IuType<T> referent() {
		return referent;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public int index() {
		return index;
	}

	@Override
	public int hashCode() {
		return IuObject.hashCodeSuper(System.identityHashCode(referrer), index, kind, name, referent);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!IuObject.typeCheck(this, obj))
			return false;
		TypeReference<?, ?> other = (TypeReference<?, ?>) obj;
		return referrer == other.referrer //
				&& index == other.index //
				&& kind == other.kind //
				&& IuObject.equals(name, other.name) //
				&& IuObject.equals(referent, other.referent);
	}

}
