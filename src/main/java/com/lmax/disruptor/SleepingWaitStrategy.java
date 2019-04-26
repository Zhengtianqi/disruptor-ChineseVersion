package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

/**
 * SleepingWaitStrategy的实现方法是先自旋，不行再临时让出调度(Thread.yield())，不行再短暂的阻塞等待。
 * 对于既想取得高性能，由不想太浪费CPU资源的场景，这个策略是一种比较好的折中方案。
 */
public final class SleepingWaitStrategy implements WaitStrategy {
	private static final int DEFAULT_RETRIES = 200;
	private static final long DEFAULT_SLEEP = 100;

	private final int retries;
	private final long sleepTimeNs;

	public SleepingWaitStrategy() {
		this(DEFAULT_RETRIES, DEFAULT_SLEEP);
	}

	public SleepingWaitStrategy(int retries) {
		this(retries, DEFAULT_SLEEP);
	}

	public SleepingWaitStrategy(int retries, long sleepTimeNs) {
		this.retries = retries;
		this.sleepTimeNs = sleepTimeNs;
	}

	@Override
	public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence,
			final SequenceBarrier barrier) throws AlertException {
		long availableSequence;
		int counter = retries;

		while ((availableSequence = dependentSequence.get()) < sequence) {
			counter = applyWaitMethod(barrier, counter);
		}

		return availableSequence;
	}

	@Override
	public void signalAllWhenBlocking() {
	}

	private int applyWaitMethod(final SequenceBarrier barrier, int counter) throws AlertException {
		barrier.checkAlert();
		// 从指定的重试次数(默认是200)重试到剩下100次，这个过程是自旋
		if (counter > 100) {
			--counter;
		}
		// 然后尝试100次让出处理器动作
		else if (counter > 0) {
			--counter;
			Thread.yield();
		}
		// 然后尝试阻塞1纳秒
		else {
			LockSupport.parkNanos(sleepTimeNs);
		}

		return counter;
	}
}
