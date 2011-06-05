package net.fwbrasil.activate.util

object GCUtil {

    val runtime = Runtime.getRuntime
    
	def runGC =
        for (i <- 0 until 4) runGCInternal
         
    private[this] def runGCInternal = {
        var usedMem1 = usedMemory
        var usedMem2 = Long.MaxValue;
        for (i <- 0 until 500; if(usedMem1 < usedMem2))
        {
            runtime.runFinalization ();
            runtime.gc;
            Thread.`yield`;
            
            usedMem2 = usedMem1;
            usedMem1 = usedMemory;
        }
    }
    
	private[this] def usedMemory =
        runtime.totalMemory - runtime.freeMemory    
}