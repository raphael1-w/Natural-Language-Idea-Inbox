package com.example.myapplication;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import  androidx.room.PrimaryKey;

@Entity (tableName = "Attachments", foreignKeys = @ForeignKey(entity = Ideas_table.class, parentColumns = "id", childColumns = "idea_id"), indices = {@Index("idea_id")})
public class Attachments_table {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public int idea_id;

    public String file_path;
}
