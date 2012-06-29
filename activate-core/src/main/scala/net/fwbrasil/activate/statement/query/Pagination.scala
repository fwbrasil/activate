package net.fwbrasil.activate.statement.query

class Pagination[S](result: List[S]) {
	def navigator(pageSize: Int) =
		new PaginationNavigator(result, pageSize)
}

class PaginationNavigator[S](result: List[S], val pageSize: Int) extends Iterator[List[S]] {
	private val pages =
		result.grouped(pageSize).toList

	val numberOfResults =
		result.size

	val numberOfPages =
		pages.size

	def hasNext =
		_currentPage + 1 < numberOfPages

	def next =
		page(_currentPage + 1)

	def page(number: Int) = {
		_currentPage = number
		pages(_currentPage)
	}

	private var _currentPage = -1
	def currentPage =
		page(_currentPage)
	def firstPage =
		page(0)
	def lastPage =
		page(numberOfPages)
}