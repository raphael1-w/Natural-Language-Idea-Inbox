package com.example.myapplication;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity (tableName = "Ideas")
public class Ideas_table {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;

    public String type;

    public String tags;

    public Date created_at;

    public Date updated_at;

    public String recording_file_path;

    public String text_file_path;

    public String summary_file_path;

    public boolean has_attachments;
}




