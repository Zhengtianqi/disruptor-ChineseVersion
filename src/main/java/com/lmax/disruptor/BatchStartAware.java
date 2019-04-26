package com.lmax.disruptor;
/**
 * @{link BatchEventProcessor}中用到。每次循环取得一批可用事件后，在实际处理前调用 。批量启动的作用
 */
public interface BatchStartAware
{
    void onBatchStart(long batchSize);
}
