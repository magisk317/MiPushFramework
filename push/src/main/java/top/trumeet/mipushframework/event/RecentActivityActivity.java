package top.trumeet.mipushframework.event;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;

import com.google.android.material.elevation.SurfaceColors;

/**
 * Created by Trumeet on 2017/8/28.
 * 
 * @author Trumeet
 */

public class RecentActivityActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, EventFragment.newInstance(getIntent().getDataString()))
                    .commitAllowingStateLoss();
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        int color = SurfaceColors.SURFACE_2.getColor(this);
        getWindow().setStatusBarColor(color);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
