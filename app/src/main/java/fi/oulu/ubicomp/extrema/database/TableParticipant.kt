package fi.oulu.ubicomp.extrema.database

import androidx.room.*

@Entity(tableName = "participant")
data class Participant(
        @PrimaryKey(autoGenerate = true) var uid: Int?,
        @ColumnInfo(name = "participantId") var participantId: String,
        @ColumnInfo(name = "participantName") var participantName: String,
        @ColumnInfo(name = "participantEmail") var participantEmail: String?,
        @ColumnInfo(name = "ruuviTag") var ruuviTag: String?,
        @ColumnInfo(name = "timestamp") var timestamp : Long,
        @ColumnInfo(name = "country") var participantCountry : String
)

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participant ORDER BY uid DESC LIMIT 1")
    fun getParticipant(): Array<Participant>

    @Query("SELECT * FROM participant WHERE timestamp > :lastSync")
    fun getPendingSync(lastSync : Long) : Array<Participant>

    @Transaction @Insert
    fun insert(participant: Participant)
}