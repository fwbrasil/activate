package org.prevayler.implementation

import org.prevayler.foundation.serialization.Serializer
import java.util.Date

class DummyTransactionCapsule(journalSerializer: Serializer)
		extends TransactionCapsule(null, journalSerializer) {

	override def executeOn(prevalentSystem: Object, executionTime: Date, journalSerializer: Serializer) = {}

}