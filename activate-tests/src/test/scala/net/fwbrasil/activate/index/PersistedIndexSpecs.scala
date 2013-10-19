//package net.fwbrasil.activate.index
//
//import net.fwbrasil.activate.ActivateTestContext
//
//class PersistedIndexSpecs extends IndexSpecs {
//
//    type I = PersistedIndex[ActivateTestContext#ActivateTestEntity, ActivateTestContext#EntityByIntValue, Int] 
//    
//    def indexFor(context: ActivateTestContext) =
//        context.persistedIndexActivateTestEntityByIntValue.asInstanceOf[I]
//}