package com.example.myapplication.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Calendar;

public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    // Get current time to display appropriate greeting
    // TODO: implement translation
    public String getGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if ((hour >= 0) && (hour < 12)) {
            return "Good Morning";
        } else if (hour >= 12 && hour < 16) {
            return "Good Afternoon";
        } else {
            return "Good Evening";
        }
    }

    public DashboardViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue(getGreeting());
    }

    public LiveData<String> getText() {
        return mText;
    }
}