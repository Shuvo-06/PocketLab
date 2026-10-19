package com.pocketlab.app;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pocketlab.app.databinding.ActivityMainBinding;
import com.pocketlab.app.ui.recorder.RecorderViewModel;
import com.pocketlab.app.util.BuildConfigVersion;

import java.util.HashSet;
import java.util.Set;

/**
 * Single-activity host. Screens are Fragments navigated through the
 * Navigation component and a bottom navigation bar.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        Fragment host = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = ((NavHostFragment) host).getNavController();

        Set<Integer> topLevel = new HashSet<>();
        topLevel.add(R.id.dashboardFragment);
        topLevel.add(R.id.recorderFragment);
        topLevel.add(R.id.experimentsFragment);
        topLevel.add(R.id.historyFragment);
        appBarConfiguration = new AppBarConfiguration.Builder(topLevel).build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.bottomNav, navController);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Until the Phase 8 foreground service exists, a recording must not keep running
        // unseen in the background. Rotation is not "leaving the app", so skip it.
        if (!isChangingConfigurations()) {
            new ViewModelProvider(this).get(RecorderViewModel.class).onAppBackgrounded();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.app_name)
                .setMessage(getString(R.string.about_message, BuildConfigVersion.name(this)))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
