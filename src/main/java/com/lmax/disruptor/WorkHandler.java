package com.lmax.disruptor;

/**
 * 回调接口，用于处理{@link RingBuffer}中可用的工作单元
 *
 * @param <T> 事件存储
 * @see WorkerPool
 */
public interface WorkHandler<T> {
	/**
	 * 回调以指示需要处理的工作单元
	 *
	 * @param event 发布到{@link RingBuffer}
	 * @throws Exception 如果{@link WorkHandler}希望在链上进一步处理异常。
	 */
	void onEvent(T event) throws Exception;
}
