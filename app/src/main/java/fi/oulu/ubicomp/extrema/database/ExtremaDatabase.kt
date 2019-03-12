package fi.oulu.ubicomp.extrema.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(entities = arrayOf(
        Participant::class,
        Survey::class
), version = 1)
abstract class ExtremaDatabase : RoomDatabase() {
    abstract fun participantDao(): ParticipantDao
    abstract fun surveyDao(): SurveyDao
}