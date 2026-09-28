package com.lucho314.spotter.domain.calc

import com.lucho314.spotter.domain.model.Equipment
import com.lucho314.spotter.domain.model.GarminExerciseRef
import java.text.Normalizer

/**
 * Maps a Spotter exercise (English/Spanish name + equipment) to a Garmin FIT `exercise_category` /
 * `exercise_subtype` pair. Only category/subtype values verified against FIT SDK 21.217.0's
 * `ExerciseCategory`/`*ExerciseName` enums are used.
 *
 * Both [GarminActivityPlanner]'s input names are evaluated against an **ordered** rule list - the
 * first rule that matches either name wins. Modifier keywords used to pick a subtype ("incline",
 * "goblet", "close grip"...) are looked up across *both* names combined, since a caller may only
 * have one of the two populated with the detail that decides the subtype.
 *
 * No match (including deliberately unmapped exercises like "leg extension", which has no Garmin
 * category) returns `null` - the caller leaves the FIT field invalid rather than guessing.
 */
object GarminExerciseMapper {

    fun map(nameEn: String?, name: String?, equipment: Equipment?): GarminExerciseRef? {
        val normalizedEn = normalize(nameEn)
        val normalizedEs = normalize(name)
        if (normalizedEn == null && normalizedEs == null) return null
        val combined = listOfNotNull(normalizedEn, normalizedEs).joinToString(" ")
        val rule = RULES.firstOrNull { rule ->
            (normalizedEn != null && rule.matches(normalizedEn)) || (normalizedEs != null && rule.matches(normalizedEs))
        }
        return rule?.resolve?.invoke(combined, equipment)
    }

    private fun normalize(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutMarks = DIACRITICS.replace(decomposed, "")
        return withoutMarks.lowercase().replace('-', ' ')
    }

    private val DIACRITICS = Regex("\\p{Mn}+")

    private class Rule(private val keywords: List<String>, val resolve: (combinedText: String, equipment: Equipment?) -> GarminExerciseRef) {
        fun matches(text: String): Boolean = keywords.any { text.contains(it) }
    }

    private fun contains(text: String, vararg needles: String) = needles.any { text.contains(it) }

    // ExerciseCategory values (FIT SDK 21.217.0).
    private const val BENCH_PRESS = 0
    private const val CALF_RAISE = 1
    private const val CRUNCH = 6
    private const val CURL = 7
    private const val DEADLIFT = 8
    private const val FLYE = 9
    private const val HIP_RAISE = 10
    private const val LATERAL_RAISE = 14
    private const val LEG_CURL = 15
    private const val LUNGE = 17
    private const val PLANK = 19
    private const val PULL_UP = 21
    private const val PUSH_UP = 22
    private const val ROW = 23
    private const val SHOULDER_PRESS = 24
    private const val SHRUG = 26
    private const val SQUAT = 28
    private const val TRICEPS_EXTENSION = 30

