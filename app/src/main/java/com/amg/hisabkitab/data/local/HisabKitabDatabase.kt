package com.amg.hisabkitab.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class AppConverters {
    @TypeConverter fun billStatus(value: String): BillStatus = BillStatus.valueOf(value)
    @TypeConverter fun billStatus(value: BillStatus): String = value.name
    @TypeConverter fun paymentMode(value: String?): PaymentMode? = value?.let(PaymentMode::valueOf)
    @TypeConverter fun paymentMode(value: PaymentMode?): String? = value?.name
    @TypeConverter fun lossReason(value: String): LossReason = LossReason.valueOf(value)
    @TypeConverter fun lossReason(value: LossReason): String = value.name
}

@Database(
    entities = [
        ProductEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        LossEntryEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(AppConverters::class)
abstract class HisabKitabDatabase : RoomDatabase() {
    abstract fun dao(): HisabKitabDao

    companion object {
        fun create(context: Context): HisabKitabDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HisabKitabDatabase::class.java,
                "hisabkitab.db"
            ).build()
    }
}
