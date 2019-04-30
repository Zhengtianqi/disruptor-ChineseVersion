package com.lmax.disruptor;

/**
 * 
 * 由{@link RingBuffer}调用，以预先调用所有事件以填充RingBuffer。
 *
 * @param <T> 存储的事件以便在事件在交换或者并行协调期间进行共享
 */
public interface EventFactory<T> {
	/*
	 * 实例化一个事件对象，尽可能分配所有内存。
	 */
	T newInstance();
}