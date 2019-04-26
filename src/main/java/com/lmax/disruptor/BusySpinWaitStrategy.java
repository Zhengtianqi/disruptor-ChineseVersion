package com.lmax.disruptor;

import com.lmax.disruptor.util.ThreadHints;

/**
 * BusySpinWaitStrategy的实现方法是自旋等待。这种策略会利用CPU资源来避免系统调用带来的延迟抖动，当线程可以绑定到指定CPU(核)的时候，最好使用这个策略。
 */
public final class BusySpinWaitStrategy implements WaitStrategy {
	@Override
	public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence,
			final SequenceBarrier barrier) throws AlertException, InterruptedException {
		long availableSequence;

		while ((availableSequence = dependentSequence.get()) < sequence) {
			barrier.checkAlert();
			ThreadHints.onSpinWait();
		}

		return availableSequence;
	}

	@Override
	public void signalAllWhenBlocking() {
	}
}
