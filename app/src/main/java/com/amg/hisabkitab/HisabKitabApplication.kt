package com.amg.hisabkitab

import android.app.Application
import com.amg.hisabkitab.data.local.HisabKitabDatabase
import com.amg.hisabkitab.data.repository.ShopRepository

class HisabKitabApplication : Application() {
    val database by lazy { HisabKitabDatabase.create(this) }
    val repository by lazy { ShopRepository(database.dao()) }
}
