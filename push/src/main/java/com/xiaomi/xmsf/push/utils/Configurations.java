package com.xiaomi.xmsf.push.utils;

import static top.trumeet.common.utils.NotificationUtils.*;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.xmpush.thrift.PushMetaInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import top.trumeet.common.Constants;

public class Configurations {
    private static Logger logger = XLog.tag(Configurations.class.getSimpleName()).build();

    class PackageConfig {
        public static final String KEY_CHANNEL_ID = "channel_id";
        public static final String KEY_CHANNEL_NAME = "channel_name";
        public static final String KEY_NOTIFY_ID = "notifyId";
        public static final String KEY_TITLE = "title";
        public static final String KEY_DESCRIPTION = "description";
        public static final String KEY_OPERATION = "operation";

        public static final String OPERATION_OPEN = "open";
        public static final String OPERATION_IGNORE = "ignore";
        public static final String OPERATION_NOTIFY = "notify";

        String regexChannelId;
        String regexChannelName;
        String regexNotifyId;
        String regexTitle;
        String regexDescription;
        String operation;

        public boolean match(PushMetaInfo metaInfo) {
            return metaInfo != null
                    && match(String.valueOf(metaInfo.getNotifyId()), this.regexChannelId)
                    && match(getExtraField(metaInfo.getExtra(), EXTRA_CHANNEL_NAME, ""), this.regexChannelName)
                    && match(getExtraField(metaInfo.getExtra(), EXTRA_CHANNEL_ID, ""), this.regexChannelId)
                    && match(metaInfo.getTitle(), this.regexTitle)
                    && match(metaInfo.getDescription(), this.regexDescription)
                    ;
        }

        private boolean match(String text, String regex) {
            if (regex == null) {
                return true;
            }
            if (text == null) {
                return false;
            }
            Pattern pattern = Pattern.compile(regex);
            return pattern.matcher(text).find();
        }
    }

    private String version;
    private Map<String, List<PackageConfig>> packageConfigs = new HashMap<>();

    private static Configurations instance = null;

    public static Configurations getInstance() {
        if (instance == null) {
            synchronized (Configurations.class) {
                if (instance == null) {
                    instance = new Configurations();
                }
            }
        }
        return instance;
    }


    private Configurations() {
    }

    public boolean init(Context context, Uri treeUri) {
        packageConfigs = new HashMap<>();
        do {
            if (context == null || treeUri == null) {
                break;
            }
            DocumentFile documentFile = DocumentFile.fromTreeUri(context, treeUri);
            if (documentFile == null) {
                break;
            }
            DocumentFile configs = documentFile.findFile(Constants.CONFIGURATIONS_FILE_NAME);
            if (configs == null) {
                break;
            }
            String json = readTextFromUri(context, configs.getUri());
            try {
                parse(json);
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                break;
            }
            return true;
        } while (false);
        return false;
    }

    private void parse(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        version = jsonObject.getString("version");
        JSONObject packageConfigsObj = jsonObject.getJSONObject("configs");
        Iterator<String> packageNames = packageConfigsObj.keys();
        while (packageNames.hasNext()) {
            String packageName = packageNames.next();
            JSONArray configsObj = packageConfigsObj.getJSONArray(packageName);
            packageConfigs.put(packageName, parseConfigs(configsObj));
        }
    }

    @NonNull
    private ArrayList<PackageConfig> parseConfigs(JSONArray configsObj) throws JSONException {
        ArrayList<PackageConfig> configs = new ArrayList<>();
        for (int i = 0; i < configsObj.length(); ++i) {
            JSONObject configObj = configsObj.getJSONObject(i);
            PackageConfig config = parseConfig(configObj);
            configs.add(config);
        }
        return configs;
    }

    @NonNull
    private PackageConfig parseConfig(JSONObject configObj) throws JSONException {
        PackageConfig config = new PackageConfig();
        if (!configObj.isNull(PackageConfig.KEY_CHANNEL_ID)) {
            config.regexChannelId = configObj.getString(PackageConfig.KEY_CHANNEL_ID);
        }
        if (!configObj.isNull(PackageConfig.KEY_CHANNEL_NAME)) {
            config.regexChannelName = configObj.getString(PackageConfig.KEY_CHANNEL_NAME);
        }
        if (!configObj.isNull(PackageConfig.KEY_NOTIFY_ID)) {
            config.regexNotifyId = configObj.getString(PackageConfig.KEY_NOTIFY_ID);
        }
        if (!configObj.isNull(PackageConfig.KEY_TITLE)) {
            config.regexTitle = configObj.getString(PackageConfig.KEY_TITLE);
        }
        if (!configObj.isNull(PackageConfig.KEY_DESCRIPTION)) {
            config.regexDescription = configObj.getString(PackageConfig.KEY_DESCRIPTION);
        }
        if (!configObj.isNull(PackageConfig.KEY_OPERATION)) {
            config.operation = configObj.getString(PackageConfig.KEY_OPERATION);
        }
        return config;
    }


    private String readTextFromUri(Context context, Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    public boolean needOpen(String packageName, PushMetaInfo metaInfo) {
        List<PackageConfig> configs = packageConfigs.get(packageName);
        logger.i("package: " + packageName + ", config count: " + (configs == null ? 0 : configs.size()));
        if (configs == null) {
            return false;
        }

        for (PackageConfig config : configs) {
            if (config.operation.contains(PackageConfig.OPERATION_OPEN) && config.match(metaInfo)) {
                logger.i("notification need open");
                return true;
            }
        }
        return false;
    }

    public boolean needIgnore(String packageName, PushMetaInfo metaInfo) {
        List<PackageConfig> configs = packageConfigs.get(packageName);
        logger.i("package: " + packageName + ", config count: " + (configs == null ? 0 : configs.size()));
        if (configs == null) {
            return false;
        }

        for (PackageConfig config : configs) {
            boolean isNotifyOp = config.operation.contains(PackageConfig.OPERATION_NOTIFY);
            boolean isIgnoreOp = config.operation.contains(PackageConfig.OPERATION_IGNORE);
            if (!isNotifyOp && !isIgnoreOp) {
                continue;
            }
            if (config.match(metaInfo)) {
                if (isIgnoreOp) {
                    logger.i("notification need ignore");
                    return true;
                } else {
                    logger.i("notification need notify");
                    return false;
                }
            }
        }
        return false;
    }
}
