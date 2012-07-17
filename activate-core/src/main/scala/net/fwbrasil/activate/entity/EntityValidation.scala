// TODO Reimplement! There are performance problems

package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.RefListener
import scala.collection.SeqLike
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.util.Reflection.toNiceObject

case class PostCond[R](f: () => R) {

	def postCond(condition: => Boolean): R =
		postCond(condition, "")

	def postCond(condition: => Boolean, name: String): R = {
		val result = f()
		require(condition, name)
		result
	}

	def postCond(condition: (R) => Boolean): R =
		postCond(condition, "")

	def postCond(condition: (R) => Boolean, name: String): R = {
		val result = f()
		require(condition(result), name)
		result
	}

	private[this] def require(f: => Boolean, name: String) =
		if (!f)
			throw new PostCondidionViolationException(name)
}

case class Invariant(f: () => Boolean)

class ViolationException(violations: String*) extends Exception {
	override def toString = violations.toString
}
case class InvariantViolationException(violations: String*) extends ViolationException(violations: _*)
case class PreCondidionViolationException(violations: String*) extends ViolationException(violations: _*)
case class PostCondidionViolationException(violations: String*) extends ViolationException(violations: _*)

trait ValidEntity {
	this: Entity =>

	@transient
	private var _invariants: List[(String, () => Boolean)] = null

	private[activate] def invariants = {
		if (_invariants == null) {
			val metadata = EntityHelper.getEntityMetadata(this.niceClass)
			if (metadata.invariantMethods.nonEmpty)
				initializeListener
			_invariants = for (method <- metadata.invariantMethods)
				yield (method.getName, method.invoke(this).asInstanceOf[Invariant].f)
		}
		_invariants
	}

	@transient
	private var _listener: RefListener[Any] = null

	def listener = {
		if (_listener == null) {
			_listener = new RefListener[Any] with Serializable {
				override def notifyPut(ref: Ref[Any], obj: Option[Any]) = {
					validate
				}
			}
		}
		_listener
	}
	private[this] def initializeListener: Unit =
		for (ref <- vars)
			ref.addWeakListener(listener)

	protected def invariant(f: => Boolean) =
		Invariant(() => f)

	protected implicit def toPostCond[R](f: => R) = PostCond(() => f)

	protected def preCond[R](condition: => Boolean)(f: => R): R =
		preCond[R](condition, "")(f)

	protected def preCond[R](condition: => Boolean, name: String)(f: => R): R = {
		if (!condition)
			throw new PreCondidionViolationException(name)
		f
	}

	def validate = {
		val invalid = invalidInvariants
		if (invalid.nonEmpty)
			throw new InvariantViolationException(invalid: _*)
	}

	private[this] def invalidInvariants = {
		val inv = invariants
		for ((name, function) <- inv; if (!function()))
			yield name
	}

}

trait ValidEntityContext {

	implicit def toNotNull(obj: Any) = NotNull(obj)
	case class NotNull(obj: Any) {
		def notNull =
			obj != null
	}

	implicit def toValidSeqLike[S <% SeqLike[_, _]](obj: S) = ValidSeqLike(obj)
	case class ValidSeqLike[S <% SeqLike[_, _]](obj: S) {
		def length(value: Int) =
			obj != null && obj.size == value
		def maxLength(value: Int) =
			obj != null && obj.size <= value
		def minLength(value: Int) =
			obj != null && obj.size >= value
		def notBlank =
			obj != null && obj.nonEmpty
	}

	implicit def toValidNumeric[N](obj: N)(implicit numeric: Numeric[N]) = ValidNumeric(obj)
	case class ValidNumeric[N](obj: N)(implicit numeric: Numeric[N]) {
		def range(a: N, b: N) =
			numeric.lteq(a, obj) && numeric.lteq(obj, b)
		def positive =
			numeric.signum(obj) >= 0
		def negative =
			numeric.signum(obj) < 0
	}

}