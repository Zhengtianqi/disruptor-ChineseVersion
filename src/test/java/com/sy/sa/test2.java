package com.sy.sa;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.BasicExecutor;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

/**
*
* @data 2019年5月5日 上午10:43:56
* @author ztq
**/
public class test2 {
	public static void main(String[] args) {
		//step_4 定义用于事件处理的线程池
		BasicExecutor executor = new BasicExecutor(new DefaultThreadFactory());
		//指定等待策略
		WaitStrategy BLOCKING_WAIT = new BlockingWaitStrategy();
		
		//step_5 启动Disruptor
		EventFactory<LongEvent> eventFactory = new LongEventFactory();
//		ExecutorService executor1 = Executors.newSingleThreadExecutor();
		// RingBuffer大小，必须是2的N次方
		int ringBufferSize = 1024*1024;
		//step_6 消费事件
		Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(eventFactory,ringBufferSize,executor,ProducerType.MULTI,new YieldingWaitStrategy());
		EventHandler<LongEvent> eventHandler = new LongEventHandler();
//		disruptor.handleEventsWith(eventHandler);
		// 多消费者
		EventHandler<LongEvent> eventHandler2 = new LongEventHandler();
		disruptor.handleEventsWith(eventHandler,eventHandler2);
		disruptor.start();
		// step_7 发布事件；
		RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();
		long sequence = ringBuffer.next();//请求下一个事件序号；
		    
		try {
		    LongEvent event = ringBuffer.get(sequence);//获取该序号对应的事件对象；
		    long data = getEventData();//获取要通过事件传递的业务数据；
		    event.set(data);
		} finally{
		    ringBuffer.publish(sequence);//发布事件；
		}
		
	}
	private static long getEventData() {
		return 999;
	}
	public static class DefaultThreadFactory implements ThreadFactory {  
	    private static final AtomicInteger poolNumber = new AtomicInteger(1);//原子类，线程池编号  
	    private final ThreadGroup group;//线程组  
	    private final AtomicInteger threadNumber = new AtomicInteger(1);//线程数目  
	    private final String namePrefix;//为每个创建的线程添加的前缀  
	  
	    DefaultThreadFactory() {  
	        SecurityManager s = System.getSecurityManager();  
	        group = (s != null) ? s.getThreadGroup() :  
	                              Thread.currentThread().getThreadGroup();//取得线程组  
	        namePrefix = "pool-" +  
	                      poolNumber.getAndIncrement() +  
	                     "-thread-";  
	    }  
	  
	    public Thread newThread(Runnable r) {  
	        Thread t = new Thread(group, r,  
	                              namePrefix + threadNumber.getAndIncrement(),  
	                              0);//真正创建线程的地方，设置了线程的线程组及线程名  
	        if (t.isDaemon())  
	            t.setDaemon(false);  
	        if (t.getPriority() != Thread.NORM_PRIORITY)//默认是正常优先级  
	            t.setPriority(Thread.NORM_PRIORITY);  
	        return t;  
	    }  
	} 

	//step_3:定义事件处理的具体实现
	public static class LongEventHandler implements EventHandler<LongEvent>{

		@Override
		public void onEvent(LongEvent event, long sequence, boolean endOfBatch) throws Exception {
			System.out.println("Event" + event.value);
		}
		
	}
	// step_2: 定义事件工厂
	public static class LongEventFactory implements EventFactory<LongEvent>{
		@Override
		public LongEvent newInstance() {
			return new LongEvent();
		}
		
	}
	//step_1:定义事件
	public static class LongEvent{
		private long value;
		public void set(long value) {
			this.value = value;
		}
	}
}
