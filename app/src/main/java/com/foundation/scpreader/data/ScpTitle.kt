package com.foundation.scpreader.data

/**
 * Parses the primary SCP number from a narration title, e.g. `SCP-173`, `SCP 2000`,
 * `SCP-7980: "Z is for Ziggurat"`. Returns the first match (compilations/tales that list several,
 * or none, resolve to their first number or null). Shared by the YouTube and Apple sources so
 * both map episodes to SCP numbers identically.
 */
private val SCP_NUMBER = Regex("""\bSCP[-\s]?0*(\d{1,5})""", RegexOption.IGNORE_CASE)

fun parseScpNumber(title: String): Int? =
    SCP_NUMBER.find(title)?.groupValues?.get(1)?.toIntOrNull()
