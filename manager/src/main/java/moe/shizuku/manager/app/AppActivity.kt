package moe.shizuku.manager.app

import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import moe.shizuku.manager.R
import rikka.core.res.isNight
import rikka.core.res.resolveColor
import rikka.material.app.MaterialActivity

abstract class AppActivity : MaterialActivity() {

    override fun computeUserThemeKey(): String {
        // The resolved night state belongs in the key. The key is what says whether the theme
        // applied already can be reused, and switching between Light and Follow System can leave
        // it identical while the attributes it resolves against have changed - so the previous
        // pass's overlays (the opaque bar colors of the light theme, for one) stayed until the
        // process was restarted. With the night state in it, the switch is a different key and
        // the overlays are applied again.
        val night = if (resources.configuration.isNight()) "dark" else "light"
        return ThemeHelper.getTheme(this) + ThemeHelper.isUsingSystemColor() + night
    }

    override fun onApplyUserThemeResource(theme: Theme, isDecorView: Boolean) {
        if (ThemeHelper.isUsingSystemColor()) {
            if (resources.configuration.isNight())
                theme.applyStyle(R.style.ThemeOverlay_DynamicColors_Dark, true)
            else
                theme.applyStyle(R.style.ThemeOverlay_DynamicColors_Light, true)
        }

        theme.applyStyle(ThemeHelper.getThemeStyleRes(this), true)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) {
            finish()
        }
        return true
    }
} 
