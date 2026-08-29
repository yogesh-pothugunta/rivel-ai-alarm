package com.example.data.repository

import com.example.data.local.dao.AlarmDao
import com.example.data.local.entity.AlarmEntity
import kotlinx.coroutines.flow.Flow

class AlarmRepository(private val dao: AlarmDao) {
    val allAlarms: Flow<List<AlarmEntity>> = dao.getAllAlarms()
    val enabledAlarms: Flow<List<AlarmEntity>> = dao.getEnabledAlarms()
    val nextAlarm: Flow<AlarmEntity?> = dao.getNextAlarm()

    suspend fun getAlarmById(id: Long): AlarmEntity? = dao.getAlarmById(id)

    suspend fun getEnabledAlarmsOnce(): List<AlarmEntity> = dao.getEnabledAlarmsOnce()

    suspend fun insertAlarm(alarm: AlarmEntity): Long = dao.insertAlarm(alarm)

    suspend fun updateAlarm(alarm: AlarmEntity) = dao.updateAlarm(alarm)

    suspend fun setAlarmEnabled(id: Long, isEnabled: Boolean) = dao.setAlarmEnabled(id, isEnabled)

    suspend fun deleteAlarm(alarm: AlarmEntity) = dao.deleteAlarm(alarm)

    suspend fun deleteAlarmById(id: Long) = dao.deleteAlarmById(id)
}
