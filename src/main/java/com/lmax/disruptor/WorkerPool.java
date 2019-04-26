package com.lmax.disruptor;

import com.lmax.disruptor.util.Util;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WorkerPool是Work模式下的消费者池，它起到了维护消费者生命周期、关联消费者与事件队列(RingBuffer)、提供工作序列(WorkProcessor多个处理器需要使用统一的workSequence)的作用
 * 1：多个WorkProcessor组成一个WorkerPool。 2：维护workSequence事件处理者处理的序列。
 * 
 * WorkerPool包含一个{@link WorkProcessor}池，它将使用序列，因此可以在一个工作池中进行工作。
 * 每个{@link WorkProcessor}都管理并调用{@link WorkHandler}来处理事件
 *
 * @param <T> 由一群工人处理的事件
 */
public final class WorkerPool<T> {
	// 运行状态标识
	private final AtomicBoolean started = new AtomicBoolean(false);
	// 工作序列
	private final Sequence workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
	// 事件队列
	private final RingBuffer<T> ringBuffer;
	// 消费者数组
	private final WorkProcessor<?>[] workProcessors;

	/**
	 * 创建一个工作池以使{@link WorkHandler}数组能够使用已发布的序列。
	 * <p>
	 * 此选项需要预先配置的{@link RingBuffer}，它必须在工作池启动之前调用{@link RingBuffer#addGatingSequences(Sequence...)}
	 *
	 * @param ringBuffer       要消耗的事件
	 * @param sequenceBarrier  worker将依赖什么时间
	 * @param exceptionHandler 发生错误时回调，而不是由错误处理 {@link WorkHandler}
	 * @param workHandlers     分配工作量
	 */
	@SafeVarargs
	public WorkerPool(final RingBuffer<T> ringBuffer, final SequenceBarrier sequenceBarrier,
			final ExceptionHandler<? super T> exceptionHandler, final WorkHandler<? super T>... workHandlers) {
		this.ringBuffer = ringBuffer;
		final int numWorkers = workHandlers.length;
		workProcessors = new WorkProcessor[numWorkers];

		for (int i = 0; i < numWorkers; i++) {
			workProcessors[i] = new WorkProcessor<>(ringBuffer, sequenceBarrier, workHandlers[i], exceptionHandler,
					workSequence);
		}
	}

	/**
	 * 为方便起见，使用内部{@link RingBuffer}构建工作池。
	 * <p>
	 * 此选项不需要在启动线程池之前使用 {@link RingBuffer#addGatingSequences(Sequence...)}
	 * ，在工作池启动之前调用
	 *
	 * @param eventFactory     填充{@link RingBuffer}
	 * @param exceptionHandler 发生错误时回调，但{@link WorkHandler}未处理。
	 * @param workHandlers     分配工作量
	 */
	@SafeVarargs
	public WorkerPool(final EventFactory<T> eventFactory, final ExceptionHandler<? super T> exceptionHandler,
			final WorkHandler<? super T>... workHandlers) {
		ringBuffer = RingBuffer.createMultiProducer(eventFactory, 1024, new BlockingWaitStrategy());
		final SequenceBarrier barrier = ringBuffer.newBarrier();
		final int numWorkers = workHandlers.length;
		workProcessors = new WorkProcessor[numWorkers];

		for (int i = 0; i < numWorkers; i++) {
			workProcessors[i] = new WorkProcessor<>(ringBuffer, barrier, workHandlers[i], exceptionHandler,
					workSequence);
		}

		ringBuffer.addGatingSequences(getWorkerSequences());
	}

	/**
	 * 
	 * 通过WorkerPool是可以获取内部消费者各自的序列和当前的WorkSequence，用于观察事件处理进度。
	 * 
	 * @return 一系列代表workers进度的{@link Sequence}数组
	 */
	public Sequence[] getWorkerSequences() {
		final Sequence[] sequences = new Sequence[workProcessors.length + 1];
		for (int i = 0, size = workProcessors.length; i < size; i++) {
			sequences[i] = workProcessors[i].getSequence();
		}
		sequences[sequences.length - 1] = workSequence;

		return sequences;
	}

	/**
	 * 
	 * start方法里面会初始化工作序列，然后使用一个给定的执行器(线程池)来执行内部的消费者。
	 * 
	 * @param executor 提供运行工作者的线程
	 * @return {@link RingBuffer}用于工作队列。
	 * @throws IllegalStateException 如果池已经启动但尚未停止
	 */
	public RingBuffer<T> start(final Executor executor) {
		if (!started.compareAndSet(false, true)) {
			throw new IllegalStateException(
					"WorkerPool has already been started and cannot be restarted until halted.");
		}

		final long cursor = ringBuffer.getCursor();
		workSequence.set(cursor);

		for (WorkProcessor<?> processor : workProcessors) {
			processor.getSequence().set(cursor);
			executor.execute(processor);
		}

		return ringBuffer;
	}

	/**
	 * drainAndHalt方法会将{@link RingBuffer}中所有的事件取出，执行完毕后，然后停止当前WorkerPool
	 */
	public void drainAndHalt() {
		Sequence[] workerSequences = getWorkerSequences();
		while (ringBuffer.getCursor() > Util.getMinimumSequence(workerSequences)) {
			Thread.yield();
		}

		for (WorkProcessor<?> processor : workProcessors) {
			processor.halt();
		}

		started.set(false);
	}

	/**
	 * 在当前周期结束时立即停止所有workers,即马上停止当前WorkerPool。
	 */
	public void halt() {
		for (WorkProcessor<?> processor : workProcessors) {
			processor.halt();
		}

		started.set(false);
	}

	/**
	 * 获取当前WorkerPool运行状态
	 */
	public boolean isRunning() {
		return started.get();
	}
}
