package com.lmax.disruptor;

/**
 * Yielding 策略：在自旋100次尝试后，让出cpu资源，等待下次cpu调度后再行尝试。
 * 
 * 这个策略会100%消耗CPU，如果其他线程需要CPU资源，但是比忙碌旋转策略（busy spin strategy）更容易放弃CPU
 * 该策略在高性能与CPU资源之间取舍的折中方案，这个策略不会带来显著的延迟抖动。
 */
public final class YieldingWaitStrategy implements WaitStrategy {
	private static final int SPIN_TRIES = 100;

	/**
	 * 自旋100次后，线程放弃cpu资源
	 */
	@Override
	public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence,
			final SequenceBarrier barrier) throws AlertException, InterruptedException {
		long availableSequence;
		int counter = SPIN_TRIES;

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

		if (0 == counter) {
			Thread.yield();
		} else {
			--counter;
		}

		return counter;
	}
}
