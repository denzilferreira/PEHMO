package fi.oulu.ubicomp.extrema.database

import androidx.room.*
import java.util.*

@Entity(tableName = "diary")
data class Survey(
        @PrimaryKey(autoGenerate = true) var uid: Int?,
        @ColumnInfo(name = "participantId") var participantId: String?,
        @ColumnInfo(name = "timestamp") var timestamp: Long,
        @ColumnInfo(name = "surveyData") var surveyData: String?
)

@Dao
interface SurveyDao {
    @Transaction @Insert
    fun insert(survey: Survey)

    @Query("SELECT * FROM diary WHERE timestamp > :lastSync")
    fun getPendingSync(lastSync : Long) : Array<Survey>

    @Query("SELECT * FROM diary WHERE timestamp > :today")
    fun getToday(today: Long) : Array<Survey>
}