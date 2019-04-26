package com.sy.sa;

import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.EventHandler;

/**
*
* @data 2019年4月23日 下午4:42:10
* @author ztq
**/
public class MyDataEventHandler implements EventHandler{

	@Override
	public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
        TimeUnit.SECONDS.sleep(3);  
        System.out.println("handle event's data:" + event +"isEndOfBatch:"+endOfBatch);  
	}

}
