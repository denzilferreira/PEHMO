package fi.oulu.ubicomp.extrema.database

import androidx.room.*

@Entity(tableName = "battery")
data class Battery(
        @PrimaryKey(autoGenerate = true) var uid: Int?,
        @ColumnInfo(name = "participantId") var participantId : String,
        @ColumnInfo(name = "entryDate") var entryDate : Long,
        @ColumnInfo(name = "batteryPercent") var batteryPercent : Double,
        @ColumnInfo(name = "batteryTemperature") var batteryTemperature : Double,
        @ColumnInfo(name = "batteryStatus") var batteryStatus : String
)

@Dao
interface BatteryDao {
    @Transaction @Insert
    fun insert(battery: Battery)

    @Query("SELECT * FROM battery WHERE entryDate > :lastSync")
    fun getPendingSync(lastSync : Long) : Array<Battery>
}