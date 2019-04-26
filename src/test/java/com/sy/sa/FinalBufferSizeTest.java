package com.sy.sa;

import org.junit.Test;

/**
*
* @data 2019年4月23日 上午10:41:15
* @author ztq
**/
public class FinalBufferSizeTest {
	protected final int bufferSize;
	public FinalBufferSizeTest(){
		this.bufferSize = 1;
//		this.bufferSize = 3;
	}
	@Test
	public void print(){
		System.out.println(bufferSize);
	}
}
