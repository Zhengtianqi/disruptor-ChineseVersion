package com.lmax.disruptor;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.lmax.disruptor.util.Util;

/**
 * 各种sequencer的基类（单/多） 提供公有的功能，如gating sequences的管理（add/remove） 和 当前光标的所在位置。
 * 作用就是管理追踪序列和关联当前序列。
 */
public abstract class AbstractSequencer implements Sequencer {
	// 用来对gatingSequences做原子操作的。Sequence[]里面存储的是事件处理者处理到的序列。
	private static final AtomicReferenceFieldUpdater<AbstractSequencer, Sequence[]> SEQUENCE_UPDATER = AtomicReferenceFieldUpdater
			.newUpdater(AbstractSequencer.class, Sequence[].class, "gatingSequences");

	// 队列大小
	protected final int bufferSize;
	// 等待策略
	protected final WaitStrategy waitStrategy;
	// 事件发布者的已经发布到的sequence
	protected final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
	// 事件处理者处理到的序列对象
	protected volatile Sequence[] gatingSequences = new Sequence[0];

	/**
	 *
	 * 检查队列大小是否是2^n，判断buffersize大小
	 * 
	 * @param bufferSize   The total number of entries, must be a positive power of
	 *                     2.
	 * @param waitStrategy The wait strategy used by this sequencer
	 */
	public AbstractSequencer(int bufferSize, WaitStrategy waitStrategy) {
		if (bufferSize < 1) {
			throw new IllegalArgumentException("bufferSize must not be less than 1");
		}
		if (Integer.bitCount(bufferSize) != 1) {
			throw new IllegalArgumentException("bufferSize must be a power of 2");
		}

		this.bufferSize = bufferSize;
		this.waitStrategy = waitStrategy;
	}

	/**
	 * 获取事件发布者的序列
	 * 
	 * @see Sequencer#getCursor()
	 */
	@Override
	public final long getCursor() {
		return cursor.get();
	}

	/**
	 * 获取大小
	 * 
	 * @see Sequencer#getBufferSize()
	 */
	@Override
	public final int getBufferSize() {
		return bufferSize;
	}

	/**
	 * 把事件消费者序列维护到gating sequence
	 * 
	 * @see Sequencer#addGatingSequences(Sequence...)
	 */
	@Override
	public final void addGatingSequences(Sequence... gatingSequences) {
		SequenceGroups.addSequences(this, SEQUENCE_UPDATER, this, gatingSequences);
	}

	/**
	 * 从gating sequence移除序列
	 * 
	 * @see Sequencer#removeGatingSequence(Sequence)
	 */
	@Override
	public boolean removeGatingSequence(Sequence sequence) {
		return SequenceGroups.removeSequence(this, SEQUENCE_UPDATER, sequence);
	}

	/**
	 * 获取gating sequence中事件处理者处理到最小的序列值
	 * 
	 * @see Sequencer#getMinimumSequence()
	 */
	@Override
	public long getMinimumSequence() {
		return Util.getMinimumSequence(gatingSequences, cursor.get());
	}

	/**
	 * 创建了一个序列栅栏
	 * 
	 * @see Sequencer#newBarrier(Sequence...)
	 */
	@Override
	public SequenceBarrier newBarrier(Sequence... sequencesToTrack) {
		return new ProcessingSequenceBarrier(this, waitStrategy, cursor, sequencesToTrack);
	}

	/**
	 * 
	 * 为此序列创建将使用提供的数据生产者序和选通序列的事件轮询器
	 * 
	 * @param dataProvider    此事件轮询器用户的数据源
	 * @param gatingSequences 序列
	 * @return RingBuffer和提供的序列进行轮询
	 */
	@Override
	public <T> EventPoller<T> newPoller(DataProvider<T> dataProvider, Sequence... gatingSequences) {
		return EventPoller.newInstance(dataProvider, this, new Sequence(), cursor, gatingSequences);
	}

	@Override
	public String toString() {
		return "AbstractSequencer{" + "waitStrategy=" + waitStrategy + ", cursor=" + cursor + ", gatingSequences="
				+ Arrays.toString(gatingSequences) + '}';
	}
}