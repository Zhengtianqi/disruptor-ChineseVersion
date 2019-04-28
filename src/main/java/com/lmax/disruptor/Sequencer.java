package com.lmax.disruptor;

/**
 * 
 * Sequencer接口的很多功能是提供给事件发布者用的。通过Sequencer可以得到一个SequenceBarrier，给消费者使用
 * Sequencer接口提供了2种实现：SingleProducerSequencer和MultiProducerSequencer。
 * 
 * 用于声明访问数据结构的序列（sequences），他的行踪依赖于Sequences
 * Sequencer这是Disruptor真正的核心。实现了这个接口的两种生产者（单生产者和多生产者）均实现了所有的并发算法，为了在生产者和消费者之间进行准确快速的数据传递。
 */
public interface Sequencer extends Cursored, Sequenced {
	/**
	 * 游标初始值
	 */
	long INITIAL_CURSOR_VALUE = -1L;

	/**
	 * 一般用来初始化RingBuffer为指定的序列
	 *
	 * @param sequence 初始化的顺序
	 */
	void claim(long sequence);

	/**
	 * 消费者调用，判断sequence是否可以消费
	 * 
	 * @param sequence 要检查的缓冲区序列
	 * @return 如果序列可用，则返回true，否则返回false
	 */
	boolean isAvailable(long sequence);

	/**
	 * 将给定序列添加到追踪序列组中，生产者在申请序列时，会通过该序列组判断是否追尾
	 * 
	 * @param gatingSequences 要添加的序列
	 */
	void addGatingSequences(Sequence... gatingSequences);

	/**
	 * 从gating sequences中移除指定的sequence，从追踪序列组中移除指定的序列
	 *
	 * @param sequence 要删除的序列
	 * @return 如果找到此序列，则返回<tt>true</tt>;否则<tt>false</tt>
	 */
	boolean removeGatingSequence(Sequence sequence);

	/**
	 * 消费者用来追踪ringBuffer中可以用的sequence
	 * 
	 * @param sequencesToTrack 新构建的障碍将等待的所有序列
	 * @return 序列屏障，用于跟踪指定的序列。
	 * @see SequenceBarrier
	 */
	SequenceBarrier newBarrier(Sequence... sequencesToTrack);

	/**
	 * 生产者获取gating sequence中最小的sequence的值
	 * 
	 * @return 如果没有添加序列，则返回最小门控序列或光标序列
	 */
	long getMinimumSequence();

	/**
	 *
	 * 消费者使用，用来获取从nextSequence到availableSequence之间最大的有效序列，如果没有，则返回nextSequence-1
	 *
	 * @param nextSequence      开始扫描的序列
	 * @param availableSequence 要扫描的序列
	 * @return 可安全读取的最高值至少为<code> nextSequence - 1</ code>。
	 */
	long getHighestPublishedSequence(long nextSequence, long availableSequence);

	/**
	 * 通过给定的生产者和控制序列来创建一个EventPoller
	 * 
	 * @param provider
	 * @param gatingSequences
	 * @return
	 */
	<T> EventPoller<T> newPoller(DataProvider<T> provider, Sequence... gatingSequences);
}