package com.subtracker

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
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

@Database(entities = [Subscription::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): SubDao
}
