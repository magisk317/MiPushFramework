package top.trumeet.mipushframework.wizard;

import android.app.AppOpsManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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

public class CheckRunInBackgroundActivity extends PushControllerWizardActivity implements NavigationBar.NavigationBarListener {
    private boolean allow;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !PermissionUtils.canAppOpsPermission()) {
            nextPage();
            finish();
            return;
        }
        connect();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!PermissionUtils.canAppOpsPermission()) {
            nextPage();
            finish();
            return;
        }

        mText.setText(Html.fromHtml(getString(R.string.wizard_descr_run_in_background, Build.VERSION.SDK_INT >= 26 ?
                "" : (Utils.isAppOpsInstalled() ? getString(R.string.run_in_background_rikka_appops) :
                getString(R.string.run_in_background_appops_root))))); // TODO: I18n more, no append.

        int result = Utils.checkOp(this, AppOpsManagerOverride.OP_RUN_IN_BACKGROUND);
        allow = (result == AppOpsManager.MODE_ALLOWED);

        if (allow) {
            nextPage();
            finish();
            return;
        }

    }

    @Override
    public void onConnected(Bundle savedInstanceState) {
        super.onConnected(savedInstanceState);
        int result = Utils.checkOp(this, AppOpsManagerOverride.OP_RUN_IN_BACKGROUND);
        allow = (result != AppOpsManager.MODE_IGNORED);

        if (allow) {
            nextPage();
            finish();
            return;
        }
        layout.getNavigationBar()
                .setNavigationBarListener(this);
        mText.setText(Html.fromHtml(getString(R.string.wizard_descr_run_in_background, (Utils.isAppOpsInstalled() ? getString(R.string.run_in_background_rikka_appops) :
                getString(R.string.run_in_background_appops_root)))));
        layout.setHeaderText(R.string.wizard_title_run_in_background);
        setContentView(layout);
    }

    @Override
    public void onNavigateBack() {
        onBackPressed();
    }

    @Override
    public void onNavigateNext() {
        if (!allow && PermissionUtils.canAppOpsPermission()) {
            PermissionUtils.lunchAppOps(this,
                    String.valueOf(AppOpsManagerOverride.OP_RUN_IN_BACKGROUND),
                    Utils.getString(R.string.rikka_appops_help_toast, this));
            return;
        }
        nextPage();
    }

    private void nextPage() {
        startActivity(new Intent(this,
                UsageStatsPermissionActivity.class));
    }
}
