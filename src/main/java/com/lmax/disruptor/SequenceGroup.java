package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.lmax.disruptor.util.Util;

/**
 * 一个{@link Sequence}组，可以在线程安全的情况下动态添加和删除{@link Sequence}
 * <p>
 * {@link SequenceGroup#get()}和{@link SequenceGroup#set(long)}方法是无锁的。
 * 可以使用{@link SequenceGroup#add(Sequence)}和{@link SequenceGroup#remove(Sequence)}同时调用序列
 * </p>
 */
public final class SequenceGroup extends Sequence {
	private static final AtomicReferenceFieldUpdater<SequenceGroup, Sequence[]> SEQUENCE_UPDATER = AtomicReferenceFieldUpdater
			.newUpdater(SequenceGroup.class, Sequence[].class, "sequences");
	private volatile Sequence[] sequences = new Sequence[0];

	/**
	 * 默认构造函数
	 */
	public SequenceGroup() {
		super(-1);
	}

	/**
	 * 
	 * 获取序列组中最小的序列值。
	 * 
	 * @return 的最小序列值
	 */
	@Override
	public long get() {
		return Util.getMinimumSequence(sequences);
	}

	/**
	 * 
	 * 将序列组中所有的序列设置为给定值。
	 *
	 * @param value 将序列组设置为
	 */
	@Override
	public void set(final long value) {
		final Sequence[] sequences = this.sequences;
		for (Sequence sequence : sequences) {
			sequence.set(value);
		}
	}

	/**
	 * 
	 * 添加一个序列到序列组，这个方法只能在初始化的时候调用。 运行时添加的话，使用addWhileRunning(Cursored, Sequence)
	 *
	 * @param sequence 要添加到聚合的序列
	 * @see SequenceGroup#addWhileRunning(Cursored, Sequence)
	 */
	public void add(final Sequence sequence) {
		Sequence[] oldSequences;
		Sequence[] newSequences;
		do {
			oldSequences = sequences;
			final int oldSize = oldSequences.length;
			newSequences = new Sequence[oldSize + 1];
			System.arraycopy(oldSequences, 0, newSequences, 0, oldSize);
			newSequences[oldSize] = sequence;
		} while (!SEQUENCE_UPDATER.compareAndSet(this, oldSequences, newSequences));
	}

	/**
	 * 
	 * 将序列组中出现的第一个给定的序列移除。
	 *
	 * @param sequence 要从此聚合中删除的序列
	 * @return 如果序列被删除则为true，否则为false
	 */
	public boolean remove(final Sequence sequence) {
		return SequenceGroups.removeSequence(this, SEQUENCE_UPDATER, sequence);
	}

	/**
	 * 
	 * 获取序列组的大小。
	 *
	 * @return 组的大小
	 */
	public int size() {
		return sequences.length;
	}

	/**
	 * 
	 * 在线程已经开始往Disruptor上发布事件后，添加一个序列到序列组。 调用这个方法后，会将新添加的序列的值设置为游标的值。
	 *
	 * @param cursored 此序列组的所有者将从中提取事件的数据结构。
	 * @param sequence 需要添加的Sequence
	 */
	public void addWhileRunning(Cursored cursored, Sequence sequence) {
		SequenceGroups.addSequences(this, SEQUENCE_UPDATER, cursored, sequence);
	}
}
