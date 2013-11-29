package net.fwbrasil.activate.entity

import language.postfixOps
import net.fwbrasil.radon.ref.RefListener
import scala.collection.SeqLike
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.util.ManifestUtil._
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.ActivateContext
import scala.collection.mutable.HashSet
import java.util.concurrent.atomic.AtomicBoolean
import net.fwbrasil.radon.transaction.NestedTransaction
import net.fwbrasil.activate.OptimisticOfflineLocking
import net.fwbrasil.activate.statement.StatementMocks
import java.util.concurrent.ConcurrentSkipListSet
import com.google.common.collect.MapMaker
import net.fwbrasil.activate.statement.Criteria

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

case class Invariant[E <: BaseEntity: Manifest](
    f: () => Boolean,
    errorParams: () => List[Any],
    properties: () => List[String],
    exceptionOption: Option[Exception] = None)

abstract class ViolationExceptions(violations: String*) extends Exception {
    override def toString = violations.toString
}
case class InvariantViolationException(violations: InvariantViolation*) extends Exception {
    override def toString = violations.toString
}
case class PreCondidionViolationException(violations: String*) extends ViolationExceptions(violations: _*)
case class PostCondidionViolationException(violations: String*) extends ViolationExceptions(violations: _*)

case class InvariantViolation(invariantName: String, errorParams: List[Any], properties: List[String])

object EntityValidationOption extends Enumeration {
    case class EntityValidationOption(name: String) extends Val
    val onWrite = EntityValidationOption("onWrite")
    val onRead = EntityValidationOption("onRead")
    val onCreate = EntityValidationOption("onCreate")
    val onTransactionEnd = EntityValidationOption("onTransactionEnd")
}
import EntityValidationOption._

trait EntityValidation {
    this: BaseEntity =>

    @transient
    private var _invariants: List[(String, Invariant[_])] = null

    protected implicit class onInvariants(on: On) {

        private def properties = on.vars.map(_.name).toList

        def invariant(f: => Boolean) =
            Invariant[EntityValidation.this.type](() => f, () => List(), () => properties)

        def invariant(errorParams: => List[Any])(f: => Boolean) =
            Invariant[EntityValidation.this.type](() => f, () => errorParams, () => properties)

        def invariant(exception: Exception)(f: => Boolean) =
            Invariant[EntityValidation.this.type](() => f, () => List(), () => properties, exceptionOption = Some(exception))

        def invariant(invariant: Invariant[_]) =
            invariant.copy(properties = () => properties)
    }

    private[activate] def invariants = {
        if (_invariants == null) {
            val metadata = this.entityMetadata
            if (metadata.invariantMethods.nonEmpty) {
                initializeListener
                _invariants = for (method <- metadata.invariantMethods)
                    yield (method.getName, method.invoke(this).asInstanceOf[Invariant[BaseEntity]])
            } else
                _invariants = List()
        }
        _invariants
    }

    @transient
    private var _listener: RefListener[Any] = null

