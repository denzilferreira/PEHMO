package fi.oulu.ubicomp.extrema.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(entities = [
    Participant::class,
    Survey::class,
    Location::class,
    Bluetooth::class]
, version = 2)

abstract class ExtremaDatabase : RoomDatabase() {
    abstract fun participantDao(): ParticipantDao
    abstract fun surveyDao(): SurveyDao
    abstract fun locationDao() : LocationDao
    abstract fun bluetoothDao() : BluetoothDao
}