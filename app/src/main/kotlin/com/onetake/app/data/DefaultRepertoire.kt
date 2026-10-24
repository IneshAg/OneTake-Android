package com.onetake.app.data

import com.onetake.app.models.PracticePiece
import com.onetake.app.models.ScriptLine
import com.onetake.app.models.ScriptScene

object DefaultRepertoire {
    val sampleScene = ScriptScene(
        id = "default-repertoire-scene-1",
        number = 1,
        title = "The Audition",
        heading = "INT. INTERROGATION ROOM - NIGHT",
        lines = listOf(
            ScriptLine(
                id = "line-1",
                character = "DETECTIVE",
                text = "Where were you on the night of the fourteenth?",
                isMine = false,
            ),
            ScriptLine(
                id = "line-2",
                character = "SUSPECT",
                text = "I told you already. I was at the diner on Fourth Street until midnight.",
                isMine = true,
            ),
            ScriptLine(
                id = "line-3",
                character = "DETECTIVE",
                text = "The diner closed at ten. The manager says nobody was there.",
                isMine = false,
            ),
            ScriptLine(
                id = "line-4",
                character = "SUSPECT",
                text = "Then check the security cameras outside. I know what I saw.",
                isMine = true,
            ),
            ScriptLine(
                id = "line-5",
                character = "DETECTIVE",
                text = "We did check. That's why you're sitting in this chair right now.",
                isMine = false,
            ),
            ScriptLine(
                id = "line-6",
                character = "SUSPECT",
                text = "I have nothing to hide. Call my lawyer if you don't believe me.",
                isMine = true,
            ),
        ),
        estimatedSeconds = 45,
    )

    val defaultPiece = PracticePiece(
        id = "default-practice-audition-piece",
        title = "The Audition",
        detail = "Detective & Suspect · 2-person dramatic scene",
        linesLearned = 0,
        linesTotal = 6,
        character = "SUSPECT",
        scene = sampleScene,
    )
}
