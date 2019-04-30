package com.lmax.disruptor;

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;

class LhsPadding {
	protected long p1, p2, p3, p4, p5, p6, p7;
}

class Value extends LhsPadding {
	protected volatile long value;
}

class RhsPadding extends Value {
	protected long p9, p10, p11, p12, p13, p14, p15;
}

/**
 * 
 * 1.通过Sequence的一系列的继承关系可以看到，它真正的用来计数的域是value，在value的前后各有7个long型的填充值，这些值在这里的作用是做cpu
 * cache line填充，防止发生伪共享。 2.Sequence类的其他set、get等方法都是通过UNSAFE对象实现对value值的原子操作
 * 
 */
public class Sequence extends RhsPadding {
	static final long INITIAL_VALUE = -1L;
	private static final Unsafe UNSAFE;
	private static final long VALUE_OFFSET;

	static {
		UNSAFE = Util.getUnsafe();
		try {
			VALUE_OFFSET = UNSAFE.objectFieldOffset(Value.class.getDeclaredField("value"));
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 创建一个初始化为-1的序列
	 */
	public Sequence() {
		this(INITIAL_VALUE);
	}

	/**
	 * 用特定的值创建一个序列
	 *
	 * @param initialValue 初始化序列的初始值
	 */
	public Sequence(final long initialValue) {
		UNSAFE.putOrderedLong(this, VALUE_OFFSET, initialValue);
	}

	/**
	 * @return 序列的当前值。
	 */
	public long get() {
		return value;
	}

	/**
	 * 执行此序列的有序写入。意图是写入和先前的Store/Store建立栏栅
	 * 
	 * ordered write，在当前写操作和任意之前的读操作之间加入Store/Store屏障
	 *
	 * @param value 序列的新值。
	 */
	public void set(final long value) {
		UNSAFE.putOrderedLong(this, VALUE_OFFSET, value);
	}

	/**
	 * 
	 * 在当前写操作和任意之前的读操作之间加入Store/Store屏障 在当前写操作和任意之后的读操作之间加入Store/Load屏障
	 *
	 * @param value sequence中的新值
	 */
	public void setVolatile(final long value) {
		UNSAFE.putLongVolatile(this, VALUE_OFFSET, value);
	}

	/**
	 * 对序列执行比较和设置操作
	 *
	 * @param expectedValue 预期当前值
	 * @param newValue      要更新的值
	 * @return 操作成功返回true, 否则返回false
	 */
	public boolean compareAndSet(final long expectedValue, final long newValue) {
		return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expectedValue, newValue);
	}

	/**
	 * 原子地将序列递增1
	 *
	 * @return 增量后的值
	 */
	public long incrementAndGet() {
		return addAndGet(1L);
	}

	/**
	 * 以原子方式添加提供的值
	 *
	 * @param increment 自动添加值到序列
	 * @return 增量后的值
	 */
	public long addAndGet(final long increment) {
		long currentValue;
		long newValue;

		do {
			currentValue = get();
			newValue = currentValue + increment;
		} while (!compareAndSet(currentValue, newValue));

		return newValue;
	}

	@Override
	public String toString() {
		return Long.toString(get());
	}
}
