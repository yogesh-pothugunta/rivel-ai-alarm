package com.example.mission.camera

import android.content.Context

data class WakeObject(
    val name: String,
    val emoji: String,
    val description: String = "Scan this object with your camera to prove you're awake."
)

object WakeObjectPool {

    val ALL_OBJECTS: List<WakeObject> = listOf(
        WakeObject("Water Bottle", "🥤", "Find any water bottle, flask, or tumbler and point your camera."),
        WakeObject("Book", "📚", "Find any textbook, novel, or reading book."),
        WakeObject("Toothbrush", "🪥", "Head to the bathroom, grab your toothbrush, and scan it."),
        WakeObject("Shoe", "👟", "Locate a shoe or sneaker and frame it clearly in view."),
        WakeObject("Keys", "🔑", "Find house, room, or vehicle keys on your desk or hook."),
        WakeObject("Backpack", "🎒", "Find your study backpack or bag and capture it."),
        WakeObject("Pen", "🖊️", "Find a pen, pencil, or highlighter on your desk."),
        WakeObject("Headphones", "🎧", "Locate your headphones or earphone case."),
        WakeObject("Watch", "⌚", "Find your wristwatch or smartwatch."),
        WakeObject("Cup", "☕", "Find a coffee mug, tea cup, or glass."),
        WakeObject("Notebook", "📓", "Find a spiral notebook or study journal."),
        WakeObject("Pillow", "🛏️", "Step back and scan your bed pillow."),
        WakeObject("Chair", "🪑", "Step away from bed and scan your study chair."),
        WakeObject("Spoon", "🥄", "Walk to the kitchen, find a spoon, and scan it."),
        WakeObject("Glasses", "👓", "Find eyeglasses or sunglasses.")
    )

    private const val PREFS_NAME = "rivel_wake_object_prefs"
    private const val KEY_RECENT_OBJECTS = "recent_objects_history"
    private const val MAX_RECENT_MEMORY = 5

    /**
     * Selects a fair random object from the pool while ensuring recent objects do not repeat.
     */
    fun pickNextWakeObject(context: Context): WakeObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_RECENT_OBJECTS, "") ?: ""
        val recentNames = historyStr.split(",").filter { it.isNotBlank() }

        // Filter available pool excluding recent history
        val candidates = ALL_OBJECTS.filter { it.name !in recentNames }
        val chosen = if (candidates.isNotEmpty()) {
            candidates.random()
        } else {
            ALL_OBJECTS.random()
        }

        // Update history
        val updatedHistory = (recentNames + chosen.name).takeLast(MAX_RECENT_MEMORY)
        prefs.edit().putString(KEY_RECENT_OBJECTS, updatedHistory.joinToString(",")).apply()

        return chosen
    }

    fun findObjectByName(name: String): WakeObject {
        return ALL_OBJECTS.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: WakeObject(name, "🎯")
    }
}
