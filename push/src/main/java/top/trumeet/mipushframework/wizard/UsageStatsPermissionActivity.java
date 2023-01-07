package top.trumeet.mipushframework.wizard;

import android.app.AppOpsManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;

import androidx.annotation.Nullable;

import com.android.setupwizardlib.view.NavigationBar;
import com.xiaomi.xmsf.R;

import top.trumeet.common.override.AppOpsManagerOverride;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipushframework.utils.PermissionUtils;

/**
 * Created by Trumeet on 2017/8/25.
 *
 * @author Trumeet
 */

public class UsageStatsPermissionActivity extends PushControllerWizardActivity implements NavigationBar.NavigationBarListener {
    private boolean allow;
    private boolean nextClicked = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            nextPage();
            finish();
            return;
        }
        connect();
    }

    @Override
    public void onResume() {
        super.onResume();

        check();
        if (allow) {
            nextPage();
            finish();
        } else if (nextClicked) {
            layout.getNavigationBar()
                    .getNextButton()
                    .setText(R.string.retry);
        }
    }

    private void check() {
        int result = Utils.checkOp(this, AppOpsManagerOverride.OP_GET_USAGE_STATS);
        allow = (result == AppOpsManager.MODE_ALLOWED);
    }

    @Override
    public void onConnected(Bundle savedInstanceState) {
        super.onConnected(savedInstanceState);
        check();

        if (allow) {
            nextPage();
            finish();
            return;
        }
        layout.getNavigationBar()
                .setNavigationBarListener(this);
        mText.setText(Html.fromHtml(getString(R.string.wizard_title_stats_permission_text)));
        layout.setHeaderText(Html.fromHtml(getString(R.string.wizard_title_stats_permission)));
        setContentView(layout);
    }

    @Override
    public void onNavigateBack() {
        onBackPressed();
    }

    @Override
    public void onNavigateNext() {
        if (!allow) {
            if (!nextClicked || !PermissionUtils.canAppOpsPermission()) {
                nextClicked = true;
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } else {
                PermissionUtils.lunchAppOps(this,
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        getString(R.string.wizard_title_stats_permission_text));
                check();
            }
        } else {
            nextPage();
        }
    }

    private void nextPage() {
        startActivity(new Intent(this,
                FinishWizardActivity.class));
    }

}
