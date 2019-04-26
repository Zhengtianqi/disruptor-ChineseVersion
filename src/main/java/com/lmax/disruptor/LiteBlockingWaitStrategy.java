package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.lmax.disruptor.util.ThreadHints;

/**
 * {@link BlockingWaitStrategy}的变化 ，当锁无效时，试图消除有条件的唤醒。
 * 相比BlockingWaitStrategy，LiteBlockingWaitStrategy的实现方法也是阻塞等待，但它会减少一些不必要的唤醒。
 * 从源码的注释上看，这个策略在基准性能测试上是会表现出一些性能提升。这种等待策略应该被认为是实验性的，因为官方作者还没有完全证明锁定省略代码的正确性。
 */
public final class LiteBlockingWaitStrategy implements WaitStrategy {
	private final Lock lock = new ReentrantLock();
	private final Condition processorNotifyCondition = lock.newCondition();
	private final AtomicBoolean signalNeeded = new AtomicBoolean(false);

	@Override
	public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
			throws AlertException, InterruptedException {
		long availableSequence;
		if (cursorSequence.get() < sequence) {
			lock.lock();

			try {
				do {
					signalNeeded.getAndSet(true);

					if (cursorSequence.get() >= sequence) {
						break;
					}

					barrier.checkAlert();
					processorNotifyCondition.await();
				} while (cursorSequence.get() < sequence);
			} finally {
				lock.unlock();
			}
		}

		while ((availableSequence = dependentSequence.get()) < sequence) {
			barrier.checkAlert();
			ThreadHints.onSpinWait();
		}

		return availableSequence;
	}

	@Override
	public void signalAllWhenBlocking() {
		if (signalNeeded.getAndSet(false)) {
			lock.lock();
			try {
				processorNotifyCondition.signalAll();
			} finally {
				lock.unlock();
			}
		}
	}

	@Override
	public String toString() {
		return "LiteBlockingWaitStrategy{" + "processorNotifyCondition=" + processorNotifyCondition + '}';
	}
}
