package com.sy.sa.event;

import com.lmax.disruptor.EventFactory;

/**
*
* @data 2019年4月23日 上午11:45:15
* @author ztq
**/
public class MyDataEventFactory implements EventFactory<MyDataEvent>{

	@Override
	public MyDataEvent newInstance() {
		return new MyDataEvent();  
	}

}
