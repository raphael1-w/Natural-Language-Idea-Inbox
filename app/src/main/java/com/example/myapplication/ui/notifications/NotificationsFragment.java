package com.example.myapplication.ui.notifications;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.databinding.FragmentNotificationsBinding;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.genai.llminference.*;
import com.google.mediapipe.tasks.core.BaseOptions;

public class NotificationsFragment extends Fragment {
    private final String TAG = "NotificationsFragment";
    private LlmInference llmInference;
    private LlmInferenceSession llmInferenceSession;

    private FragmentNotificationsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        NotificationsViewModel notificationsViewModel =
                new ViewModelProvider(this).get(NotificationsViewModel.class);

        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textNotifications;
        notificationsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        binding.testllm.setOnClickListener(v -> {

            String modelPath = "/data/local/tmp/llm_model/gemma2-2b-it-cpu-int8.task";
            String modelPathGPU = "/data/local/tmp/llm_model/gemma2-2b-it-gpu-int8.bin";
            String modelName = "gemma-2-2b-it-cpu-int8.task";

            String summarizationPrompt = "Generate a short sentence. ";

            StringBuilder results = new StringBuilder();

            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelPath)
                    .setDelegate(Delegate.GPU)
                    .build();

            LlmInference.LlmInferenceOptions options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(1024)
                    .setResultListener( (partialResult, done) -> {
                            results.append(partialResult);
                            if (done) {
                                Log.d(TAG, "Complete result: " + results);
                            } else {
                                Log.d(TAG, "Partial result: " + results);
                            }
                        })
                    .build();

            LlmInferenceSession.LlmInferenceSessionOptions sessionOption = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTemperature(0.8f)
                    .setTopK(40)
                    .setTopP(0.9f)
                    .setRandomSeed(101)
                    .build();

            try {
                Log.d(TAG, "Creating LLM inference...");
                llmInference = LlmInference.createFromOptions(requireContext(), options);

                llmInferenceSession = LlmInferenceSession.createFromOptions(llmInference, sessionOption);

                Log.d(TAG, "Generating response...");
                llmInferenceSession.addQueryChunk(summarizationPrompt);
                llmInferenceSession.generateResponseAsync();

            } catch (Exception e) {
                Log.e(TAG, "Error creating LLM inference", e);
            }
        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        llmInference.close();
        llmInferenceSession.close();
        binding = null;
    }
}