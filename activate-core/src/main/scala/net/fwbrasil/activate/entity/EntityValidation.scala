package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.RefListener
import scala.collection.SeqLike
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.radon.util.ReferenceWeakKeyMap
import net.fwbrasil.activate.util.Reflection.toNiceObject
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.radon.transaction.Transaction
import scala.collection.mutable.SynchronizedMap
import net.fwbrasil.activate.ActivateContext
import scala.collection.mutable.HashSet
import scala.collection.mutable.SynchronizedSet
import java.util.concurrent.atomic.AtomicBoolean

import net.fwbrasil.radon.transaction.NestedTransaction

case class PostCond[R](f: () => R) {

	def postCondition(condition: => Boolean): R =
		postCondition(condition, "")

	def postCondition(condition: => Boolean, name: String): R = {
		val result = f()
		require(condition, name)
		result
	}

	def postCondition(condition: (R) => Boolean): R =
		postCondition(condition, "")

	def postCondition(condition: (R) => Boolean, name: String): R = {
		val result = f()
		require(condition(result), name)
		result
	}

	private[this] def require(f: => Boolean, name: String) =
		if (!f)
			throw new PostCondidionViolationException(name)
}

case class Invariant(f: () => Boolean, errorParams: () => List[Any])

abstract class ViolationExceptions(violations: String*) extends Exception {
	override def toString = violations.toString
}
case class InvariantViolationException(violations: InvariantViolation*) extends Exception {
	override def toString = violations.toString
}
case class PreCondidionViolationException(violations: String*) extends ViolationExceptions(violations: _*)
case class PostCondidionViolationException(violations: String*) extends ViolationExceptions(violations: _*)

case class InvariantViolation(invariantName: String, errorParams: List[Any])

object EntityValidationOption extends Enumeration {
	case class EntityValidationOption(name: String) extends Val
	val onWrite = EntityValidationOption("onWrite")
	val onRead = EntityValidationOption("onRead")
	val onCreate = EntityValidationOption("onCreate")
	val onTransactionEnd = EntityValidationOption("onTransactionEnd")
}

import EntityValidationOption._

trait EntityValidation {
	this: Entity =>

	@transient
	private var _invariants: List[(String, Invariant)] = null

	private[activate] def invariants = {
		if (_invariants == null) {
			val metadata = EntityHelper.getEntityMetadata(this.niceClass)
			if (metadata.invariantMethods.nonEmpty)
				initializeListener
			_invariants = for (method <- metadata.invariantMethods)
				yield (method.getName, method.invoke(this).asInstanceOf[Invariant])
		}
		_invariants
	}

	@transient
	private var _listener: RefListener[Any] = null

	private def listener = {
		if (_listener == null) {
			_listener = new RefListener[Any] with Serializable {
				private val isValidating = new AtomicBoolean(false)
				override def notifyPut(ref: Ref[Any], obj: Option[Any]) = {
					EntityValidation.validateOnWrite(EntityValidation.this)
				}
				override def notifyGet(ref: Ref[Any]) =
					if (isValidating.compareAndSet(false, true))
						try {
							EntityValidation.validateOnRead(EntityValidation.this)
						} finally {
							isValidating.set(false)
						}
			}
		}
		_listener
	}
	private[this] def initializeListener: Unit =
		for (ref <- vars)
			ref.addWeakListener(listener)

	protected def invariant(f: => Boolean) =
		Invariant(() => f, () => List())

	protected def invariant(errorParams: => List[Any])(f: => Boolean) =
		Invariant(() => f, () => errorParams)

	protected implicit def toPostCond[R](f: => R) = PostCond(() => f)

	protected def preCondition[R](condition: => Boolean)(f: => R): R =
		preCondition[R](condition, "")(f)

	protected def preCondition[R](condition: => Boolean, name: String)(f: => R): R = {
		if (!condition)
			throw new PreCondidionViolationException(name)
		f
	}

	private[activate] def isValidable =
		invariants.nonEmpty

	private[activate] def validateOnCreate =
		try {
			EntityValidation.validateOnCreate(this)
		} catch {
			case e: InvariantViolationException =>
				delete
				throw e;
		}

	def validate = {
		if (!isDeleted) {
			val invalid = invalidInvariants
			if (invalid.nonEmpty) {
				val invariantViolations =
					invalidInvariants.map(tuple => InvariantViolation(tuple._1, tuple._2))
				throw new InvariantViolationException(invariantViolations: _*)
			}
		}
	}

