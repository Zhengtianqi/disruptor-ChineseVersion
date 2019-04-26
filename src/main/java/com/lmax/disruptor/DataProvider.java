package com.lmax.disruptor;

/**
 * DataProvider 提供了根据序列获取对应的对象有两个地方调用。
 * 这个Event对象需要被生产者获取往里面填充数据。第二个是在消费时，获取这个Event对象用于消费
 * 
 * @param <T>
 */
public interface DataProvider<T> {
	T get(long sequence);
}
