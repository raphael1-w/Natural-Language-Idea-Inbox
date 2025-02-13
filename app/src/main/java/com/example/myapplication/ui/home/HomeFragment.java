package com.example.myapplication.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.example.myapplication.database.AppDatabase;
import com.example.myapplication.database.IdeasDao;
import com.example.myapplication.database.Ideas_table;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.example.myapplication.ui.detail.DetailFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private IdeasDao ideasDao;
    private AppDatabase db;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize database instance
        db = Room.databaseBuilder(requireContext(), AppDatabase.class, "app-database").build();
        ideasDao = db.ideasDao();

        // Calculate the bottom margin for the capture bar, adjusting for the BottomNavigationView
        calculateBottomMargin();

        // Reference RecyclerView from the binding
        RecyclerView recyclerView = binding.recyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Load ideas from the database
        recyclerView.setAdapter(new IdeasAdapter(new ArrayList<>(), this::openDetailFragment)); // Set an empty adapter first
        loadIdeas(recyclerView);

        return root;
    }

    private void loadIdeas(RecyclerView recyclerView) {
        // Get all ideas from the database
        new Thread (() -> {
            List<Ideas_table> ideas = ideasDao.getAllNewestFirst();

            // Update the UI on the main thread
            requireActivity().runOnUiThread(() -> {

                // If list is not empty, set textview to @string/no_ideas
                if (ideas.isEmpty()) {
                    binding.emptyView.setText(R.string.no_ideas);
                }

                // Create an adapter with a click listener
                IdeasAdapter adapter = new IdeasAdapter(ideas, this::openDetailFragment);

                // Set the adapter for the RecyclerView
                recyclerView.setAdapter(adapter);
            });
        }).start();
    }

    private void openDetailFragment(Ideas_table idea) {
        // Open the detail fragment with the selected idea using nav controller
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_activity_main);
        navController.navigate(R.id.navigation_detail, DetailFragment.newInstance(idea).getArguments());
    }

    private void calculateBottomMargin() { // Calculate the bottom margin for the capture bar

        // Find your BottomNavigationView in the Activity
        BottomNavigationView bottomNavigationView = requireActivity().findViewById(R.id.nav_view);

        // Find your element in the Fragment's layout
        View yourElement = binding.getRoot().findViewById(R.id.recyclerView);

        // Add a listener to calculate the height of the BottomNavigationView
        bottomNavigationView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Get the height of the BottomNavigationView
                int navBarHeight = bottomNavigationView.getHeight();

                // Set the bottom margin for your element
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) yourElement.getLayoutParams();
                params.bottomMargin = navBarHeight; // 0dp above the nav bar
                yourElement.setLayoutParams(params);

                // Remove the listener to avoid multiple calls
                bottomNavigationView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        db.close();
        binding = null;
    }
}
