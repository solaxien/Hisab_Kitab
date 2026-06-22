package com.amg.hisabkitab.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true
)
@TypeConverters(AppConverters::class)
abstract class HisabKitabDatabase : RoomDatabase() {
    abstract fun dao(): HisabKitabDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS index_products_sku")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_products_sku ON products(sku)")
            }
        }

        fun create(context: Context): HisabKitabDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HisabKitabDatabase::class.java,
                "hisabkitab.db"
            ).addMigrations(MIGRATION_1_2).build()
    }
}