    private def listener = {
        if (_listener == null) {
            _listener = new RefListener[Any] with Serializable {
                private val isValidating = new AtomicBoolean(false)
                override def notifyPut(ref: Ref[Any], obj: Option[Any]) =
                    if (EntityValidation.this.isInitialized) {
                        EntityValidation.validateOnWrite(EntityValidation.this)
                    }
                override def notifyGet(ref: Ref[Any]) =
                    if (EntityValidation.this.isInitialized)
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
        for (ref <- vars if ref.name != OptimisticOfflineLocking.versionVarName)
            ref.addWeakListener(listener)

    protected def invariant(f: => Boolean) =
        Invariant[this.type](() => f, () => List(), () => List())

    protected def invariant(errorParams: => List[Any])(f: => Boolean) =
        Invariant[this.type](() => f, () => errorParams, () => List())

    protected def invariant(exception: Exception)(f: => Boolean) =
        Invariant[this.type](() => f, () => List(), () => List(), exceptionOption = Some(exception))

    import language.implicitConversions

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
            case e: Throwable =>
                delete
                throw e;
        }

    def validate = {
        if (!isDeleted) {
            val invalid = invalidInvariants
            if (invalid.nonEmpty) {
                invalid.map(_._3).flatten.headOption.map(throw _)
                val invariantViolations =
                    invalidInvariants.map(tuple => InvariantViolation(tuple._1, tuple._2, tuple._4))
                throw new InvariantViolationException(invariantViolations: _*)
            }
        }
    }

    private def invalidInvariants =
        for ((name, invariant) <- invariants; if (!invariant.f()))
            yield (name, invariant.errorParams(), invariant.exceptionOption, invariant.properties())

    protected def validationOptions: Option[Set[EntityValidationOption]] = None

    private def emailPattern =
        "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    protected def email(string: => String) =
        invariant(string != null && string.matches(emailPattern))

    protected def notEmpty[T](iterable: => Iterable[T]) =
        invariant(iterable != null && iterable.nonEmpty)

    protected def notNull(obj: => Any) =
        invariant(obj != null)

    protected def unique(criterias: (this.type => Any)*)(implicit m: Manifest[this.type]) = {
        val ctx = context
        import ctx._
        invariant {
            val found =
                if (criterias.isEmpty)
                    select[this.type].where()
                else
                    select[this.type].where { entity =>
                        val entityCriterias =
                            for (criteria <- criterias) yield {
                                criteria(entity)
                                implicit val tval = StatementMocks.lastFakeVarCalled.get.tval.asInstanceOf[Option[Any] => EntityValue[Any]]
                                val value = criteria(this)
                                if (value == null)
                                    criteria(entity) isNull
                                else
                                    criteria(entity) :== value
                            }
                        entityCriterias.tail.foldLeft(entityCriterias.head)((base, criteria) => base :&& criteria)
                    }
            found.filter(_ != this).isEmpty
        }
    }

}

object EntityValidation {

    private val globalOptions = new ConcurrentSkipListSet[EntityValidationOption]()

    private val transactionOptionsMap =
        (new MapMaker).weakKeys.makeMap[Transaction, Set[EntityValidationOption]]

    private var threadOptionsThreadLocal: ThreadLocal[Option[Set[EntityValidationOption]]] = _

    removeAllCustomOptions

    def removeAllCustomOptions = synchronized {
        globalOptions.clear
        globalOptions.add(onWrite)
        globalOptions.add(onCreate)
        transactionOptionsMap.clear
        threadOptionsThreadLocal =
            new ThreadLocal[Option[Set[EntityValidationOption]]]() {
                override def initialValue = None
            }
    }

    def addGlobalOption(option: EntityValidationOption): Unit =
        globalOptions.add(option)

    def removeGlobalOption(option: EntityValidationOption): Unit =
        globalOptions.add(option)

    def setGlobalOptions(options: Set[EntityValidationOption]): Unit = {
        import scala.collection.JavaConversions._
        globalOptions.clear
        globalOptions.addAll(options)
    }

    def getGlobalOptions: Set[EntityValidationOption] = {
        import scala.collection.JavaConversions._
        globalOptions.toSet
    }

    def addTransactionOption(option: EntityValidationOption)(implicit ctx: ActivateContext): Unit = {
        val key = ctx.currentTransaction
        val old = Option(transactionOptionsMap.get(key)).getOrElse(Set())
        transactionOptionsMap.put(key, old + option)
    }

    def removeTransactionOption(option: EntityValidationOption)(implicit ctx: ActivateContext): Unit = {
        val key = ctx.currentTransaction
        val old = Option(transactionOptionsMap.get(key)).getOrElse(Set())
        transactionOptionsMap.put(key, old - option)
    }

    def setTransactionOptions(options: Set[EntityValidationOption])(implicit ctx: ActivateContext): Unit =
        transactionOptionsMap.put(ctx.currentTransaction, options)

    def getTransactionOptions(implicit ctx: ActivateContext): Option[Set[EntityValidationOption]] =
        Option(transactionOptionsMap.get(ctx.currentTransaction))

    def addThreadOption(option: EntityValidationOption): Unit =
        threadOptionsThreadLocal.set(Option(threadOptionsThreadLocal.get.getOrElse(Set()) + option))

    def removeThreadOption(option: EntityValidationOption): Unit =
        threadOptionsThreadLocal.set(Option(threadOptionsThreadLocal.get.getOrElse(Set()) - option))

    def setThreadOptions(options: Set[EntityValidationOption]): Unit =
        threadOptionsThreadLocal.set(Option(options))

    def getThreadOptions: Option[Set[EntityValidationOption]] =
        threadOptionsThreadLocal.get

    private def optionsFor(obj: BaseEntity): Set[EntityValidationOption] =
        optionsFor(obj, obj.context.currentTransaction)

    private def transactionOptions(transaction: Transaction): Option[Set[EntityValidationOption]] = {
        transaction match {
            case nested: NestedTransaction =>
                Option(transactionOptionsMap.get(transaction)).orElse(transactionOptions(nested.parent))
            case normal: Transaction =>
                Option(transactionOptionsMap.get(transaction))
        }
    }

    private def optionsFor(obj: BaseEntity, transaction: Transaction): Set[EntityValidationOption] =
        obj.validationOptions.getOrElse {
            transactionOptions(transaction).getOrElse {
                threadOptionsThreadLocal.get.getOrElse {
                    import scala.collection.JavaConversions._
                    globalOptions.toSet
                }
            }
        }

    private def validateIfHasOption(obj: BaseEntity, option: EntityValidationOption) =
        if (obj.isValidable && optionsFor(obj).contains(option))
            obj.validate

    private[activate] def validateOnWrite(obj: BaseEntity) =
        validateIfHasOption(obj, onWrite)

    private[activate] def validateOnRead(obj: BaseEntity) =
        validateIfHasOption(obj, onRead)

    private[activate] def validateOnCreate(obj: BaseEntity) =
        validateIfHasOption(obj, onCreate)

    private[activate] def validatesOnTransactionEnd(obj: BaseEntity, transaction: Transaction) =
        obj.isValidable && optionsFor(obj, transaction).contains(onTransactionEnd)

}