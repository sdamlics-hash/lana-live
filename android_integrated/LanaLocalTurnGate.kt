package ai.dreibus.lanalive

/** Serial inference admission. Cancellation suppresses delivery but does not release native resources early. */
internal class LanaLocalTurnGate {
    private var next=0L
    private var active:Long?=null
    private var deliver=false
    private var closed=false
    @Synchronized fun begin():Long? {
        if(closed || active!=null)return null
        next++; active=next; deliver=true; return next
    }
    @Synchronized fun accepts(id:Long)=!closed && active==id && deliver
    @Synchronized fun cancel(id:Long):Boolean {
        if(active!=id || !deliver)return false
        deliver=false;return true
    }
    @Synchronized fun finish(id:Long):Boolean {
        if(active!=id)return false
        val valid=deliver && !closed;active=null;deliver=false;return valid
    }
    @Synchronized fun close():Long? {closed=true;deliver=false;return active}
    @Synchronized fun busy()=active!=null
}
