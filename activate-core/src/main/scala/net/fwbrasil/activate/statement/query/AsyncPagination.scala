package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.radon.transaction.TransactionalExecutionContext

class AsyncPagination[S](query: OrderedQuery[S]) {
    val select1 = Select(SimpleValue[Int](() => 1, IntEntityValue(_)))
    def navigator(pageSize: Int)(implicit ctx: TransactionalExecutionContext) =
        new Query[Int](query.from, query.where, select1).executeAsync.map {
            result => new AsyncPaginationNavigator(result.size, query, pageSize) 
        }
}

class AsyncPaginationNavigator[S](val numberOfResults: Int, query: OrderedQuery[S], pageSize: Int) {

    val numberOfPages = (numberOfResults / pageSize) + (if (numberOfResults % pageSize > 0) 1 else 0)

    def hasNext =
        _currentPage + 1 < numberOfPages

    def next(implicit ctx: TransactionalExecutionContext) =
        page(_currentPage + 1)

    def page(number: Int)(implicit ctx: TransactionalExecutionContext) = {
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
                () => pageSize,
                () => Some(offset))
        pageQuery.executeAsync
    }

    private var _currentPage = -1
    def currentPage(implicit ctx: TransactionalExecutionContext) =
        page(_currentPage)
    def firstPage(implicit ctx: TransactionalExecutionContext) =
        page(0)
    def lastPage(implicit ctx: TransactionalExecutionContext) =
        page(numberOfPages)
}