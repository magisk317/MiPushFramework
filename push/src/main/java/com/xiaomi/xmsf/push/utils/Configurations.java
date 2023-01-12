package com.xiaomi.xmsf.push.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.google.gson.GsonBuilder;
import com.xiaomi.xmpush.thrift.PushMetaInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

import top.trumeet.common.utils.Utils;

public class Configurations {
    private static final Logger logger = XLog.tag(Configurations.class.getSimpleName()).build();

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
                            Object subVal = subObj.opt(subKey);
                            if (subVal instanceof JSONArray) {
                                Object value = evaluate((JSONArray) subVal, this);
                                if (value == null) {
                                    subMap.remove(subKey);
                                } else {
                                    subMap.put(subKey, value.toString());
                                }
                            } else {
                                String value = subObj.getString(subKey);
                                value = replace(value);
                                subMap.put(subKey, value);
                            }
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
            List<String> groups = getNamedGroupCandidates(regex);
            for (int i = 0; i < groups.size(); ++i) {
                String name = groups.get(i);
                matchGroup.put(name,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                                matcher.group(name) :
                                matcher.group(i + 1));
            }
            return false;
        }

        private ArrayList<String> getNamedGroupCandidates(String regex) {
            ArrayList<String> namedGroups = new ArrayList<>();
            Matcher m = Pattern.compile("(?<!\\\\)\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").matcher(regex);
            while (m.find()) {
                namedGroups.add(m.group(1));
            }
            return namedGroups;
        }
    }

    private String version;
    private Map<String, List<Object>> packageConfigs = new HashMap<>();

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
        packageConfigs.clear();
        do {
            if (context == null || treeUri == null) {
                break;
            }
            List<Pair<DocumentFile, JSONException>> exceptions = new ArrayList<>();
            List<DocumentFile> loadedFiles = new ArrayList<>();
            parseDirectory(context, treeUri, exceptions, loadedFiles);

            if (!loadedFiles.isEmpty()) {
                StringBuilder loadedList = new StringBuilder("loaded configuration list:");
                for (DocumentFile file : loadedFiles) {
                    loadedList.append('\n');
                    loadedList.append(file.getName());
                }
                Utils.makeText(context, loadedList, Toast.LENGTH_SHORT);
            }
            if (!exceptions.isEmpty()) {
                for (Pair<DocumentFile, JSONException> pair : exceptions) {
                    StringBuilder errmsg = getJsonExceptionMessage(context, pair);
                    Utils.makeText(context, errmsg.toString(), Toast.LENGTH_LONG);
                }
                break;
            }
            return true;
        } while (false);
        return false;
    }

    @NonNull
    public static StringBuilder getJsonExceptionMessage(Context context, Pair<DocumentFile, JSONException> pair) {
        DocumentFile file = pair.first;
        JSONException e = pair.second;
        e.printStackTrace();

        StringBuilder errmsg = new StringBuilder(e.toString());
        Pattern pattern = Pattern.compile(" character (\\d+) of ");
        Matcher matcher = pattern.matcher(errmsg.toString());
        if (matcher.find()) {
            int pos = Integer.parseInt(matcher.group(1));
            String json = readTextFromUri(context, file.getUri());
            String[] beforeErr = json.substring(0, pos).split("\n");
            int errorLine = beforeErr.length;
            int errorColumn = beforeErr[beforeErr.length - 1].length();
            String exceptionMessage = errmsg.substring(0, matcher.start())
                    .replace("org.json.JSONException: ", "")
                    .replaceFirst("(after )(.*)( at)", "$1\"$2\"$3");
            errmsg = new StringBuilder(String.format("%s line %d column %d", exceptionMessage, errorLine, errorColumn));

            String[] jsonLine = json.split("\n");
            jsonLine[errorLine - 1] = jsonLine[errorLine - 1].substring(0, errorColumn - 1) +
                    "┋" +
                    jsonLine[errorLine - 1].substring(errorColumn - 1);
            for (int i = Math.max(0, errorLine - 2); i <= Math.min(jsonLine.length - 1, errorLine); ++i) {
                errmsg.append('\n');
                errmsg.append(i + 1);
                errmsg.append(": ");
                errmsg.append(jsonLine[i]);
            }
        }
        errmsg.insert(0, file.getName() + "\n");
        return errmsg;
    }

    private boolean parseDirectory(Context context, Uri treeUri, List<Pair<DocumentFile, JSONException>> exceptions, List<DocumentFile> loadedFiles) {
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, treeUri);
        if (documentFile == null) {
            return true;
        }
        DocumentFile[] files = documentFile.listFiles();
        for (DocumentFile file : files) {
            if (!"application/json".equals(file.getType())) {
                continue;
            }
            String json = readTextFromUri(context, file.getUri());
            try {
                parse(json);
                loadedFiles.add(file);
            } catch (JSONException e) {
                exceptions.add(new Pair<>(file, e));
            }
        }
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
    private ArrayList<Object> parseConfigs(JSONArray configsObj) throws JSONException {
        ArrayList<Object> configs = new ArrayList<>();
        for (int i = 0; i < configsObj.length(); ++i) {
            Object config = configsObj.get(i);
            if (config instanceof JSONArray) {
                configs.add(config);
            } else if (config instanceof String) {
                configs.add(config);
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


    public static String readTextFromUri(Context context, Uri uri) {
        StringBuilder stringBuilder = new StringBuilder();
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(Objects.requireNonNull(inputStream)))) {
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Utils.makeText(context, e.toString(), Toast.LENGTH_LONG);
        }
        return stringBuilder.toString();
    }

    public Set<String> handle(String packageName, PushMetaInfo metaInfo)
            throws JSONException, NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String[] checkPkgs = new String[]{"^", packageName, "$"};
        Set<String> operations = new HashSet<>();
        for (String pkg : checkPkgs) {
            List<Object> configs = packageConfigs.get(pkg);
            logger.d("package: " + packageName + ", config count: " + (configs == null ? 0 : configs.size()));
            boolean stop = doHandle(metaInfo, configs, operations);
            if (stop) {
                return operations;
            }
        }
        return operations;
    }

    private boolean doHandle(PushMetaInfo metaInfo, List<Object> configs, Set<String> operations)
            throws JSONException, NoSuchFieldException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        return doHandle(metaInfo, configs, operations, new ArrayList<>());
    }

    private boolean doHandle(PushMetaInfo metaInfo, List<Object> configs, Set<String> operations, List<Object> matched)
            throws NoSuchFieldException, IllegalAccessException, JSONException, NoSuchMethodException, InvocationTargetException {
        if (configs != null && !matched.contains(configs)) {
            matched.add(configs);
            for (Object configItem : configs) {
                if (configItem instanceof PackageConfig) {
                    PackageConfig config = (PackageConfig) configItem;
                    if (config.match(metaInfo)) {
                        logger.d(new GsonBuilder().disableHtmlEscaping().create().toJson(config));
                        config.replace(metaInfo);
                        logger.d(new GsonBuilder().disableHtmlEscaping().create().toJson(metaInfo));
                        operations.addAll(config.operation);
                        if (config.stop) {
                            return true;
                        }
                    }
                } else {
                    List<Object> refConfigs = null;
                    if (configItem instanceof JSONArray) {
                        Object value = evaluate(configItem, metaInfo);
                        if (value instanceof JSONObject) {
                            refConfigs = new ArrayList<>();
                            refConfigs.add(parseConfig((JSONObject) value));
                        } else if (value != null) {
                            refConfigs = packageConfigs.get(value.toString());
                        }
                    } else {
                        refConfigs = packageConfigs.get(configItem);
                    }
                    if (refConfigs != null) {
                        boolean stop = doHandle(metaInfo, refConfigs, operations);
                        if (stop) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean match(PushMetaInfo metaInfo, List<Object> configs, List<Object> matched)
            throws NoSuchFieldException, IllegalAccessException, JSONException {
        if (configs != null && !matched.contains(configs)) {
            matched.add(configs);
            for (Object configItem : configs) {
                if (configItem instanceof PackageConfig) {
                    PackageConfig config = (PackageConfig) configItem;
                    if (config.match(metaInfo)) {
                        return true;
                    }
                } else {
                    List<Object> refConfigs = packageConfigs.get(configItem);
                    return match(metaInfo, refConfigs, matched);
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface Callable {
        Object run();
    }

    private Object evaluate(Object expr, PackageConfig env) {
        return evaluate(expr, env, null);
    }

    private Object evaluate(Object expr, PushMetaInfo env) {
        return evaluate(expr, null, env);
    }

    private Object evaluate(Object expr, PackageConfig config, PushMetaInfo metaInfo) {
        if (expr instanceof String) {
            return expr;
        }
        if (expr instanceof JSONObject) {
            return expr;
        }
        if (expr instanceof JSONArray) {
            return evaluate((JSONArray) expr, config, metaInfo);
        }
        return null;
    }

    private Object evaluate(JSONArray expr, PackageConfig config, PushMetaInfo metaInfo) {
        String method = (String) evaluate(expr.opt(0), config);
        if (method == null) {
            return null;
        }

        int length = expr.length();
        try {
            if ("cond".equals(method)) {
                for (int i = 1; i < length; ++i) {
                    JSONArray clause = expr.optJSONArray(i);
                    Object test = clause.opt(0);
                    if (test instanceof JSONObject) {
                        if (parseConfig((JSONObject) test).match(metaInfo)) {
                            return clause.opt(1);
                        }
                    }
                    if (test instanceof String) {
                        if (match(metaInfo, packageConfigs.get(test), new ArrayList<>())) {
                            return clause.opt(1);
                        }
                    }
                    if (test instanceof JSONArray) {
                        if (Boolean.TRUE.equals(evaluate(test, config, metaInfo))) {
                            Object ret = null;
                            for (int j = 1; j < clause.length(); ++j) {
                                ret = evaluate(expr.opt(j), config);
                            }
                            return ret;
                        }
                    }
                }
                return null;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }

        JSONArray evaluated = new JSONArray();
        evaluated.put(method);
        for (int i = 1; i < length; ++i) {
            evaluated.put(evaluate(expr.opt(i), config));
        }

        Map<String, Callable> methods = new HashMap<>();
        methods.put("$", () -> config.matchGroup.get(evaluated.optString(1)));
        methods.put("decode-uri", () -> {
            try {
                return URLDecoder.decode(evaluated.optString(1), StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return null;
            }
        });
        methods.put("parse-json", () -> {
            try {
                return new JSONTokener(evaluated.optString(1)).nextValue();
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        });
        methods.put("property", () -> {
            Object obj = evaluated.opt(2);
            if (obj instanceof JSONObject) {
                return ((JSONObject) obj).opt(evaluated.optString(1));
            }
            if (obj instanceof JSONArray) {
                return ((JSONArray) obj).opt(evaluated.optInt(1));
            }
            return null;
        });

        Callable ret = methods.get(method);
        return ret == null ? null : ret.run();
    }
}
