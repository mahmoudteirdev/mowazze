package com.mowazze3

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
    @Test
    fun `device language follows Arabic devices and keeps Latin digits`() {
        val locale = AppLocale.resolve(AppLanguage.SYSTEM, "ar")
        assertEquals("ar", locale.language)
        assertEquals("latn", locale.getUnicodeLocaleType("nu"))
    }

    @Test
    fun `any other device language gets English, the only other translation`() {
        assertEquals(Locale.ENGLISH, AppLocale.resolve(AppLanguage.SYSTEM, "en"))
        assertEquals(Locale.ENGLISH, AppLocale.resolve(AppLanguage.SYSTEM, "fr"))
    }

    @Test
    fun `a picked language wins over the device`() {
        assertEquals(Locale.ENGLISH, AppLocale.resolve(AppLanguage.ENGLISH, "ar"))
        assertEquals("ar", AppLocale.resolve(AppLanguage.ARABIC, "en").language)
    }
}
