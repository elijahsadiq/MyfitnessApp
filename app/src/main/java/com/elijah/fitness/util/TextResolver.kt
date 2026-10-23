package com.elijah.fitness.util

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Reads `strings.xml` / `plurals.xml` for code that has no `Context`:
 * ViewModels and pure helpers such as the goal-title builder or the
 * insight engine. `AppContainer.text` is the app-wide instance; unit
 * tests pass one that reads the same XML files from disk, so they check
 * the real copy.
 */
interface TextResolver {
    fun string(@StringRes id: Int, vararg args: Any): String
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

/** [TextResolver] backed by Android [Resources]. */
class ResourcesTextResolver(private val resources: Resources) : TextResolver {

    override fun string(id: Int, vararg args: Any): String =
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String =
        if (args.isEmpty()) {
            resources.getQuantityString(id, count)
        } else {
            resources.getQuantityString(id, count, *args)
        }
}
