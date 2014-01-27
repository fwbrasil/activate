package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateTestContext

class PersistedIndexSpecs extends IndexSpecs {

    def indexFor(context: ActivateTestContext) =
        context.persistedIndexActivateTestEntityByIntValue
}