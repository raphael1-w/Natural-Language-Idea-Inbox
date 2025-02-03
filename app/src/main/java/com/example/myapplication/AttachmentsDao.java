package com.example.myapplication;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AttachmentsDao {

    @Query("SELECT * FROM Attachments")
    List<Attachments_table> getAll();

    @Query("SELECT * FROM Attachments WHERE idea_id = :ideaId")
    List<Attachments_table> getAttachmentsForIdea(int ideaId);

    @Insert
    void insertAll(Attachments_table... attachments);

    @Delete
    void delete(Attachments_table attachment);
}
