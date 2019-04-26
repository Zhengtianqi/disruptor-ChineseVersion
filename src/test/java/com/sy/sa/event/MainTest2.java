package com.sy.sa.event;

import com.lmax.disruptor.RingBuffer;

/**
 *  可见，我们已经成功了发布了一个事件到RingBuffer，由于是从序列0开始发布，所以我们从序列0可以读出这个数据。因为只发布了一个，所以序列1上还是没有数据。
 *  
 * @data 2019年4月23日 上午11:57:12
 * @author ztq
 **/
public class MainTest2 {
	public static void main(String[] args) {
		RingBuffer<MyDataEvent> ringBuffer = RingBuffer.createSingleProducer(new MyDataEventFactory(), 1024);
		// 发布事件!!!
		ringBuffer.publishEvent(new MyDataEventTranslator());

		MyDataEvent dataEvent0 = ringBuffer.get(0);
		System.out.println("Event = " + dataEvent0);
		System.out.println("Data = " + dataEvent0.getData());
		MyDataEvent dataEvent1 = ringBuffer.get(1);
		System.out.println("Event = " + dataEvent1);
		System.out.println("Data = " + dataEvent1.getData());
	}
}
