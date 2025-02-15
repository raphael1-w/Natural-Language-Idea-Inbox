package com.example.myapplication.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private MutableLiveData<String> deletionResult = new MutableLiveData<>();

    public MutableLiveData<String> getDeletionResult() {
        return deletionResult;
    }

    public void setDeletionResult(String result) {
        deletionResult.setValue(result);
    }
}
