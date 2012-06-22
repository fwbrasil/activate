package net.fwbrasil.activate.statement.query

class Pagination[S](result: List[S]) {
	def navigator(pageSize: Int) =
		new PaginationNavigator(result, pageSize)
}

class PaginationNavigator[S](result: List[S], val pageSize: Int) extends Iterator[List[S]] {
	private val pages =
		result.grouped(pageSize).toList

	def numberOfPages =
		pages.size

	def hasNext =
		_currentPage + 1 < numberOfPages

	def next =
		goToPage(_currentPage + 1)

	def goToPage(number: Int) = {
		_currentPage = number
		pages(_currentPage)
	}

	private var _currentPage = -1
	def currentPage =
		goToPage(_currentPage)
	def firstPage =
		goToPage(0)
	def lastPage =
		goToPage(numberOfPages)
}