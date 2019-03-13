package fi.oulu.ubicomp.extrema.database

import androidx.room.*

@Entity(tableName = "bluetooth")
data class Bluetooth(
        @PrimaryKey(autoGenerate = true) var uid: Int?,
        @ColumnInfo(name = "participantId") var participantId : String,
        @ColumnInfo(name = "entryDate") var entryDate : Long,
        @ColumnInfo(name = "macAddress") var macAddress : String,
        @ColumnInfo(name = "btName") var btName : String,
        @ColumnInfo(name = "btRSSI") var btRSSI : Int
)

@Dao
interface BluetoothDao {
    @Insert
    fun insert(bluetooth: Bluetooth)

    @Query("SELECT * FROM bluetooth WHERE entryDate > :lastSync")
    fun getPendingSync(lastSync : Long) : Array<Bluetooth>
}