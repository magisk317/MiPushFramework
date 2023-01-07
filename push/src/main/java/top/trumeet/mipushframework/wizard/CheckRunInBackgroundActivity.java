package top.trumeet.mipushframework.wizard;

import android.app.AppOpsManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.setupwizardlib.view.NavigationBar;
import com.xiaomi.xmsf.R;

import top.trumeet.common.Constants;
import top.trumeet.common.override.AppOpsManagerOverride;
import top.trumeet.common.utils.Utils;
import top.trumeet.mipushframework.utils.ShellUtils;

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !canAppOpsPermission()) {
            nextPage();
            finish();
            return;
        }
        connect();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!canAppOpsPermission()) {
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
        if (!allow && canAppOpsPermission()) {
            lunchAppOps(
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

    private boolean canAppOpsPermission() {
        return Utils.isAppOpsInstalled() ||
                ShellUtils.isSuAvailable();
    }

    private boolean lunchAppOps(String permission, CharSequence tips) {
        // root first
        if (ShellUtils.isSuAvailable()) {
            if (allowPermission(permission)) {
                return true;
            } else {
                Toast.makeText(this, R.string.fail, Toast.LENGTH_SHORT).show();
            }
        }

        if (Utils.isAppOpsInstalled()) {
            Intent intent = new Intent("rikka.appops.intent.action.PACKAGE_DETAIL")
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .setClassName("rikka.appops", "rikka.appops.DetailActivity")
                    .putExtra("rikka.appops.intent.extra.USER_HANDLE", Utils.myUid())
                    .putExtra("rikka.appops.intent.extra.PACKAGE_NAME", Constants.SERVICE_APP_NAME)
                    .setData(Uri.parse("package:" + Constants.SERVICE_APP_NAME))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, tips, Toast.LENGTH_LONG).show();
            return true;
        }

        return false;
    }

    private boolean allowPermission(String permission) {
        return ShellUtils.exec("appops set --user " + Utils.myUid() +
                " " + Constants.SERVICE_APP_NAME + " " + permission +
                " " + AppOpsManager.MODE_ALLOWED);
    }
}
