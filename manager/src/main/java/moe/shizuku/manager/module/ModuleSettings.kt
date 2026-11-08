package moe.shizuku.manager.module

import moe.shizuku.manager.ShizukuSettings

/**
 * What modules are allowed to do, as the user set it.
 *
 * Two switches and a per-module mark of trust, all kept where every other setting of this app is
 * kept. They default to the cautious end: safe access, no background actions, no module trusted.
 */
object ModuleSettings {

    private const val KEY_ACCESS_MODE = "module_access_mode"
    private const val KEY_BACKGROUND = "module_allow_background"
    private const val KEY_TRUSTED_PREFIX = "module_trusted_"
    private const val KEY_SOURCE_PREFIX = "module_source_"

    fun getAccessMode(): ModuleAccessMode =
        ModuleAccessMode.fromValue(ShizukuSettings.getPreferences().getString(KEY_ACCESS_MODE, null))

    fun setAccessMode(mode: ModuleAccessMode) {
        ShizukuSettings.getPreferences().edit().putString(KEY_ACCESS_MODE, mode.value).apply()
    }

    fun allowsBackground(): Boolean =
        ShizukuSettings.getPreferences().getBoolean(KEY_BACKGROUND, false)

    fun setAllowsBackground(allowed: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_BACKGROUND, allowed).apply()
    }

    fun isTrusted(moduleId: String): Boolean =
        ShizukuSettings.getPreferences().getBoolean(KEY_TRUSTED_PREFIX + moduleId, false)

    fun setTrusted(moduleId: String, trusted: Boolean) {
        ShizukuSettings.getPreferences().edit()
            .putBoolean(KEY_TRUSTED_PREFIX + moduleId, trusted)
            .apply()
    }

    /**
     * Where a module came from, when this app installed it from the catalogue.
     *
     * A module's directory name is its own id, which says nothing about the repository it was built
     * from - so the catalogue, which shows repositories, had no way to tell that one of them was
     * already installed: it compared the repository's name against the ids and the two could never
     * match, since an id cannot contain a slash. Recording the source at install is what makes the
     * question answerable, and it is the only answer that is a fact rather than a guess.
     */
    fun setSource(moduleId: String, repoFullName: String) {
        ShizukuSettings.getPreferences().edit()
            .putString(KEY_SOURCE_PREFIX + moduleId, repoFullName)
            .apply()
    }

    fun sourceOf(moduleId: String): String? =
        ShizukuSettings.getPreferences().getString(KEY_SOURCE_PREFIX + moduleId, null)

    /** Everything an installed module answers to: its own id, and the repository it came from. */
    fun installedKeys(modules: List<AdbModule>): Set<String> = buildSet {
        modules.forEach { module ->
            add(module.id.lowercase())
            sourceOf(module.id)?.lowercase()?.let(::add)
        }
    }

    /**
     * Whether a repository's module is already installed, as far as [keys] can tell.
     *
     * The full name matches when this app recorded where a module came from. A repository's own
     * name matches a module's id, which is what a module installed by hand has to go on: a module
     * built from a repository is usually named after it. That half is a guess, and it can only be
     * wrong in the direction of a mark that is missing rather than one that is not earned.
     */
    fun installedMatch(keys: Set<String>, repo: ModuleRepo): Boolean =
        repo.fullName.lowercase() in keys || repo.name.lowercase() in keys

    /**
     * The order the module list is shown in, under the same keys their screen keeps it in, so the
     * names mean the same thing to anybody who has seen both.
     */
    const val SORT_A_TO_Z = "module_sort_a_to_z"
    const val SORT_Z_TO_A = "module_sort_z_to_a"
    const val SORT_SIZE_LOW_TO_HIGH = "module_sort_size_low_to_high"
    const val SORT_SIZE_HIGH_TO_LOW = "module_sort_size_high_to_low"
    const val SORT_ENABLED_FIRST = "module_sort_enabled_first"
    const val SORT_ACTION_FIRST = "module_sort_action_first"
    const val SORT_WEBUI_FIRST = "module_sort_webui_first"

    /**
     * The order the find-modules list is shown in.
     *
     * Their screen keeps this order under its own keys, which are the same words as the module
     * list's but a different list: the modules installed here and the repositories to install
     * from are sorted independently, so they get keys of their own.
     */
    const val REPO_SORT_A_TO_Z = "module_repo_sort_a_to_z"
    const val REPO_SORT_Z_TO_A = "module_repo_sort_z_to_a"
    const val REPO_SORT_STARS_LOW_TO_HIGH = "module_repo_sort_stars_low_to_high"
    const val REPO_SORT_STARS_HIGH_TO_LOW = "module_repo_sort_stars_high_to_low"

    fun sortFlag(key: String, default: Boolean = false): Boolean =
        ShizukuSettings.getPreferences().getBoolean(key, default)

    fun setSortFlag(key: String, value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(key, value).apply()
    }
}
