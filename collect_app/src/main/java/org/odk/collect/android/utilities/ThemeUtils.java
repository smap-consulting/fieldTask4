/*
 * Copyright (C) 2018 Shobhit Agarwal
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.utilities;

import android.content.Context;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.StyleRes;

import org.odk.collect.android.R;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.PreferencesProvider;

public final class ThemeUtils {

    private final Context context;
    private final PreferencesProvider preferencesProvider;

    public ThemeUtils(Context context) {
        this.context = context;
        this.preferencesProvider = new PreferencesProvider(context);
    }

    @StyleRes
    public int getAppTheme() {
        if (isMagentaEnabled()) {
            return R.style.Theme_Collect_Magenta;
        } else {
            String theme = getPrefsTheme();
            if (theme.equals(context.getString(org.odk.collect.strings.R.string.app_theme_dark))) {
                return R.style.Theme_Collect_Dark;
            } else {
                return R.style.Theme_Collect_Light;
            }
        }
    }

    @StyleRes
    public int getFormEntryActivityTheme() {
        if (isMagentaEnabled()) {
            return R.style.Theme_Collect_Activity_FormEntryActivity_Magenta;
        } else {
            String theme = getPrefsTheme();
            if (theme.equals(context.getString(org.odk.collect.strings.R.string.app_theme_dark))) {
                return R.style.Theme_Collect_Activity_FormEntryActivity_Dark;
            } else {
                return R.style.Theme_Collect_Activity_FormEntryActivity_Light;
            }
        }
    }

    @StyleRes
    public int getSettingsTheme() {
        if (isMagentaEnabled()) {
            return R.style.Theme_Collect_Settings_Magenta;
        } else {
            String theme = getPrefsTheme();
            if (theme.equals(context.getString(org.odk.collect.strings.R.string.app_theme_dark))) {
                return R.style.Theme_Collect_Settings_Dark;
            } else {
                return R.style.Theme_Collect_Settings_Light;
            }
        }
    }

    @StyleRes
    public int getBottomDialogTheme() {
        return isDarkTheme() ? R.style.Theme_Collect_MaterialDialogSheet_Dark : R.style.Theme_Collect_MaterialDialogSheet_Light;
    }

    @DrawableRes
    public int getDivider() {
        return isDarkTheme() ? android.R.drawable.divider_horizontal_dark : android.R.drawable.divider_horizontal_bright;
    }

    public boolean isHoloDialogTheme(int theme) {
        return theme == android.R.style.Theme_Holo_Light_Dialog ||
                theme == android.R.style.Theme_Holo_Dialog;
    }

    @StyleRes
    public int getMaterialDialogTheme() {
        return isDarkTheme()
                ? R.style.Theme_Collect_Dark_Dialog
                : R.style.Theme_Collect_Light_Dialog;
    }

    @StyleRes
    public int getHoloDialogTheme() {
        return isDarkTheme() ?
                android.R.style.Theme_Holo_Dialog :
                android.R.style.Theme_Holo_Light_Dialog;
    }

    private int getAttributeValue(@AttrRes int resId) {
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(resId, outValue, true);
        return outValue.data;
    }

    public int getAccountPickerTheme() {
        return isDarkTheme() ? 0 : 1;
    }

    public boolean isDarkTheme() {
        String theme = getPrefsTheme();
        return theme.equals(context.getString(org.odk.collect.strings.R.string.app_theme_dark));
    }

    private boolean isMagentaEnabled() {
        return preferencesProvider.getGeneralSharedPreferences().getBoolean(GeneralKeys.KEY_MAGENTA_THEME, false);
    }

    private String getPrefsTheme() {
        return preferencesProvider.getGeneralSharedPreferences().getString(GeneralKeys.KEY_APP_THEME, "light_theme");
    }

    /**
     * @return Text color for the current {@link android.content.res.Resources.Theme}
     */
    @ColorInt
    public int getColorOnSurface() {
        return getAttributeValue(com.google.android.material.R.attr.colorOnSurface);
    }

    @ColorInt
    public int getAccentColor() {
        return getAttributeValue(com.google.android.material.R.attr.colorAccent);
    }

    @ColorInt
    public int getIconColor() {
        return getAttributeValue(com.google.android.material.R.attr.colorOnSurface);
    }

    @ColorInt
    public int getColorPrimary() {
        return getAttributeValue(com.google.android.material.R.attr.colorPrimary);
    }

    @ColorInt
    public int getColorOnPrimary() {
        return getAttributeValue(com.google.android.material.R.attr.colorOnPrimary);
    }

    @ColorInt
    public int getColorSecondary() {
        return getAttributeValue(com.google.android.material.R.attr.colorSecondary);
    }
}
