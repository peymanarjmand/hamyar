package com.hesabyar.garage.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hesabyar.garage.util.PersianDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "hesabyar_settings")

data class AppSettings(
    val shopName: String = "تعمیرگاه صافکاری و نقاشی",
    val onboardingDone: Boolean = false,
    val selectedYear: Int = PersianDate.today().year,
    val lastBackupAt: Long = 0,
    val lastBackupChecksum: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val shopName = stringPreferencesKey("shop_name")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val selectedYear = intPreferencesKey("selected_year")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val lastBackupChecksum = stringPreferencesKey("last_backup_checksum")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            shopName = preferences[Keys.shopName] ?: "تعمیرگاه صافکاری و نقاشی",
            onboardingDone = preferences[Keys.onboardingDone] ?: false,
            selectedYear = preferences[Keys.selectedYear] ?: PersianDate.today().year,
            lastBackupAt = preferences[Keys.lastBackupAt] ?: 0,
            lastBackupChecksum = preferences[Keys.lastBackupChecksum] ?: "",
        )
    }

    suspend fun finishOnboarding(shopName: String, year: Int) {
        context.settingsDataStore.edit {
            it[Keys.shopName] = shopName.trim()
            it[Keys.selectedYear] = year
            it[Keys.onboardingDone] = true
        }
    }

    suspend fun selectYear(year: Int) {
        context.settingsDataStore.edit { it[Keys.selectedYear] = year }
    }

    suspend fun setShopName(value: String) {
        context.settingsDataStore.edit { it[Keys.shopName] = value.trim() }
    }

    suspend fun recordBackup(checksum: String, at: Long = System.currentTimeMillis()) {
        context.settingsDataStore.edit {
            it[Keys.lastBackupAt] = at
            it[Keys.lastBackupChecksum] = checksum
        }
    }

    suspend fun restore(settings: AppSettings) {
        context.settingsDataStore.edit {
            it[Keys.shopName] = settings.shopName
            it[Keys.onboardingDone] = settings.onboardingDone
            it[Keys.selectedYear] = settings.selectedYear
            it[Keys.lastBackupAt] = settings.lastBackupAt
            it[Keys.lastBackupChecksum] = settings.lastBackupChecksum
        }
    }
}
