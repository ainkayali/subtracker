package com.subtracker

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val currency: String,
    val cycle: String,
    val nextBilling: Long,
    val category: String,
    val notes: String = "",
    val reminderOn: Boolean = true,
    val reminderDays: Int = 1
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Subscription::class,
            parentColumns = ["id"],
            childColumns = ["subscriptionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("subscriptionId"),
        Index("paidAt")
    ]
)
data class PaymentLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    val paidAt: Long,
    val amount: Double,
    val currency: String,
    val cycleAtPayment: String
)

@Entity(
    indices = [
        Index(value = ["emailId"], unique = true),
        Index("status"),
        Index("createdAt")
    ]
)
data class PendingDetection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emailId: String,
    val kind: String,
    val targetSubId: Long?,
    val provider: String,
    val amount: Double,
    val currency: String,
    val dateIso: String,
    val cycle: String,
    val confidence: Double,
    val rawSubject: String,
    val createdAt: Long,
    val status: String = "pending"
)

@Dao
interface SubDao {
    @Query("SELECT * FROM Subscription ORDER BY nextBilling ASC")
    fun getAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM Subscription")
    suspend fun all(): List<Subscription>

    @Query("SELECT * FROM Subscription WHERE id = :id")
    suspend fun byId(id: Long): Subscription?

    @Insert
    suspend fun insert(sub: Subscription): Long

    @Update
    suspend fun update(sub: Subscription)

    @Delete
    suspend fun delete(sub: Subscription)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM PaymentLog ORDER BY paidAt DESC LIMIT :limit")
    fun recent(limit: Int = 10): Flow<List<PaymentLog>>

    @Query("SELECT * FROM PaymentLog WHERE subscriptionId = :id ORDER BY paidAt DESC")
    fun forSub(id: Long): Flow<List<PaymentLog>>

    @Query("SELECT * FROM PaymentLog WHERE paidAt >= :startMillis AND paidAt < :endMillis ORDER BY paidAt DESC")
    fun forPeriod(startMillis: Long, endMillis: Long): Flow<List<PaymentLog>>

    @Insert
    suspend fun insert(log: PaymentLog)

    @Delete
    suspend fun delete(log: PaymentLog)
}

@Dao
interface PendingDetectionDao {
    @Query("SELECT * FROM PendingDetection WHERE status = 'pending' ORDER BY createdAt DESC")
    fun pending(): Flow<List<PendingDetection>>

    @Query("SELECT COUNT(*) FROM PendingDetection WHERE status = 'pending'")
    fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM PendingDetection WHERE emailId = :emailId LIMIT 1")
    suspend fun byEmailId(emailId: String): PendingDetection?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(detection: PendingDetection): Long

    @Query("UPDATE PendingDetection SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("DELETE FROM PendingDetection")
    suspend fun clearAll()

    @Delete
    suspend fun delete(detection: PendingDetection)
}

@Database(
    entities = [Subscription::class, PaymentLog::class, PendingDetection::class],
    version = 3,
    exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): SubDao
    abstract fun paymentDao(): PaymentDao
    abstract fun pendingDao(): PendingDetectionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `PaymentLog` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subscriptionId` INTEGER NOT NULL,
                        `paidAt` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `cycleAtPayment` TEXT NOT NULL,
                        FOREIGN KEY(`subscriptionId`) REFERENCES `Subscription`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PaymentLog_subscriptionId` ON `PaymentLog` (`subscriptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PaymentLog_paidAt` ON `PaymentLog` (`paidAt`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `PendingDetection` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `emailId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `targetSubId` INTEGER,
                        `provider` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `currency` TEXT NOT NULL,
                        `dateIso` TEXT NOT NULL,
                        `cycle` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `rawSubject` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL DEFAULT 'pending'
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PendingDetection_emailId` ON `PendingDetection` (`emailId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PendingDetection_status` ON `PendingDetection` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PendingDetection_createdAt` ON `PendingDetection` (`createdAt`)")
            }
        }
    }
}
