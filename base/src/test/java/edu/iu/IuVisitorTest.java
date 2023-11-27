package edu.iu;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class IuVisitorTest {

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testEmpty() {
		final var visitor = new IuVisitor();
		final Function f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testVisitOne() {
		final var visitor = new IuVisitor();
		final var one = new Object();
		visitor.accept(one);
		final Function f = mock(Function.class);
		visitor.visit(f);
		verify(f).apply(one);
		verify(f).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testPicksOne() {
		final var visitor = new IuVisitor();
		final var one = new Object();
		visitor.accept(one);
		final Function f = mock(Function.class);
		when(f.apply(one)).thenReturn(Optional.empty());
		visitor.visit(f);
		verify(f).apply(one);
		verify(f, never()).apply(null);
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testPrunesClearedRefs() throws InterruptedException {
		final var visitor = new IuVisitor();
		visitor.accept(new Object());
		final Function f = mock(Function.class);
		System.gc();
		Thread.sleep(100L);
		visitor.visit(f);
		verify(f, never()).apply(notNull());
		verify(f).apply(null);
	}

}
