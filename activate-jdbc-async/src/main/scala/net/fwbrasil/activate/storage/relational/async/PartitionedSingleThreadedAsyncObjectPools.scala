package com.github.mauricio.async.db.pool

import scala.concurrent.Future
import com.github.mauricio.async.db.util.ExecutorServiceUtils

class PartitionedSingleThreadedAsyncObjectPools[T](
        factory: ObjectFactory[T],
        configuration: PoolConfiguration,
        numberOfPartitions: Int) {

    private val pools =
        (0 until numberOfPartitions)
            .map(_ -> new SingleThreadedAsyncObjectPool(factory, configuration))
            .toMap

    def take: Future[T] =
        currentThreadPool.take

    def giveBack(item: T) =
        currentThreadPool.giveBack(item)

    def close = {
        import ExecutorServiceUtils.CachedExecutionContext
        Future.sequence(pools.values.map(_.close).toList)
    }
    
    protected def isClosed = 
        pools.values.forall(_.isClosed)

    private def currentThreadPool =
        pools(currentThreadAffinity)

    private def currentThreadAffinity =
        (Thread.currentThread.getId % numberOfPartitions).toInt
}