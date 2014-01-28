package net.fwbrasil.activate.storage.prevayler

import scala.annotation.implicitNotFound

import org.prevayler.Prevayler
import org.prevayler.PrevaylerFactory
import org.prevayler.foundation.serialization.Serializer
import org.prevayler.implementation.DummyTransactionCapsule
import org.prevayler.implementation.PrevalentSystemGuard
import org.prevayler.implementation.TransactionTimestamp
import org.prevayler.implementation.publishing.AbstractPublisher
import org.prevayler.implementation.publishing.TransactionSubscriber

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.memory.BasePrevalentStorage
import net.fwbrasil.activate.storage.memory.BasePrevalentStorageSystem
import net.fwbrasil.activate.util.Reflection

@implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
class PrevaylerStorage(
    val factory: PrevaylerFactory[BasePrevalentStorageSystem])(implicit val context: ActivateContext)
        extends BasePrevalentStorage[BasePrevalentStorageSystem, Prevayler[BasePrevalentStorageSystem]] {

    protected[activate] var prevayler: Prevayler[BasePrevalentStorageSystem] = _

    def this(prevalenceDirectory: String)(implicit context: ActivateContext) = this({
        val res = new PrevaylerFactory[BasePrevalentStorageSystem]()
        res.configurePrevalenceDirectory(prevalenceDirectory)
        res
    })
    def this()(implicit context: ActivateContext) = this("activate")

    def directAccess =
        prevayler

    override protected def snapshot(system: BasePrevalentStorageSystem) =
        prevayler.takeSnapshot

    override protected def recover = {
        val prevalentSystem = new BasePrevalentStorageSystem()
        factory.configurePrevalentSystem(prevalentSystem)
        prevayler = factory.create()
        hackPrevaylerToActAsARedoLogOnly
        prevayler.prevalentSystem
    }

    private def hackPrevaylerToActAsARedoLogOnly = {
        val publisher = Reflection.get(prevayler, "_publisher").asInstanceOf[AbstractPublisher]
        val guard = Reflection.get(prevayler, "_guard").asInstanceOf[PrevalentSystemGuard[BasePrevalentStorageSystem]]
        val journalSerializer = Reflection.get(prevayler, "_journalSerializer").asInstanceOf[Serializer]
        publisher.cancelSubscription(guard)
        val dummyCapsule = new DummyTransactionCapsule
        publisher.addSubscriber(new TransactionSubscriber {
            def receive(transactionTimestamp: TransactionTimestamp) = {
                guard.receive(
                    new TransactionTimestamp(
                        dummyCapsule,
                        transactionTimestamp.systemVersion,
                        transactionTimestamp.executionTime))
            }
        })
    }

    override protected def logTransaction(
        insertList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
        updateList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
        deleteList: Array[(BaseEntity#ID, Class[BaseEntity])]) = {
        val transaction =
            new PrevaylerTransaction(
                context,
                insertList,
                updateList,
                deleteList)
        prevayler.execute(transaction)
    }

}

object PrevaylerMemoryStorageFactory extends StorageFactory {
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        getProperty("prevalenceDirectory").map(new PrevaylerStorage(_)).getOrElse(new PrevaylerStorage())
}
