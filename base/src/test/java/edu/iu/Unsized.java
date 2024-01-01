package edu.iu;

import java.util.Spliterator;
import java.util.function.Consumer;

@SuppressWarnings("javadoc")
class Unsized<T> implements Spliterator<T> {
	private final Spliterator<T> sp;

	Unsized(Spliterator<T> sp) {
		super();
		this.sp = sp;
	}

	@Override
	public boolean tryAdvance(Consumer<? super T> action) {
		return sp.tryAdvance(action);
	}

	@Override
	public Spliterator<T> trySplit() {
		final var split = sp.trySplit();
		if (split == null)
			return null;
		else
			return new Unsized<>(split);
	}

	@Override
	public long estimateSize() {
		return Long.MAX_VALUE;
	}

	@Override
	public int characteristics() {
		return IMMUTABLE;
	}
}
