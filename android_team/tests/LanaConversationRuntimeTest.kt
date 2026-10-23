package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaConversationRuntimeTest {
    private class Transport : LanaConversationRuntime.Transport {
        val callbacks = mutableListOf<(String?,String?)->Unit>(); var cancellations = 0
        override fun ask(request: LanaRuntimeRequest, complete: (String?,String?)->Unit): LanaConversationRuntime.Cancel {
            callbacks.add(complete); return LanaConversationRuntime.Cancel { cancellations++ }
        }
    }
    private class Speaker : LanaConversationRuntime.Speaker {
        var complete: ((String?)->Unit)? = null; var started: (()->Unit)? = null; var calls=0; var cancellations=0
        override fun speak(text:String,voice:LanaRuntimeVoice,started:()->Unit,complete:(String?)->Unit):LanaConversationRuntime.Cancel {
            calls++; this.complete=complete; this.started=started; return LanaConversationRuntime.Cancel { cancellations++ }
        }
    }
    private fun request()=LanaRuntimeRequest("https://example.invalid","secret","id","question","prompt",true,listOf("chosen"))
    private fun voice(enabled:Boolean=true)=LanaRuntimeVoice(enabled,true,"https://example.invalid","secret","",1f)
    @Test fun answerIsAcceptedBeforeAudioAndFinalEventsAreConsumedOnce() {
        val t=Transport();val s=Speaker();var commits=0;val r=LanaConversationRuntime(t,s,{_,_-> commits++;true})
        r.ask(request(),voice(),7);assertEquals(LanaConversationRuntime.Phase.THINKING,r.snapshot.phase)
        t.callbacks[0]("answer",null);assertEquals(1,commits);assertEquals(1,s.calls)
        t.callbacks[0]("duplicate",null);assertEquals(1,commits);assertEquals(1,s.calls)
        s.started!!();assertEquals(LanaConversationRuntime.Phase.SPEAKING,r.snapshot.phase)
        s.complete!!(null);assertEquals(LanaConversationRuntime.Phase.READY,r.snapshot.phase)
        s.complete!!("late failure");assertEquals(LanaConversationRuntime.Phase.READY,r.snapshot.phase)
    }
    @Test fun stopRejectsLateNetworkAnswerWithoutPersistenceOrSpeech() {
        val t=Transport();val s=Speaker();var commits=0;val r=LanaConversationRuntime(t,s,{_,_-> commits++;true})
        r.ask(request(),voice(),7);r.stop();t.callbacks[0]("late",null)
        assertEquals(0,commits);assertEquals(0,s.calls);assertEquals(1,t.cancellations)
        assertEquals(LanaConversationRuntime.Phase.STOPPED,r.snapshot.phase)
    }
    @Test fun stoppedSpeechCannotReturnToListeningOrReady() {
        val t=Transport();val s=Speaker();val r=LanaConversationRuntime(t,s)
        r.say("local answer",voice(),3);val done=s.complete!!;r.stop();done(null)
        assertEquals(1,s.cancellations);assertEquals(LanaConversationRuntime.Phase.STOPPED,r.snapshot.phase)
    }
    @Test fun observerCanDetachAndReattachWithoutRepeatingRequest() {
        val t=Transport();val s=Speaker();val r=LanaConversationRuntime(t,s)
        var events=0;r.observe {events++};r.ask(request(),voice(false),7);r.observe(null)
        t.callbacks[0]("saved answer",null);val before=events
        r.observe {events++;assertEquals("saved answer",it.answer)}
        assertEquals(before+1,events);assertEquals(1,t.callbacks.size);assertEquals(0,s.calls)
    }
    @Test fun networkFailureDoesNotRetryAndVoiceFailureKeepsAnswer() {
        val t=Transport();val s=Speaker();val r=LanaConversationRuntime(t,s)
        r.ask(request(),voice(),1);t.callbacks[0](null,"network failed")
        assertEquals(1,t.callbacks.size);assertEquals(0,s.calls);assertEquals(LanaConversationRuntime.Phase.ERROR,r.snapshot.phase)
        r.say("visible answer",voice(),2);s.complete!!("audio failed")
        assertEquals("visible answer",r.snapshot.answer);assertEquals("audio failed",r.snapshot.error)
    }
    @Test fun textOnlyAndFailedContextSaveAreReportedTruthfully() {
        val t=Transport();val s=Speaker();val r=LanaConversationRuntime(t,s,{_,_->false})
        r.ask(request(),voice(false),7);t.callbacks[0]("answer",null)
        assertTrue(r.snapshot.textOnly);assertFalse(r.snapshot.contextSaved);assertEquals(0,s.calls)
        assertEquals(LanaConversationRuntime.Phase.READY,r.snapshot.phase)
    }
    @Test fun replacingRequestCannotAllowOldResultIntoNewTurn() {
        val t=Transport();val s=Speaker();val r=LanaConversationRuntime(t,s)
        r.ask(request(),voice(false),1);r.ask(request(),voice(false),2)
        t.callbacks[0]("old",null);assertEquals("",r.snapshot.answer)
        t.callbacks[1]("new",null);assertEquals("new",r.snapshot.answer);assertEquals(2L,r.snapshot.clientTicket)
    }
    @Test fun credentialsNeverAppearInConfigurationToString() {
        assertFalse(request().toString().contains("secret"));assertFalse(voice().toString().contains("secret"))
    }
    @Test fun synchronousTransportAndSpeakerCallbacksDoNotLeaveStaleCancellation() {
        var cancelled=0
        val t=object:LanaConversationRuntime.Transport {
            override fun ask(request:LanaRuntimeRequest,complete:(String?,String?)->Unit):LanaConversationRuntime.Cancel {complete("answer",null);return LanaConversationRuntime.Cancel{cancelled++}}
        }
        val s=object:LanaConversationRuntime.Speaker {
            override fun speak(text:String,voice:LanaRuntimeVoice,started:()->Unit,complete:(String?)->Unit):LanaConversationRuntime.Cancel {started();complete(null);return LanaConversationRuntime.Cancel{cancelled++}}
        }
        val r=LanaConversationRuntime(t,s);r.ask(request(),voice(),5)
        assertEquals(LanaConversationRuntime.Phase.READY,r.snapshot.phase);assertEquals(2,cancelled)
        r.stop();assertEquals(2,cancelled)
    }
    @Test fun throwingTransportAndSpeakerBecomeErrorsWithoutLosingText() {
        val t=object:LanaConversationRuntime.Transport {
            override fun ask(request:LanaRuntimeRequest,complete:(String?,String?)->Unit):LanaConversationRuntime.Cancel {throw IllegalStateException("private provider detail")}
        }
        val s=object:LanaConversationRuntime.Speaker {
            override fun speak(text:String,voice:LanaRuntimeVoice,started:()->Unit,complete:(String?)->Unit):LanaConversationRuntime.Cancel {throw IllegalStateException("private device detail")}
        }
        val r=LanaConversationRuntime(t,s);r.ask(request(),voice(),1)
        assertEquals(LanaConversationRuntime.Phase.ERROR,r.snapshot.phase);assertFalse(r.snapshot.error!!.contains("private"))
        r.say("answer survives",voice(),2)
        assertEquals(LanaConversationRuntime.Phase.ERROR,r.snapshot.phase);assertEquals("answer survives",r.snapshot.answer)
    }
    @Test fun stopFromObserverBeforeDispatchDoesNotCallTransport() {
        val t=Transport();val s=Speaker();val r=LanaConversationRuntime(t,s)
        r.observe { if(it.phase==LanaConversationRuntime.Phase.THINKING) r.stop() }
        r.ask(request(),voice(),1);assertEquals(0,t.callbacks.size)
        assertEquals(LanaConversationRuntime.Phase.STOPPED,r.snapshot.phase)
    }
}
