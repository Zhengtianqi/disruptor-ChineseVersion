package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 
 * 1.首先，由于是Work模式，必然是多个事件处理者(WorkProcessor)处理同一批事件，那么肯定会存在多个处理者对同一个要处理事件的竞争，所以出现了一个workSequence，所有的处理者都使用这一个workSequence，大家通过对workSequence的原子操作来保证不会处理相同的事件。
 * 2.其次，多个事件处理者和事件发布者之间也需要协调，需要等待事件发布者发布完事件之后才能对其进行处理，这里还是使用序列栅栏来协调(sequenceBarrier.waitFor)。
 * 
 * {@link WorkProcessor}包装了一个{@link WorkHandler}，有效地消耗了sequence并确保了适当的barriers。
 *
 * <p>
 * 通常，这将用作{@link WorkerPool}的一部分。
 * </p>
 *
 * @param <T> 实现存储事件的详细信息
 */
public final class WorkProcessor<T> implements EventProcessor {
	// 运行状态标识
	private final AtomicBoolean running = new AtomicBoolean(false);
	// 工作序列
	private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
	// 事件队列
	private final RingBuffer<T> ringBuffer;
	private final SequenceBarrier sequenceBarrier;
	private final WorkHandler<? super T> workHandler;
	private final ExceptionHandler<? super T> exceptionHandler;
	private final Sequence workSequence;

	private final EventReleaser eventReleaser = new EventReleaser() {
		@Override
		public void release() {
			sequence.set(Long.MAX_VALUE);
		}
	};

	private final TimeoutHandler timeoutHandler;

	/**
	 * {@link WorkProcessor} 构造函数
	 *
	 * @param ringBuffer       发布哪些事件。
	 * @param sequenceBarrier  等待它
	 * @param workHandler      是调度事件的委托
	 * @param exceptionHandler 发生错误时回调
	 * @param workSequence     从中宣称要开展的下一个活动。 It should always be initialised as
	 *                         {@link Sequencer#INITIAL_CURSOR_VALUE}
	 */
	public WorkProcessor(final RingBuffer<T> ringBuffer, final SequenceBarrier sequenceBarrier,
			final WorkHandler<? super T> workHandler, final ExceptionHandler<? super T> exceptionHandler,
			final Sequence workSequence) {
		this.ringBuffer = ringBuffer;
		this.sequenceBarrier = sequenceBarrier;
		this.workHandler = workHandler;
		this.exceptionHandler = exceptionHandler;
		this.workSequence = workSequence;

		if (this.workHandler instanceof EventReleaseAware) {
			((EventReleaseAware) this.workHandler).setEventReleaser(eventReleaser);
		}

		timeoutHandler = (workHandler instanceof TimeoutHandler) ? (TimeoutHandler) workHandler : null;
	}

	@Override
	public Sequence getSequence() {
		return sequence;
	}

	@Override
	public void halt() {
		running.set(false);
		sequenceBarrier.alert();
	}

	@Override
	public boolean isRunning() {
		return running.get();
	}

	/**
	 * 在halt()之后让另一个线程重新运行，此方法是可以的。
	 *
	 * @throws IllegalStateException 如果此消费者已在运行
	 */
	@Override
	public void run() {
		// 判断线程是否启动，状态设置与检测
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException("Thread is already running");
		}
		// 先清除序列栅栏的通知状态
		sequenceBarrier.clearAlert();

		// 如果workHandler实现了LifecycleAware，这里会对其进行一个启动通知
		notifyStart();

		// 事件处理标志
		boolean processedSequence = true;

		long cachedAvailableSequence = Long.MIN_VALUE;
		long nextSequence = sequence.get();
		T event = null;
		while (true) {
			try {
				// 判断上一个事件是否已经处理完毕。
				if (processedSequence) {
					// 如果处理完毕，重置标识为false
					processedSequence = false;
					do {
						// 原子的获取下一要处理事件的序列值
						nextSequence = workSequence.get() + 1L;

						// 更新当前已经处理到的
						sequence.set(nextSequence - 1L);
					}
					// 多个WorkProcessor共享一个workSequence，可以实现互斥消费，因为只有一个线程可以CAS更新成功
					while (!workSequence.compareAndSet(nextSequence - 1L, nextSequence));
				}

				// 检查序列值是否需要申请。这一步是为了防止和事件生产者冲突
				if (cachedAvailableSequence >= nextSequence) {
					// 从RingBuffer上获取事件
					event = ringBuffer.get(nextSequence);

					// 委托给workHandler处理事件
					workHandler.onEvent(event);

					// 设置事件处理完成标识
					processedSequence = true;
				} else {
					// 如果需要申请，通过序列栅栏来申请可用的序列
					cachedAvailableSequence = sequenceBarrier.waitFor(nextSequence);
				}
			} catch (final TimeoutException e) {
				notifyTimeout(sequence.get());
			} catch (final AlertException ex) {
				// 处理通知。
				if (!running.get()) {
					// 如果当前处理器被停止，那么退出主循环
					break;
				}
			} catch (final Throwable ex) {
				// 处理异常
				exceptionHandler.handleEventException(ex, nextSequence, event);
				// 如果异常处理器不抛出异常的话，就认为事件处理完毕，设置事件处理完成标识。
				processedSequence = true;
			}
		}

		// 退出主循环后，如果workHandler实现了LifecycleAware，这里会对其进行一个关闭通知
		notifyShutdown();

		// 设置当前处理器状态为停止
		running.set(false);
	}

	private void notifyTimeout(final long availableSequence) {
		try {
			if (timeoutHandler != null) {
				timeoutHandler.onTimeout(availableSequence);
			}
		} catch (Throwable e) {
			exceptionHandler.handleEventException(e, availableSequence, null);
		}
	}

	private void notifyStart() {
		if (workHandler instanceof LifecycleAware) {
			try {
				((LifecycleAware) workHandler).onStart();
			} catch (final Throwable ex) {
				exceptionHandler.handleOnStartException(ex);
			}
		}
	}

	private void notifyShutdown() {
		if (workHandler instanceof LifecycleAware) {
			try {
				((LifecycleAware) workHandler).onShutdown();
			} catch (final Throwable ex) {
				exceptionHandler.handleOnShutdownException(ex);
			}
		}
	}
}
