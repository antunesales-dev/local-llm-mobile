package com.localllm.chat.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.localllm.chat.data.db.entity.ConversationEntity
import com.localllm.chat.data.db.entity.MessageEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
