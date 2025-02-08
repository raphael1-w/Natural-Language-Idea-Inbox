package com.example.myapplication.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.Date;
import java.util.List;

@Dao
public interface IdeasDao {
    @Query("SELECT * FROM Ideas")
    List<Ideas_table> getAll();

    @Query("SELECT * FROM Ideas ORDER BY created_at DESC")
    List<Ideas_table> getAllNewestFirst();

    @Query("SELECT * FROM Ideas WHERE created_at BETWEEN :start AND :end")
    List<Ideas_table> getIdeasBetweenDates(Date start, Date end);

    @Query("SELECT * FROM Ideas WHERE type = :type")
    List<Ideas_table> getIdeasOfType(String type);

    @Query("SELECT * FROM Ideas WHERE tags LIKE :tags")
    List<Ideas_table> getIdeasWithTags(String tags);

    @Query("SELECT * FROM Ideas WHERE has_attachments = :hasAttachments")
    List<Ideas_table> getIdeasWithAttachments(boolean hasAttachments);

    @Query("SELECT * FROM Ideas WHERE id = :id")
    Ideas_table getIdeaById(int id);

    @Insert
    void insertAll(Ideas_table... ideas);

    @Delete
    void delete(Ideas_table idea);
}
