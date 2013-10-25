package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateTestContext

class MemoryIndexSpecs extends IndexSpecs {

    type I = MemoryIndex[ActivateTestContext#ActivateTestEntity, Int] 
    
    def indexFor(context: ActivateTestContext) =
        context.indexActivateTestEntityByIntValue.asInstanceOf[I]
} 