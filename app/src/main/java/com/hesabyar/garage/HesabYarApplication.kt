package com.hesabyar.garage

import android.app.Application
import com.hesabyar.garage.data.AppSecurityRepository
import com.hesabyar.garage.data.GarageRepository
import com.hesabyar.garage.data.BackupService
import com.hesabyar.garage.data.SettingsRepository
import com.hesabyar.garage.data.TransactionDeletionService
import com.hesabyar.garage.data.local.GarageDatabase
import com.hesabyar.garage.security.CardCrypto

class HesabYarApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database: GarageDatabase = GarageDatabase.create(application)
    val cardCrypto = CardCrypto()
    val settingsRepository = SettingsRepository(application)
    val appSecurityRepository = AppSecurityRepository(application)
    internal val garageRepository = GarageRepository(database, cardCrypto, settingsRepository)
    internal val transactionDeletionService = TransactionDeletionService(appSecurityRepository, garageRepository)
    val backupService = BackupService(application, database, settingsRepository, cardCrypto)
}
