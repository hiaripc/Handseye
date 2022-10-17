package org.pytorch.demo.handseye

import android.service.textservice.SpellCheckerService

class MySpellCheckerService : SpellCheckerService(){
    private val mySpellCheckerSession : MySpellCheckerSession = MySpellCheckerSession()
    override fun createSession(): Session {
        return mySpellCheckerSession
    }
}