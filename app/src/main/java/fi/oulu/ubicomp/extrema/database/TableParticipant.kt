package fi.oulu.ubicomp.extrema.database

import androidx.room.*

@Entity(tableName = "participant")
data class Participant(
        @PrimaryKey(autoGenerate = true) var uid: Int?,
        @ColumnInfo(name = "participantId") var participantId: String,
        @ColumnInfo(name = "participantName") var participantName: String,
        @ColumnInfo(name = "participantEmail") var participantEmail: String,
        @ColumnInfo(name = "participantBirth") var participantBirth: String,
        @ColumnInfo(name = "onboardDate") var onboardDate : Long
)

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participant LIMIT 1")
    fun getParticipant(): Participant

    @Insert
    fun insert(participant: Participant)
}