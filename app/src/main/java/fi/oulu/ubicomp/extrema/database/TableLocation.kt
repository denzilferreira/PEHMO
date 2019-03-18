package fi.oulu.ubicomp.extrema.database

import androidx.room.*

@Entity(tableName = "location")
data class Location(
        @PrimaryKey(autoGenerate = true) var uid: Int?,
        @ColumnInfo(name = "participantId") var participantId: String?,
        @ColumnInfo(name = "entryDate") var entryDate: Long,
        @ColumnInfo(name = "latitude") var latitude: Double?,
        @ColumnInfo(name = "longitude") var longitude: Double?,
        @ColumnInfo(name = "accuracy_meters") var accuracy: Float?,
        @ColumnInfo(name = "speed") var speed : Float?,
        @ColumnInfo(name = "source") var source: String?,
        @ColumnInfo(name = "satellites") var satellites: Int?,
        @ColumnInfo(name = "indoors") var isIndoors : Boolean?
)

@Dao
interface LocationDao {
    @Transaction @Insert
    fun insert(location: Location)

    @Query("SELECT * FROM location WHERE entryDate > :lastSync")
    fun getPendingSync(lastSync : Long) : Array<Location>
}