    private val RULES: List<Rule> = listOf(
        Rule(listOf("leg press", "prensa")) { _, _ -> GarminExerciseRef(SQUAT, 0) },
        Rule(listOf("leg curl", "curl femoral", "femoral")) { _, _ -> GarminExerciseRef(LEG_CURL, 0) },
        Rule(listOf("romanian", "rumano")) { _, _ -> GarminExerciseRef(DEADLIFT, 23) },
        Rule(listOf("deadlift", "peso muerto")) { _, equipment ->
            val subtype = when (equipment) {
                Equipment.BARBELL -> 0
                Equipment.DUMBBELL -> 2
                else -> null
            }
            GarminExerciseRef(DEADLIFT, subtype)
        },
        Rule(listOf("hip thrust", "empuje de cadera", "glute bridge", "puente de gluteo")) { _, equipment ->
            GarminExerciseRef(HIP_RAISE, if (equipment == Equipment.BARBELL) 1 else 11)
        },
        Rule(
            listOf(
                "bench press", "press banca", "press de banca", "press plano", "press inclinado",
                "chest press", "press de pecho",
            ),
        ) { text, equipment ->
            val incline = contains(text, "incline", "inclinad")
            val subtype = when {
                incline && equipment == Equipment.BARBELL -> 8
                incline && equipment == Equipment.DUMBBELL -> 9
                incline -> null
                equipment == Equipment.BARBELL -> 1
                equipment == Equipment.DUMBBELL -> 6
                else -> null
            }
            GarminExerciseRef(BENCH_PRESS, subtype)
        },
        Rule(listOf("squat", "sentadilla")) { text, equipment ->
            val subtype = when {
                contains(text, "goblet") -> 37
                contains(text, "front", "frontal") -> 8
                equipment == Equipment.BARBELL -> 6
                else -> null
            }
            GarminExerciseRef(SQUAT, subtype)
        },
        Rule(listOf("lat pulldown", "pulldown", "jalon")) { text, _ ->
            val subtype = when {
                contains(text, "close", "cerrado") -> 5
                contains(text, "wide", "abierto") -> 25
                else -> 13
            }
            GarminExerciseRef(PULL_UP, subtype)
        },
        Rule(listOf("chin up", "dominada supina")) { _, _ -> GarminExerciseRef(PULL_UP, 39) },
        Rule(listOf("pull up", "dominada")) { _, _ -> GarminExerciseRef(PULL_UP, 38) },
        Rule(
            listOf(
                "shoulder press", "overhead press", "military press", "press militar",
                "press de hombro", "arnold",
            ),
        ) { text, equipment ->
            val subtype = when {
                contains(text, "arnold") -> 1
                contains(text, "military", "militar") -> 25
                equipment == Equipment.BARBELL -> 14
                equipment == Equipment.DUMBBELL -> 24
                else -> null
            }
            GarminExerciseRef(SHOULDER_PRESS, subtype)
        },
        Rule(listOf("lateral raise", "elevacion lateral", "elevaciones laterales", "vuelos laterales")) { _, equipment ->
            GarminExerciseRef(LATERAL_RAISE, if (equipment == Equipment.DUMBBELL) 34 else null)
        },
        Rule(listOf("front raise", "elevacion frontal", "elevaciones frontales")) { _, _ -> GarminExerciseRef(LATERAL_RAISE, 10) },
        Rule(listOf("hammer curl", "curl martillo")) { _, _ -> GarminExerciseRef(CURL, 16) },
        Rule(listOf("preacher", "curl scott", "predicador")) { _, _ -> GarminExerciseRef(CURL, 19) },
        Rule(listOf("curl")) { _, equipment ->
            val subtype = when (equipment) {
                Equipment.CABLE -> 8
                Equipment.DUMBBELL -> 37
                else -> null
            }
            GarminExerciseRef(CURL, subtype)
        },
        Rule(listOf("pushdown", "rope", "soga", "polea triceps")) { text, _ ->
            GarminExerciseRef(TRICEPS_EXTENSION, if (contains(text, "rope", "soga")) 19 else 39)
        },
        Rule(listOf("dip", "fondos")) { text, _ ->
            GarminExerciseRef(TRICEPS_EXTENSION, if (contains(text, "bench", "banco")) 0 else 40)
        },
        Rule(listOf("triceps", "tricep", "extension de triceps", "frances")) { text, equipment ->
            val overhead = contains(text, "overhead", "sobre la cabeza")
            GarminExerciseRef(TRICEPS_EXTENSION, if (overhead && equipment == Equipment.DUMBBELL) 15 else null)
        },
        Rule(listOf("row", "remo")) { _, equipment ->
            val subtype = when (equipment) {
                Equipment.BARBELL -> 45
                Equipment.DUMBBELL -> 2
                Equipment.CABLE -> 18
                else -> null
            }
            GarminExerciseRef(ROW, subtype)
        },
        Rule(listOf("crossover", "cruce de poleas")) { _, _ -> GarminExerciseRef(FLYE, 0) },
        Rule(listOf("fly", "flye", "apertura", "aperturas")) { text, equipment ->
            val incline = contains(text, "incline", "inclinad")
            val subtype = when {
                incline && equipment == Equipment.DUMBBELL -> 3
                equipment == Equipment.DUMBBELL -> 2
                else -> null
            }
            GarminExerciseRef(FLYE, subtype)
        },
        Rule(listOf("calf", "pantorrilla", "gemelo")) { text, _ ->
            GarminExerciseRef(CALF_RAISE, if (contains(text, "seated", "sentado")) 6 else 18)
        },
        Rule(listOf("bulgarian", "bulgara")) { _, _ -> GarminExerciseRef(LUNGE, 18) },
        Rule(listOf("lunge", "zancada", "estocada")) { text, equipment ->
            val subtype = when {
                contains(text, "walking", "caminando") -> 78
                equipment == Equipment.DUMBBELL -> 21
                equipment == Equipment.BARBELL -> 10
                else -> null
            }
            GarminExerciseRef(LUNGE, subtype)
        },
        Rule(listOf("shrug", "encogimiento")) { _, equipment ->
            val subtype = when (equipment) {
                Equipment.BARBELL -> 1
                Equipment.DUMBBELL -> 5
                else -> null
            }
            GarminExerciseRef(SHRUG, subtype)
        },
        Rule(listOf("push up", "pushup", "flexion", "flexiones", "lagartija")) { _, _ -> GarminExerciseRef(PUSH_UP, 77) },
        Rule(listOf("crunch", "abdominal")) { _, _ -> GarminExerciseRef(CRUNCH, 83) },
        Rule(listOf("plank", "plancha")) { _, _ -> GarminExerciseRef(PLANK, 43) },
    )
}
