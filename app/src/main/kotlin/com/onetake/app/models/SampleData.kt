package com.onetake.app.models

import java.time.LocalDate
import java.time.LocalDateTime

/** Same sample content as iOS SampleData.swift (and the Figma screens). */
object SampleData {
    val now: LocalDateTime = LocalDateTime.now()

    fun at(days: Long = 0, hour: Int, minute: Int = 0): LocalDateTime =
        LocalDate.now().plusDays(days).atTime(hour, minute)

    val apartment = ScriptScene(
        number = 1,
        title = "Scene 1",
        heading = "INT. APARTMENT — NIGHT",
        lines = listOf(
            ScriptLine(character = "ARJUN", text = "I thought you understood why I had to leave.", isMine = true),
            ScriptLine(character = "MAYA", text = "You never gave me a chance to understand.", isMine = false),
            ScriptLine(character = "ARJUN", text = "Because every time I tried, you turned away.", isMine = true),
            ScriptLine(character = "MAYA", text = "So you packed a bag and let me hear it from your sister.", isMine = false),
            ScriptLine(character = "ARJUN", text = "(quietly) I didn't know how to say it to your face.", isMine = true),
            ScriptLine(character = "MAYA", text = "You still don't.", isMine = false),
        ),
        estimatedSeconds = 90,
    )

    val goodbye = ScriptScene(
        number = 2,
        title = "Scene 2",
        heading = "EXT. STATION — DAWN",
        lines = listOf(
            ScriptLine(character = "MAYA", text = "Every summer you said next year.", isMine = false),
            ScriptLine(character = "ARJUN", text = "And every year I meant it.", isMine = true),
        ),
        estimatedSeconds = 52,
    )

    val lastSummer = Audition(
        project = "The Last Summer",
        projectType = "Feature film",
        role = "Arjun",
        roleSize = "Lead",
        castingOffice = "Northlight Casting",
        due = at(days = 2, hour = 10),
        asks = listOf("Slate first", "Landscape", "Full length", "Both profiles", "Under 3 min", "Actors Access"),
        pieces = listOf(
            Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front · taken Sep 20", isDone = true),
            Piece(kind = PieceKind.Slate, title = "Slate", detail = "Kabir Sharma · 5′ 11″ · Mumbai · 0:05", isDone = true),
            Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "Apartment Confrontation · not shot yet", isDone = false),
            Piece(kind = PieceKind.Scene, title = "Scene 2", detail = "Goodbye · any order", isDone = false),
        ),
        scenes = listOf(apartment, goodbye),
        destination = Destination.ActorsAccess,
        status = AuditionStatus.Due,
    )

    val northbound = Audition(
        project = "Northbound",
        projectType = "Series",
        role = "Supporting role",
        roleSize = "Supporting",
        castingOffice = "Ridge Casting",
        due = at(days = 1, hour = 14, minute = 30),
        asks = listOf("Callback tape", "Slate first"),
        pieces = listOf(
            Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front", isDone = true),
            Piece(kind = PieceKind.Slate, title = "Slate", detail = "0:05", isDone = true),
            Piece(kind = PieceKind.FullLength, title = "Full length", detail = "0:06", isDone = true),
            Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "Take 2 chosen", isDone = true),
            Piece(kind = PieceKind.Scene, title = "Watch it once", detail = "Then send", isDone = false),
        ),
        scenes = listOf(apartment),
        destination = Destination.ActorsAccess,
        status = AuditionStatus.Ready,
    )

    val guiltyParty = Audition(
        project = "Guilty Party",
        projectType = "Drama",
        role = "Supporting role",
        roleSize = "Supporting",
        castingOffice = "Dev Casting",
        due = at(days = 7, hour = 11),
        asks = listOf("Slate first"),
        pieces = listOf(
            Piece(kind = PieceKind.Headshot, title = "Headshot", detail = "Front", isDone = true),
            Piece(kind = PieceKind.Slate, title = "Slate", detail = "", isDone = false),
            Piece(kind = PieceKind.FullLength, title = "Full length", detail = "", isDone = false),
            Piece(kind = PieceKind.Scene, title = "Scene 1", detail = "", isDone = false),
            Piece(kind = PieceKind.Scene, title = "Scene 2", detail = "", isDone = false),
        ),
        scenes = listOf(apartment),
        destination = Destination.CastingNetworks,
        status = AuditionStatus.Due,
    )

    val winterPlay = Audition(
        project = "Winter Play",
        projectType = "Theatre",
        role = "Ensemble",
        roleSize = "Ensemble",
        castingOffice = "City Rep",
        due = at(days = -13, hour = 18),
        asks = emptyList(),
        pieces = listOf(Piece(kind = PieceKind.Scene, title = "Monologue", detail = "", isDone = true)),
        scenes = emptyList(),
        destination = Destination.EmailOrLink,
        status = AuditionStatus.Sent,
        sentDate = at(days = -13, hour = 18),
    )

    val auditions = listOf(northbound, lastSummer, guiltyParty, winterPlay)

    val schedule = listOf(
        ScheduleItem(whenLabel = "TODAY", time = "6:00 PM", title = "The Last Summer", detail = "Scene practice · 20 min"),
        ScheduleItem(whenLabel = "TOMORROW", time = "2:30 PM", title = "Northbound", detail = "Callback tape due"),
        ScheduleItem(whenLabel = "SUN 11", time = "11:00 AM", title = "Guilty Party", detail = "Record the slate"),
    )

    val takes = listOf(
        Take(number = 1, durationSeconds = 88, recordedAt = now.minusMinutes(20), isCircled = false, notes = listOf("Rushed the top")),
        Take(number = 2, durationSeconds = 92, recordedAt = now.minusMinutes(12), isCircled = true, notes = listOf("Found the turn", "Felt true")),
        Take(number = 3, durationSeconds = 90, recordedAt = now, isCircled = false, notes = listOf("Eyeline drifted")),
    )

    val findings = listOf(
        TapeFinding(category = "Eyeline", title = "You looked into the lens", where = "Scene 1 · 0:41 · 4 s", advice = "Casting wants your eyes just beside the lens, on the reader."),
        TapeFinding(category = "Reader volume", title = "Maya is louder than you", where = "Scene 2 · 0:18 · +3 dB", advice = "Lower her track so casting hears you first."),
    )

    val practice = listOf(
        PracticePiece(title = "Kitchen Argument", detail = "Two-hander · you play Sam", linesLearned = 5, linesTotal = 6),
        PracticePiece(title = "Showcase monologue", detail = "Monologue · 1:10", linesLearned = 12, linesTotal = 12),
    )
}
