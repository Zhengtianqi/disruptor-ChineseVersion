package com.lmax.disruptor;

/**
 * 
 * SequenceBarrier由Sequencer生成并且包含了已经发布的Sequence的引用这些Sequence源于Sequencer和一些独立的消费者的Sequence。
 * 它包含了决定是否有供消费者消费的Event的逻辑。用来权衡当消费者无法从RingBuffer里面获取事件时的处理策略。
 * 例如当生产者太慢消费者太快会导致消费者获取不到新的事件会根据该策略进行处理默认会堵塞
 * 
 * SequenceBarrier接口 消费者使用
 * 
 */
public interface SequenceBarrier {
	/**
	 *
	 * 等待一个序列变为可用，然后消费这个序列。是给事件处理者使用的。
	 * 
	 * @param sequence 等待序列
	 * @return 可用序列
	 * @throws AlertException       如果Disruptor状态发生看变化
	 * @throws InterruptedException 如果线程需要在条件变化时上唤醒。
	 * @throws TimeoutException     如果在等待提供的序列时发生超时。
	 */
	long waitFor(long sequence) throws AlertException, InterruptedException, TimeoutException;

	/**
	 * 
	 * 返回当前可读的游标（一个序号）
	 * 
	 * @return 已发布的entries的游标值。
	 */
	long getCursor();

	/**
	 * 
	 * 当前栅栏是否发过通知。
	 *
	 * @return .如果处于发通知（alert）状态，则为true
	 */
	boolean isAlerted();

	/**
	 * 
	 * 通知事件处理器状态发生改变，并保持这个状态直到被清除
	 * 
	 */
	void alert();

	/**
	 * 
	 * 清除当前通知状态
	 * 
	 */
	void clearAlert();

	/**
	 *
	 * 检测是否发生了通知，如果已经发生了抛出AlertException异常 ？
	 *
	 * @throws AlertException 如果通知已经被唤起
	 */
	void checkAlert() throws AlertException;
}
