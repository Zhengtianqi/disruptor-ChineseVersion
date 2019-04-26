package com.lmax.disruptor;
/**
 * 
 * Sequenced 序列的申请及发布
 * @author ztq
 *
 */
public interface Sequenced
{
    /**
     * 获取队列的大小，就是RingBuffer的容量
     * @return the size of the RingBuffer.
     */
    int getBufferSize();

    /**
     * 判断队列中是否还有可用的容量
     * @param requiredCapacity in the buffer
     * @return true if the buffer has the capacity to allocate the next sequence otherwise false.
     */
    boolean hasAvailableCapacity(int requiredCapacity);

    /**
     * 获取队列中剩余的有效容量
     * @return The number of slots remaining.
     */
    long remainingCapacity();

    /**
     * 申请下1个sequence，用来发布事件，申请失败则自旋
     * @return the claimed sequence value
     */
    long next();

    /**
     * 申请下n个sequence，用来发布事件，申请失败则自旋
     * <pre>
     * int n = 10;
     * long hi = sequencer.next(n);
     * long lo = hi - (n - 1);
     * for (long sequence = lo; sequence &lt;= hi; sequence++) {
     *     // Do work.
     * }
     * sequencer.publish(lo, hi);
     * </pre>
     *
     * @param n the number of sequences to claim
     * @return the highest claimed sequence value
     */
    long next(int n);

    /**
     * 尝试获取一个sequence，尝试申请下一个序列值用来发布事件，这个是无阻塞的方法。 
     * @return the claimed sequence value
     * @throws InsufficientCapacityException thrown if there is no space available in the ring buffer.
     */
    long tryNext() throws InsufficientCapacityException;

    /**
     * 尝试获取n个sequence，尝试申请下N个序列值用来发布事件，这个是无阻塞的方法。 
     * @param n the number of sequences to claim
     * @return the claimed sequence value
     * @throws InsufficientCapacityException thrown if there is no space available in the ring buffer.
     */
    long tryNext(int n) throws InsufficientCapacityException;

    /**
     * 
     * 发布sequence，在给定的序列值上发布事件，当填充好事件后会调用这个方法。 
     * 
     * @param sequence the sequence to be published.
     */
    void publish(long sequence);

    /**
     * 
     * 批量发布sequence,在给定的序列值上发布事件，当填充好事件后会调用这个方法。 
     * 
     * @param lo first sequence number to publish
     * @param hi last sequence number to publish
     */
    void publish(long lo, long hi);
}