package org.prevayler.implementation

import org.prevayler.Transaction
import org.prevayler.foundation.serialization.Serializer

object dummyTransaction extends Transaction {
	def executeOn(system: Object, date: java.util.Date) = {}
}

class DummyTransactionCapsule(journalSerializer: Serializer)
	extends TransactionCapsule(dummyTransaction, journalSerializer)