# Test support

The **activate-test** module provides an infrastructure to ease writing tests with Activate.
It is possible to use the integration with your preferred test framework just mixing the ActivateTest trait.

``` scala
import net.fwbrasil.activate.test.ActivateTest

class MyTest extends Spec with ActivateTest {
	
	"some test" in activateTest {
		// test code
	}

	"some async test" in activateTestAsync {
		// async test code
	}

	"some async chain test" in activateTestAsyncChain { implicit ctx =>
		// test using the async context
	}
}
```

The activateTest methods provides isolation for each execution, so one test doesn't modify the behavior of the others. 

**Important:**
As the tests use a shared state (database), it is necessary to run them sequentially (only one thread).

## Strategies

It is possible to define the strategy used to isolate the test executions. Available strategies:

	transactionRollbackStrategy

This is the default strategy. After each test execution, rollbacks the transaction instead of performing a commit.

	cleanDatabaseStrategy

Before each test execution, deletes all entity instances.

	recreateDatabaseStrategy

Before each test execution, drops all tables and recreate them.

## Strategy definition

It is possible to define the default strategy for the test class:

``` scala
protected def defaultStrategy: ActivateTestStrategy = cleanDatabaseStrategy
```

Alternativelly, it is possible to define the strategy for each individual test:

``` scala
import net.fwbrasil.activate.test.ActivateTest

class MyTest extends Spec with ActivateTest {
	
	"some test" in activateTest(transactionRollbackStrategy) {
		// test code
	}

	"some async test" in activateTestAsync(recreateDatabaseStrategy) {
		// async test code
	}

	"some async chain test" in activateTestAsyncChain(cleanDatabaseStrategy) { implicit ctx =>
		// test using the async context
	}
}
```