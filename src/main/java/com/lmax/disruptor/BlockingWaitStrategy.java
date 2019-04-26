package com.lmax.disruptor;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.lmax.disruptor.util.ThreadHints;

/**
 * BlockingWaitStrategy的实现方法是阻塞等待。当要求节省CPU资源，而不要求高吞吐量和低延迟的时候使用这个策略
 * 阻止策略使用锁和条件变量来等待障碍的{@link EventProcessor}
 */
public final class BlockingWaitStrategy implements WaitStrategy {
	private final Lock lock = new ReentrantLock();
	private final Condition processorNotifyCondition = lock.newCondition();

	@Override
	public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
			throws AlertException, InterruptedException {
		long availableSequence;
		if (cursorSequence.get() < sequence) {
			// 如果RingBuffer上当前可用的序列值小于要申请的序列值。
			lock.lock();
			try {
				// 再次检测
				while (cursorSequence.get() < sequence) {
					// 检查序列栅栏状态(事件处理器是否被关闭)
					barrier.checkAlert();
					// 当前线程在processorNotifyCondition条件上等待
					processorNotifyCondition.await();
				}
			} finally {
				lock.unlock();
			}
		}
		// 再次检测，避免事件处理器关闭的情况
		while ((availableSequence = dependentSequence.get()) < sequence) {
			barrier.checkAlert();
			ThreadHints.onSpinWait();
		}

		return availableSequence;
	}

	@Override
	public void signalAllWhenBlocking() {
		lock.lock();
		try {
			// 唤醒在processorNotifyCondition条件上等待的处理事件线程
			processorNotifyCondition.signalAll();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		return "BlockingWaitStrategy{" + "processorNotifyCondition=" + processorNotifyCondition + '}';
	}
}