	private[this] def invalidInvariants =
		for ((name, invariant) <- invariants; if (!invariant.f()))
			yield (name, invariant.errorParams())

	protected def validationOptions: Option[Set[EntityValidationOption]] = None

}

object EntityValidation {

	private val globalOptions = new HashSet[EntityValidationOption]() with SynchronizedSet[EntityValidationOption]

	private val transactionOptionsMap =
		new ReferenceWeakKeyMap[Transaction, Set[EntityValidationOption]]() with SynchronizedMap[Transaction, Set[EntityValidationOption]]

	private var threadOptionsThreadLocal: ThreadLocal[Option[Set[EntityValidationOption]]] = _

	removeAllCustomOptions

	def removeAllCustomOptions = synchronized {
		globalOptions.clear
		globalOptions += onWrite
		globalOptions += onCreate
		transactionOptionsMap.clear
		threadOptionsThreadLocal =
			new ThreadLocal[Option[Set[EntityValidationOption]]]() {
				override def initialValue = None
			}
	}

	def addGlobalOption(option: EntityValidationOption) =
		globalOptions += option

	def removeGlobalOption(option: EntityValidationOption) =
		globalOptions -= option

	def setGlobalOptions(options: Set[EntityValidationOption]) = {
		globalOptions.clear
		globalOptions ++= options
	}

	def getGlobalOptions =
		globalOptions.toSet

	def addTransactionOption(option: EntityValidationOption)(implicit ctx: ActivateContext) =
		transactionOptionsMap.synchronized {
			val key = ctx.currentTransaction
			val old = transactionOptionsMap.getOrElse(key, Set())
			transactionOptionsMap.put(key, old + option)
		}

	def removeTransactionOption(option: EntityValidationOption)(implicit ctx: ActivateContext) =
		transactionOptionsMap.synchronized {
			val key = ctx.currentTransaction
			val old = transactionOptionsMap.getOrElse(key, Set())
			transactionOptionsMap.put(key, old - option)
		}

	def setTransactionOptions(options: Set[EntityValidationOption])(implicit ctx: ActivateContext) =
		transactionOptionsMap.put(ctx.currentTransaction, options)

	def getTransactionOptions(implicit ctx: ActivateContext) =
		transactionOptionsMap.get(ctx.currentTransaction)

	def addThreadOption(option: EntityValidationOption) =
		threadOptionsThreadLocal.set(Option(threadOptionsThreadLocal.get.getOrElse(Set()) + option))

	def removeThreadOption(option: EntityValidationOption) =
		threadOptionsThreadLocal.set(Option(threadOptionsThreadLocal.get.getOrElse(Set()) - option))

	def setThreadOptions(options: Set[EntityValidationOption]) =
		threadOptionsThreadLocal.set(Option(options))

	def getThreadOptions =
		threadOptionsThreadLocal.get

	private def optionsFor(obj: Entity): Set[EntityValidationOption] =
		optionsFor(obj, obj.context.currentTransaction)

	private def transactionOptions(transaction: Transaction): Option[Set[EntityValidationOption]] = {
		transaction match {
			case nested: NestedTransaction =>
				transactionOptionsMap.get(transaction).orElse(transactionOptions(nested.parent))
			case normal: Transaction =>
				transactionOptionsMap.get(transaction)
		}
	}

	private def optionsFor(obj: Entity, transaction: Transaction): Set[EntityValidationOption] =
		obj.validationOptions.getOrElse {
			transactionOptions(transaction).getOrElse {
				threadOptionsThreadLocal.get.getOrElse {
					globalOptions.toSet
				}
			}
		}

	private def validateIfHasOption(obj: Entity, option: EntityValidationOption) =
		if (obj.isValidable && optionsFor(obj).contains(option))
			obj.validate

	private[activate] def validateOnWrite(obj: Entity) =
		validateIfHasOption(obj, onWrite)

	private[activate] def validateOnRead(obj: Entity) =
		validateIfHasOption(obj, onRead)

	private[activate] def validateOnCreate(obj: Entity) =
		validateIfHasOption(obj, onCreate)

	private[activate] def validateOnTransactionEnd(entities: List[Entity], transaction: Transaction): Unit =
		entities.foreach(validateOnTransactionEnd(_, transaction))

	private def validateOnTransactionEnd(obj: Entity, transaction: Transaction): Unit =
		if (obj.isValidable && optionsFor(obj, transaction).contains(onTransactionEnd))
			obj.validate

}