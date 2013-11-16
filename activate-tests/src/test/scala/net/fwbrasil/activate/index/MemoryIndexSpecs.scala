package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateTestContext

class MemoryIndexSpecs extends IndexSpecs {

    def indexFor(context: ActivateTestContext) =
        context.indexActivateTestEntityByIntValue
} 