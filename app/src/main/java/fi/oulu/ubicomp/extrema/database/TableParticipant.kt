package fi.oulu.ubicomp.extrema.database

import androidx.room.*

@Entity(tableName = "participants")
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
    @Query("SELECT * FROM participants LIMIT 1")
    fun getParticipant(): Participant

    @Insert
    fun insertAll(vararg participants: Participant)
}