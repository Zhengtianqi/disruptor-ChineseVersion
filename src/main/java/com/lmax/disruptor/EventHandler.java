package com.lmax.disruptor;

/**
 * 回调接口，用于处理{@link RingBuffer}中可用的事件
 * 
 * @param <T> 事件实现存储数据以便在事件的交换或并行协调期间进行共享
 * @see 处理从handler传播出去的异常：BatchEventProcessor#setExceptionHandler(ExceptionHandler)
 */
public interface EventHandler<T> {
	/**
	 * 当生产者把事件发布到{@link RingBuffer}时调用。 {@link BatchEventProcessor}从
	 * {@link RingBuffer}的批次中读信息，其中一个批处理是所有可处理的事件，而不必等待任何新事件到达。
	 * 这对于需要执行更慢操作（如I/O）的事件处理程序很有用，因为它们可以将来自多个事件的数据分组为一个操作。
	 * 实现应该确保在endOfBatch为true时始终执行操作，因为该消息与下一条消息之间的时间不确定
	 *
	 * @param event      发布到{@link RingBuffer}
	 * @param sequence   正在处理的事件
	 * @param endOfBatch 用于指示是否来自{@link RingBuffer}的批处理中的最后一个时间的标志
	 * @throws Exception 如果事件处理程序希望在链上进一步处理异常
	 */
	void onEvent(T event, long sequence, boolean endOfBatch) throws Exception;
}
