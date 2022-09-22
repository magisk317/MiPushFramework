package com.xiaomi.xmsf.push.utils;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

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

import top.trumeet.common.Constants;

public class Configurations {

    class PackageConfig {
        public static final String KEY_CHANNEL_ID = "channel_id";
        public static final String KEY_CHANNEL_NAME = "channel_name";
        public static final String KEY_NOTIFY_ID = "notifyId";
        public static final String KEY_TITLE = "title";
        public static final String KEY_DESCRIPTION = "description";
        public static final String KEY_OPERATION = "operation";

        public static final String OPERATION_OPEN = "open";
        public static final String OPERATION_IGNORE = "ignore";

        String regexChannelId;
        String regexChannelName;
        String regexNotifyId;
        String regexTitle;
        String regexDescription;
        String operation;
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
}
