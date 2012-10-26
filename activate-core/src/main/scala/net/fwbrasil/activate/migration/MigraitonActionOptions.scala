package net.fwbrasil.activate.migration

trait IfNotExists[T <: IfNotExists[T]] {
	this: T =>
	// TODO This should be immutable
	private var _ifNotExists = false
	def ifNotExists = {
		_ifNotExists = true
		this
	}
	private[activate] def onlyIfNotExists =
		_ifNotExists
}

trait IfExists[T <: IfExists[T]] {
	this: T =>
	// TODO This should be immutable
	private var _ifExists = false
	def ifExists = {
		_ifExists = true
		this
	}
	private[activate] def onlyIfExists =
		_ifExists
}

case class IfNotExistsBag(actions: List[IfNotExists[_]]) 
extends IfNotExists[IfNotExistsBag]{
	override def ifNotExists = {
		actions.foreach(_.ifNotExists)
		this
	}
}

case class IfExistsBag(actions: List[IfExists[_]]) 
extends IfExists[IfExistsBag]{
	override def ifExists = {
		actions.foreach(_.ifExists)
		this
	}
}

trait Cascade {
	// TODO This should be immutable
	private var _cascade = false
	def cascade = {
		_cascade = true
		this
	}
	private[activate] def isCascade =
		_cascade
}

trait CascadeBag[T <: Cascade] extends Cascade {
	protected val actions: List[T]
	override def cascade = {
		actions.foreach(_.cascade)
		this
	}
}

case class IfExistsWithCascadeBag(actions: List[IfExists[_] with Cascade]) {
  def ifExists = {
    actions.foreach(_.ifExists)
    this
  }
  def cascade = {
    actions.foreach(_.cascade)
    this
  }
}