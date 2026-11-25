package ai.dreibus.lanalive

fun runLocalTurnGateChecks(){
    val cases=listOf<Pair<String,()->Unit>>(
        "first request" to { val g=LanaLocalTurnGate(); val a=g.begin()!!; check(g.accepts(a)&&g.busy()) },
        "reject overlapping" to { val g=LanaLocalTurnGate();g.begin();check(g.begin()==null) },
        "normal completion" to { val g=LanaLocalTurnGate();val a=g.begin()!!;check(g.finish(a)&&!g.busy()&&!g.accepts(a)) },
        "STOP suppresses result" to { val g=LanaLocalTurnGate();val a=g.begin()!!;check(g.cancel(a)&&!g.accepts(a)&&!g.finish(a)) },
        "STOP does not free busy worker" to { val g=LanaLocalTurnGate();val a=g.begin()!!;g.cancel(a);check(g.busy()&&g.begin()==null) },
        "restart after native exit" to { val g=LanaLocalTurnGate();val a=g.begin()!!;g.cancel(a);g.finish(a);val b=g.begin()!!;check(b>a&&g.accepts(b)) },
        "stale callbacks" to { val g=LanaLocalTurnGate();val a=g.begin()!!;g.finish(a);val b=g.begin()!!;check(!g.cancel(a)&&!g.finish(a)&&g.accepts(b)) },
        "double cancellation" to { val g=LanaLocalTurnGate();val a=g.begin()!!;check(g.cancel(a)&&!g.cancel(a)) },
        "service shutdown" to { val g=LanaLocalTurnGate();val a=g.begin()!!;check(g.close()==a&&!g.accepts(a)&&g.begin()==null&&!g.finish(a)&&g.begin()==null) },
        "1000 consecutive turns" to { val g=LanaLocalTurnGate();repeat(1000){val a=g.begin()!!;if(it%2==0){g.cancel(a);check(!g.finish(a))}else check(g.finish(a))};check(!g.busy()) }
    );cases.forEachIndexed{i,(n,t)->t();println("PASS "+(i+1)+": "+n)}
}
