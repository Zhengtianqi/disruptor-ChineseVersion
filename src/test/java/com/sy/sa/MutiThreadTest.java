package com.sy.sa;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.IgnoreExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.WorkerPool;

/**
* @data 2019年4月22日 下午4:08:20
* @author ztq
**/
public class MutiThreadTest {
	public static void main(String[] args) {
	    //创建一个RingBuffer，注意容量是2。
	    RingBuffer<TradeBO> ringBuffer = RingBuffer.createSingleProducer(() -> new TradeBO(), 2);
	    //创建2个WorkHandler其实就是创建2个WorkProcessor
	    WorkerPool<TradeBO> workerPool =
	            new WorkerPool<TradeBO>(ringBuffer, ringBuffer.newBarrier(),
	                    new IgnoreExceptionHandler(),
	                    new ConsumerC(),new ConsumerD());
	    //将WorkPool的工作序列集设置为ringBuffer的追踪序列。
	    ringBuffer.addGatingSequences(workerPool.getWorkerSequences());
	    //创建一个线程池用于执行Workhandler。
	    Executor executor = Executors.newFixedThreadPool(4);
	    //启动WorkPool。
	    workerPool.start(executor);
	    //往RingBuffer上发布事件
	    for (int i = 0; i < 4; i++) {
	        int finalI = i;
	        EventTranslator eventTranslator = (EventTranslator<TradeBO>) (event, sequence) -> {
	            event.setId(finalI);
	            event.setPrice((double) finalI);
	        };
	        ringBuffer.publishEvent(eventTranslator);
	        System.out.println("发布[" + finalI + "]");
	    }
	}
	
	public static class ConsumerC implements WorkHandler<TradeBO> {
		@Override
		public void onEvent(TradeBO event) throws Exception {
			System.out.println("ConsumerB id=" + event.getId() + "price=" + event.getPrice());
		}
	}

	public static class ConsumerD implements WorkHandler<TradeBO> {
		@Override
		public void onEvent(TradeBO event) throws Exception {
			System.out.println("ConsumerB id=" + event.getId() + "   price=" + event.getPrice());
		}
	}
	
	public static class TradeBO{
		private Integer id;
		private Double price;
		public Integer getId() {
			return id;
		}
		public void setId(Integer id) {
			this.id = id;
		}
		public Double getPrice() {
			return price;
		}
		public void setPrice(Double price) {
			this.price = price;
		}
		
	}
	
}
