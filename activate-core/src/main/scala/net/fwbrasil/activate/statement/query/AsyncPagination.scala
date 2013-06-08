package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.entity.IntEntityValue

class Pagination[S](query: OrderedQuery[S]) {
    def navigator(pageSize: Int) =
        new PaginationNavigator(query, pageSize)
}

class PaginationNavigator[S](query: OrderedQuery[S], pageSize: Int) {

    val select1 = Select(SimpleValue[Int](() => 1, IntEntityValue(_)))
    val numberOfResults = new Query[Int](query.from, query.where, select1).execute.size
    val numberOfPages = (numberOfResults / pageSize) + (if (numberOfResults % pageSize > 0) 1 else 0)

    def hasNext =
        _currentPage + 1 < numberOfPages

    def next =
        page(_currentPage + 1)

    def page(number: Int) = {
    	if (number < 0 || number >= numberOfPages)
    		throw new IndexOutOfBoundsException
        _currentPage = number
        val offset = _currentPage * pageSize
        val pageQuery =
            new LimitedOrderedQuery[S](
                query.from,
                query.where,
                query.select,
                query.orderByClause.get,
                pageSize,
                Some(offset))
        pageQuery.execute
    }

    private var _currentPage = -1
    def currentPage =
        page(_currentPage)
    def firstPage =
        page(0)
    def lastPage =
        page(numberOfPages)
}