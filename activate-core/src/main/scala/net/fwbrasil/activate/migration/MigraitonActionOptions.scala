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

trait IfNotExistsBag[T] {
	val actions: List[{ def ifNotExists: T }]
	def ifNotExists = {
		actions.foreach(_.ifNotExists)
		this
	}
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

trait IfExistsBag[T] {
	val actions: List[{ def ifExists: T }]
	def ifExists = {
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

trait CascadeBag[T <: Cascade] {
	protected val actions: List[T]
	def cascade = {
		actions.foreach(_.cascade)
		this
	}
}