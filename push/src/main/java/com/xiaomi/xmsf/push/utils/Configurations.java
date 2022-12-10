package com.xiaomi.xmsf.push.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.xiaomi.xmpush.thrift.PushMetaInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import top.trumeet.common.Constants;

public class Configurations {
    private static Logger logger = XLog.tag(Configurations.class.getSimpleName()).build();

    public class PackageConfig {
        public static final String KEY_META_INFO = "metaInfo";
        public static final String KEY_NEW_META_INFO = "newMetaInfo";
        public static final String KEY_OPERATION = "operation";
        public static final String KEY_STOP = "stop";

        public static final String OPERATION_OPEN = "open";
        public static final String OPERATION_IGNORE = "ignore";
        public static final String OPERATION_NOTIFY = "notify";
        public static final String OPERATION_WAKE = "wake";

        JSONObject metaInfoObj;
        JSONObject newMetaInfoObj;
        Set<String> operation = new HashSet<>();
        boolean stop = true;

        Map<String, String> matchGroup;

        public boolean match(PushMetaInfo metaInfo) throws NoSuchFieldException, IllegalAccessException, JSONException {
            matchGroup = new HashMap<>();
            if (metaInfoObj == null) {
                return true;
            }
            Iterator<String> keys = metaInfoObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                final Field field = PushMetaInfo.class.getDeclaredField(key);
                Object value = field.get(metaInfo);

                if (value instanceof Map) {
                    Map subMap = (Map) value;
                    JSONObject subObj = metaInfoObj.getJSONObject(key);

                    Iterator<String> subKeys = subObj.keys();
                    while (subKeys.hasNext()) {
                        String subKey = subKeys.next();
                        if (mismatchField(subObj, subKey, subMap.get(subKey))) {
                            return false;
                        }
                    }
                } else {
                    if (mismatchField(metaInfoObj, key, value)) {
                        return false;
                    }
                }
            }
            return true;
        }

        public void replace(PushMetaInfo metaInfo) throws NoSuchFieldException, IllegalAccessException, JSONException, NoSuchMethodException, InvocationTargetException {
            JSONObject metaInfoObj = newMetaInfoObj;
            if (metaInfoObj == null) {
                return;
            }
            Iterator<String> keys = metaInfoObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                final Field field = PushMetaInfo.class.getDeclaredField(key);
                final Class<?> type = field.getType();

                if (Map.class.isAssignableFrom(field.getType())) {
                    Map subMap = (Map) field.get(metaInfo);
                    JSONObject subObj = metaInfoObj.getJSONObject(key);

                    Iterator<String> subKeys = subObj.keys();
                    while (subKeys.hasNext()) {
                        String subKey = subKeys.next();
                        if (subObj.isNull(subKey)) {
                            subMap.remove(subKey);
                        } else {
                            String value = subObj.getString(subKey);
                            value = replace(value);
                            subMap.put(subKey, value);
                        }
                    }
                } else if (metaInfoObj.isNull(key)) {
                    String capitalizedKey = key.substring(0, 1).toUpperCase() + key.substring(1);
                    final Method method = PushMetaInfo.class.getDeclaredMethod("unset" + capitalizedKey);
                    method.invoke(metaInfo);
                } else {
                    String value = metaInfoObj.getString(key);
                    Object typedValue = null;
                    if (long.class == type) {
                        typedValue = Long.parseLong(value);
                    } else if (int.class == type) {
                        typedValue = Integer.parseInt(value);
                    } else if (boolean.class == type) {
                        typedValue = Boolean.parseBoolean(value);
                    } else {
                        // Assume String
                        value = replace(value);
                        typedValue = value;
                    }
                    field.set(metaInfo, typedValue);
                }
            }
        }

        @NonNull
        private String replace(String value) {
            Pattern pattern = Pattern.compile("(?<!\\\\)\\$\\{([^}]+)\\}");
            Matcher matcher = pattern.matcher(value);
            while (matcher.find()) {
                String groupName = matcher.group(1);
                if (matchGroup.containsKey(groupName)) {
                    value = value.replaceAll(Pattern.quote(matcher.group()), matchGroup.get(groupName));
                }
            }
            return value;
        }

        private boolean mismatchField(JSONObject obj, String key, Object value) throws JSONException {
            if (obj.isNull(key)) {
                return value != null;
            } else if (value == null) {
                return true;
            }

            String regex = obj.getString(key);
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(value.toString());
            if (!matcher.find()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Set<String> groups = getNamedGroupCandidates(regex);
                for (String name : groups) {
                    matchGroup.put(name, matcher.group(name));
                }
            }
            return false;
        }

        private Set<String> getNamedGroupCandidates(String regex) {
            Set<String> namedGroups = new HashSet<>();
            Matcher m = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").matcher(regex);
            while (m.find()) {
                namedGroups.add(m.group(1));
            }
            return namedGroups;
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

                String errmsg = e.toString();
                Pattern pattern = Pattern.compile("\\d+");
                Matcher matcher = pattern.matcher(errmsg);
                if (matcher.find()) {
                    int pos = Integer.parseInt(matcher.group());
                    errmsg = errmsg.substring(0, matcher.end()) + errmsg.substring(pos);
                }
                Toast.makeText(context, errmsg, Toast.LENGTH_LONG).show();
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
            Object config = configsObj.get(i);
            if (config instanceof String) {
                List<PackageConfig> existConfigs = this.packageConfigs.get(config.toString());
                configs.addAll(existConfigs);
            } else {
                configs.add(parseConfig(configsObj.getJSONObject(i)));
            }
        }
        return configs;
    }

    @NonNull
    private PackageConfig parseConfig(JSONObject configObj) throws JSONException {
        PackageConfig config = new PackageConfig();
        if (!configObj.isNull(PackageConfig.KEY_META_INFO)) {
            config.metaInfoObj = configObj.getJSONObject(PackageConfig.KEY_META_INFO);
        }
        if (!configObj.isNull(PackageConfig.KEY_NEW_META_INFO)) {
            config.newMetaInfoObj = configObj.getJSONObject(PackageConfig.KEY_NEW_META_INFO);
        }
        if (!configObj.isNull(PackageConfig.KEY_OPERATION)) {
            String operations = configObj.getString(PackageConfig.KEY_OPERATION);
            config.operation = new HashSet<>(Arrays.asList(operations.split("[\\s|]+")));
        }
        if (!configObj.isNull(PackageConfig.KEY_STOP)) {
            config.stop = configObj.getBoolean(PackageConfig.KEY_STOP);
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
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show();
        }
        return stringBuilder.toString();
    }

    public Set<String> handle(String packageName, PushMetaInfo metaInfo)
            throws JSONException, NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String[] checkPkgs = new String[]{"^", packageName, "$"};
        Set<String> operations = new HashSet<>();
        for (String pkg : checkPkgs) {
            List<PackageConfig> configs = packageConfigs.get(pkg);
            logger.i("package: " + packageName + ", config count: " + (configs == null ? 0 : configs.size()));
            boolean stop = doHandle(metaInfo, configs, operations);
            if (stop) {
                return operations;
            };
        }
        return operations;
    }

    @Nullable
    private boolean doHandle(PushMetaInfo metaInfo, List<PackageConfig> configs, Set<String> operations)
            throws NoSuchFieldException, IllegalAccessException, JSONException, NoSuchMethodException, InvocationTargetException {
        if (configs != null) {
            for (PackageConfig config : configs) {
                if (config.match(metaInfo)) {
                    config.replace(metaInfo);
                    operations.addAll(config.operation);
                    if (config.stop) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
