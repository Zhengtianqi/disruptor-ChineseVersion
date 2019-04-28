package com.lmax.disruptor;

/**
 * 
 * Sequenced接口提供的方法都是用来给生产者使用，用于申请序列，发布序列的
 *
 */
public interface Sequenced {
	/**
	 *	获取队列的大小，就是RingBuffer的容量
	 * 
	 * @return RingBuffer的容量
	 */
	int getBufferSize();

	/**
	 *	判断队列中是否还有可用的容量
	 * 
	 * @param requiredCapacity
	 * @return 如果缓冲区具有分配下一个序列的容量，则返回true，否则返回false。
	 */
	boolean hasAvailableCapacity(int requiredCapacity);

	/**
	 *	获取队列中剩余的有效容量
	 * 
	 * @return 剩余的插槽数量
	 */
	long remainingCapacity();

	/**
	 *	申请下1个sequence，用来发布事件，申请失败则自旋
	 * 
	 * @return 声称的序列值
	 */
	long next();

	/**
	 *	申请下n个sequence，用来发布事件，申请失败则自旋
	 * 
	 * <pre>
	 * int n = 10;
	 * long hi = sequencer.next(n);
	 * long lo = hi - (n - 1);
	 * for (long sequence = lo; sequence &lt;= hi; sequence++) {
	 * 	// Do work.
	 * }
	 * sequencer.publish(lo, hi);
	 * </pre>
	 *
	 * @param n 声明序列的数量
	 * @return
	 */
	long next(int n);

	/**
	 *	尝试获取一个sequence，尝试申请下一个序列值用来发布事件，这个是无阻塞的方法
	 * 
	 * @return 声明的序列值
	 * @throws InsufficientCapacityException 如果RingBuffer中没有可用空间则抛出
	 */
	long tryNext() throws InsufficientCapacityException;

	/**
	 *	尝试获取n个sequence，尝试申请下N个序列值用来发布事件，这个是无阻塞的方法
	 * 
	 * @param n 序列值
	 * @return 要发布的序列值
	 * @throws InsufficientCapacityException 如果RingBuffer中没有可用空间则抛出
	 */
	long tryNext(int n) throws InsufficientCapacityException;

	/**
	 * 
	 *	发布sequence，在给定的序列值上发布事件，当填充好事件后会调用这个方法。
	 * 
	 * @param sequence,要发布的序列。
	 */
	void publish(long sequence);

	/**
	 * 
	 *	批量发布sequence,在给定的序列值上发布事件，当填充好事件后会调用这个方法。
	 * 
	 * @param lo 要发布的第一个序列号
	 * @param hi 要发布的周后的序列号
	 */
	void publish(long lo, long hi);
}