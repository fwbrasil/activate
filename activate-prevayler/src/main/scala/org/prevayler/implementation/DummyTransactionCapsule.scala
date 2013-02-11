package org.prevayler.implementation

import org.prevayler.foundation.serialization.Serializer
import java.util.Date

class DummyTransactionCapsule
        extends TransactionCapsule(null) {

    override def executeOn(prevalentSystem: Object, executionTime: Date, journalSerializer: Serializer) = {}

}