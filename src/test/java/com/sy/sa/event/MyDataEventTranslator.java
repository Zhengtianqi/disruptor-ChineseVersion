package com.sy.sa.event;

import com.lmax.disruptor.EventTranslator;

/**
*
* @data 2019年4月23日 上午11:51:35
* @author ztq
**/
public class MyDataEventTranslator implements EventTranslator<MyDataEvent> {
	
	//public void translateTo(MyDataEvent event, long sequence, Integer id, String value)
	@Override
	public void translateTo(MyDataEvent event, long sequence) {
		//新建一个数据  
        MyData data = new MyData(1, "hello world!");  
        //将数据放入事件中。  
        event.setData(data);  
	}

}
