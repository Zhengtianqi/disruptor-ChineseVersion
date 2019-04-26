package com.sy.sa.event;

import com.lmax.disruptor.RingBuffer;

/**
 *
 * @data 2019年4月23日 上午11:46:01
 * @author ztq
 **/
public class MainTest {
	/**
	 * RingBuffer里面是MydataEvent，而不是MyData；其次我们构造好了RingBuffer，里面就已经填充了事件，我们可以取一个事件出来，发现里面的数据是空的。
	 * 所以我们需要生产者进行数据生产。要使用RingBuffer发布一个事件，需要一个事件转换器接口，针对我们的数据实现一个MyDataEventTranslator
	 * @param args
	 */
	public static void main(String[] args) {
		RingBuffer<MyDataEvent> ringBuffer = RingBuffer.createSingleProducer(new MyDataEventFactory(), 1024);
		MyDataEvent dataEvent = ringBuffer.get(0);
		System.out.println("Event = " + dataEvent);
		System.out.println("Data = " + dataEvent.getData());
	}

}
