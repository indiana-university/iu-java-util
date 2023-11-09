package edu.iu;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Controls a bounded queue of potentially expensive and/or blocking
 * {@link Runnable runnable} tasks, typically that wait on another task to
 * complete asynchronously.
 * 
 * <p>
 * Once the limit has been reached, in order to put a new task on the tail of
 * the queue, the task at the head of the queue must be removed and
 * {@link Runnable#run}.
 * </p>
 * 
 * <p>
 * This class is not thread-safe; it is intended for use in single-threaded for
 * loop that feeds multiple tasks into a workload controller. For example, to
 * ensure no more than 10 tasks are running at the same time:
 * </p>
 * 
 * <pre>
 * final var limit = new RateLimit(10);
 * for (T task : tasks)
 * 	limit.accept(workload.async(task));
 * limit.run();
 * </pre>
 */
public class IuRateLimit implements Consumer<Runnable>, Runnable {

	private final int limit;
	private final Queue<Runnable> queue = new ArrayDeque<>();

	/**
	 * Constructor.
	 * 
	 * @param limit upper limit on the number of tasks that may be pending in the
	 *              queue.
	 */
	public IuRateLimit(int limit) {
		this.limit = limit;
	}

	/**
	 * Accept a task, blocking until enough tasks have run to create room in the
	 * queue.
	 */
	@Override
	public void accept(Runnable t) {
		while (queue.size() >= limit)
			queue.poll().run();
		queue.offer(t);
	}

	@Override
	public void run() {
		while (!queue.isEmpty())
			queue.poll().run();
	}

}
