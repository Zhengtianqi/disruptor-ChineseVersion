package com.lmax.disruptor;

/**
 *	介绍：EventProcessor需要是一个runnable的实现，它将使用适当的等待策略从环形队列轮询事件。不太可能需要自己实现此接口。在第一个实例中使用{@link EventHandler}接口以及预先提供的BatchEventProcessor。
 *	作用：事件处理器会等待RingBuffer中的事件变为可用(可处理)，然后处理可用的事件。 一个事件处理器通常会关联一个线程。 
 */
public interface EventProcessor extends Runnable
{
    /**
     *	获取对此{@link EventProcessor}使用的{@link Sequence}的引用。获取一个消费者使用的序列引用
     * @return 对{@link EventProcessor}的引用
     */
    Sequence getSequence();

    /**
     *	发出此消费者应该停止的信号，在下一次干净休息时消耗完后。应该在下一次休息时完成消费时停止
     *	它将调用{@link SequenceBarrier#alert()}去通知线程检查状态
     */
    void halt();
    //判断是否运行
    boolean isRunning();
}
