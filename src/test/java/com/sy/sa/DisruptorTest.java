package com.sy.sa;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.dsl.Disruptor;
import com.sy.sa.event.MyDataEvent;
import com.sy.sa.event.MyDataEventFactory;
import com.sy.sa.event.MyDataEventTranslator;

/**
*
* @data 2019年4月23日 下午4:38:44
* @author ztq
**/
public class DisruptorTest {
	public static void main(String[] args) {
		  //创建一个执行器(线程池)。  
	    Executor executor = Executors.newFixedThreadPool(4);  
	    //创建一个Disruptor。  
	    Disruptor<MyDataEvent> disruptor =   
	            new Disruptor<MyDataEvent>(new MyDataEventFactory(), 4, executor);  
	    //创建两个事件处理器。  
	    MyDataEventHandler handler1 = new MyDataEventHandler();  
	    KickAssEventHandler handler2 = new KickAssEventHandler();  
	    //同一个事件，先用handler1处理再用handler2处理。  
	    disruptor.handleEventsWith(handler1).then(handler2);  
	    //启动Disruptor。  
	    disruptor.start();  
	    //发布10个事件。  
	    for(int i=0;i<10;i++){  
	        disruptor.publishEvent(new MyDataEventTranslator());  
	        System.out.println("发布事件["+i+"]");  
	        try {  
	            TimeUnit.SECONDS.sleep(3);  
	        } catch (InterruptedException e) {  
	            e.printStackTrace();  
	        }  
	    }  
	    try {  
	        System.in.read();  
	    } catch (IOException e) {  
	        e.printStackTrace();  
	    }  
	}

}
