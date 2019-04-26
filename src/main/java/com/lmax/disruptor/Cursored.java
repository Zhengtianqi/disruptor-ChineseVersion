package com.lmax.disruptor;

/**
 * Cursored接口只提供了一个获取当前序列值的方法
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
