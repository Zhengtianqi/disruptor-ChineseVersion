package com.lmax.disruptor.dsl;

import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;

import java.util.concurrent.Executor;

/**
 * ConsumerInfo就相当于事件消费者信息和序列栅栏的包装类 ConsumerInfo本身是一个接口，针对Event模式和Work模式
 * 提供了两种实现：@{link EventProcessorInfo}和@{link WorkerPoolInfo}
 */
interface ConsumerInfo {
	Sequence[] getSequences();

	SequenceBarrier getBarrier();

	boolean isEndOfChain();

	void start(Executor executor);

	void halt();

	void markAsUsedInBarrier();

	boolean isRunning();
}
