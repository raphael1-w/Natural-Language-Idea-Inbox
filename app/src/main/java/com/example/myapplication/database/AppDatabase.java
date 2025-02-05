package com.example.myapplication.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {Ideas_table.class, Attachments_table.class}, version = 1)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract IdeasDao ideasDao();
    public abstract AttachmentsDao attachmentsDao();
}