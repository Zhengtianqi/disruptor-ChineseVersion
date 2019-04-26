package com.lmax.disruptor.dsl;

/**
 * 定义生产者类型,用来创建RingBuffer
 */
public enum ProducerType {
	/**
	 * 单个事件生产者创建一个RingBuffer
	 */
	SINGLE,

	/**
	 * 多个事件生产者创建一个RingBuffer
	 */
	MULTI
}
