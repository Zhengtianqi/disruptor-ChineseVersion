package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.Util;

/**
 * 缓冲行填充，防止伪共享。真正使用的值是nextValue和cachedValue。
 */
abstract class SingleProducerSequencerPad extends AbstractSequencer {
	protected long p1, p2, p3, p4, p5, p6, p7;

	SingleProducerSequencerPad(int bufferSize, WaitStrategy waitStrategy) {
		super(bufferSize, waitStrategy);
	}
}

/**
 * SingleProducerSequencerFields 维护事件发布者发布的序列和事件处理者处理到的最小序列。
 */
abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad {
	SingleProducerSequencerFields(int bufferSize, WaitStrategy waitStrategy) {
		super(bufferSize, waitStrategy);
	}

	// 事件发布者发布到的序列值
	long nextValue = Sequence.INITIAL_VALUE;
	// 当前序列的cachedValue记录的是之前事件处理者申请的序列值。
	long cachedValue = Sequence.INITIAL_VALUE;
}

/**
 *
 * SingleProducerSequencer内部维护cachedValue(事件消费者序列)，nextValue(事件发布者序列)。并且采用padding填充。这个类是线程不安全的。<br>
 * SingleProducerSequencer对象拥有所有正在访问RingBuffer的消费者gatingSequences列表
 * 在调用完{@link Sequencer#publish(long)}之后更新游标
 */

public final class SingleProducerSequencer extends SingleProducerSequencerFields {
	protected long p1, p2, p3, p4, p5, p6, p7;

	/**
	 * 
	 * 使用选定的等待策略和buffer大小构造Sequencer。
	 *
	 * @param bufferSize 这个序列的buffer大小
	 * @param 等待策略
	 */
	public SingleProducerSequencer(int bufferSize, WaitStrategy waitStrategy) {
		super(bufferSize, waitStrategy);
	}

	/**
	 * 
	 * 当前序列的nextValue + requiredCapacity是事件发布者要申请的序列值。
	 * 
	 * @see Sequencer#hasAvailableCapacity(int)
	 */
	@Override
	public boolean hasAvailableCapacity(int requiredCapacity) {
		return hasAvailableCapacity(requiredCapacity, false);
	}

	private boolean hasAvailableCapacity(int requiredCapacity, boolean doStore) {
		long nextValue = this.nextValue;

		long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
		long cachedGatingSequence = this.cachedValue;

		if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
			if (doStore) {
				cursor.setVolatile(nextValue); // StoreLoad fence
			}

			long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
			this.cachedValue = minSequence;

			if (wrapPoint > minSequence) {
				return false;
			}
		}

		return true;
	}

	/**
	 * next方法是真正申请序列的方法，里面的逻辑和hasAvailableCapacity一样，只是在不能申请序列的时候会阻塞等待一下，然后重试。
	 * 
	 * @see Sequencer#next()
	 */
	@Override
	public long next() {
		return next(1);
	}

	/**
	 * 该方法是事件发布者申请序列
	 * 
	 * @see Sequencer#next(int)
	 */
	@Override
	public long next(int n) {
		// 该方法是事件发布者申请序列，n表示此次发布者期望获取多少个序号，通常是1
		if (n < 1) {
			throw new IllegalArgumentException("n must be > 0");
		}
		// 获取事件发布者发布到的序列值
		long nextValue = this.nextValue;

		// 发布者当前序号值+期望获取的序号数量后达到的序号值
		long nextSequence = nextValue + n;
		// wrap 代表申请的序列绕一圈以后的位置
		long wrapPoint = nextSequence - bufferSize;
		// 获取事件处理者处理到的序列值
		long cachedGatingSequence = this.cachedValue;
		/**
		 * 1.事件发布者要申请的序列值大于事件处理者当前的序列值且事件发布者要申请的序列值减去环的长度要小于事件处理者的序列值。 2.满足，可以申请给定的序列。
		 * 3.不满足，就需要查看一下当前事件处理者的最小的序列值(可能有多个事件处理者)。如果最小序列值大于等于当前事件处理者的最小序列值大了一圈，那就不能申请了序列(申请了就会被覆盖)，
		 */
		if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
			// wrapPoint > cachedGatingSequence 代表绕一圈并且位置大于事件处理者处理到的序列
			// cachedGatingSequence > nextValue 说明事件发布者的位置位于事件处理者的屁股后面
			// 维护父类中事件生产者的序列
			cursor.setVolatile(nextValue); // StoreLoad fence

			long minSequence;
			// 如果事件生产者绕一圈以后大于事件处理者的序列，那么会在此处自旋
			while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue))) {
				LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
			}
			// 缓存最小值
			this.cachedValue = minSequence;
		}

		this.nextValue = nextSequence;

		return nextSequence;
	}

	// 此处博客中自己添加了一个nextInSimple方法
	// https://github.com/daoqidelv/disruptor/blob/5e099621e921483870a4367a8ffc2e789409d613/src/main/java/com/lmax/disruptor/SingleProducerSequencer.java
	/**
	 * tryNext方法是next方法的非阻塞版本，不能申请就抛异常。
	 * 
	 * @see Sequencer#tryNext()
	 */
	@Override
	public long tryNext() throws InsufficientCapacityException {
		return tryNext(1);
	}

	/**
	 * @see Sequencer#tryNext(int)
	 */
	@Override
	public long tryNext(int n) throws InsufficientCapacityException {
		if (n < 1) {
			throw new IllegalArgumentException("n must be > 0");
		}

		if (!hasAvailableCapacity(n, true)) {
			throw InsufficientCapacityException.INSTANCE;
		}

		long nextSequence = this.nextValue += n;

		return nextSequence;
	}

	/**
	 * remainingCapacity方法就是环形队列的容量减去事件发布者与事件处理者的序列差。
	 * 
	 * @see Sequencer#remainingCapacity()
	 */
	@Override
	public long remainingCapacity() {
		long nextValue = this.nextValue;

		long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
		long produced = nextValue;
		return getBufferSize() - (produced - consumed);
	}

	/**
	 * claim方法是声明一个序列，在初始化的时候用。
	 * 
	 * @see Sequencer#claim(long)
	 */
	@Override
	public void claim(long sequence) {
		this.nextValue = sequence;
	}

	/**
	 * 发布一个序列，会先设置内部游标值，然后唤醒等待的事件处理者。
	 * 
	 * @see Sequencer#publish(long)
	 */
	@Override
	public void publish(long sequence) {
		cursor.set(sequence);
		waitStrategy.signalAllWhenBlocking();
	}

	/**
	 * @see Sequencer#publish(long, long)
	 */
	@Override
	public void publish(long lo, long hi) {
		publish(hi);
	}

	/**
	 * @see Sequencer#isAvailable(long)
	 */
	@Override
	public boolean isAvailable(long sequence) {
		return sequence <= cursor.get();
	}

	@Override
	public long getHighestPublishedSequence(long lowerBound, long availableSequence) {
		return availableSequence;
	}
}
