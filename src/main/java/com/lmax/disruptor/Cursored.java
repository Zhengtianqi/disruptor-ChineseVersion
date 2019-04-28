package com.lmax.disruptor;

/**
 * Cursored接口只有一个方法，getCursor就是用来获取当前游标的位置，也就是用来获取当前生产者的实时位置。
 * 
 * {@link SequenceGroups#addSequences(Object, java.util.concurrent.atomic.AtomicReferenceFieldUpdater, Cursored, Sequence...)}.
 */
public interface Cursored {
	/**
	 * 获取当前游标值.
	 *
	 * @return 当前游标值
	 */
	long getCursor();
}
