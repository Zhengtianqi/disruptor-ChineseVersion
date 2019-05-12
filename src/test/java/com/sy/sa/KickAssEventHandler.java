package com.sy.sa;

import com.lmax.disruptor.EventHandler;

/**
*
* @data 2019年4月23日 下午4:42:15
* @author ztq
**/
public class KickAssEventHandler implements EventHandler{

	@Override
	public void onEvent(Object event, long sequence, boolean endOfBatch) throws Exception {
		 System.out.println(sequence+" times!!!!");  
	}

}
