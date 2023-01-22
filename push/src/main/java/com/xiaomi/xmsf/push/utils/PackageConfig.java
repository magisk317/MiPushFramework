package com.xiaomi.xmsf.push.utils;

import android.os.Build;

import androidx.annotation.NonNull;

import com.xiaomi.xmpush.thrift.PushMetaInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageConfig {
    public static final String KEY_META_INFO = "metaInfo";
    public static final String KEY_NEW_META_INFO = "newMetaInfo";
    public static final String KEY_OPERATION = "operation";
    public static final String KEY_STOP = "stop";

    public static final String OPERATION_OPEN = "open";
    public static final String OPERATION_IGNORE = "ignore";
    public static final String OPERATION_NOTIFY = "notify";
    public static final String OPERATION_WAKE = "wake";

    private Configurations configurations;

    JSONObject metaInfoObj;
    JSONObject newMetaInfoObj;
    Set<String> operation = new HashSet<>();
    boolean stop = true;

    Map<String, String> matchGroup;

    public PackageConfig(Configurations configurations) {
        this.configurations = configurations;
    }

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
                            Object value = configurations.evaluate(subVal, this);
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
            } else {
                Object valueObj = metaInfoObj.get(key);
                boolean evaluated = valueObj instanceof JSONArray;
                if (evaluated) {
                    valueObj = configurations.evaluate(valueObj, this);
                }
                if (metaInfoObj.isNull(key) || valueObj == null) {
                    String capitalizedKey = key.substring(0, 1).toUpperCase() + key.substring(1);
                    final Method method = PushMetaInfo.class.getDeclaredMethod("unset" + capitalizedKey);
                    method.invoke(metaInfo);
                } else {
                    String value = valueObj.toString();
                    if (!evaluated) {
                        value = replace(value);
                    }
                    Object typedValue = null;
                    final Class<?> fieldType = field.getType();
                    if (long.class == fieldType) {
                        typedValue = Long.parseLong(value);
                    } else if (int.class == fieldType) {
                        typedValue = (int) Long.parseLong(value);
                    } else if (boolean.class == fieldType) {
                        typedValue = Boolean.parseBoolean(value);
                    } else {
                        // Assume String
                        typedValue = value;
                    }
                    field.set(metaInfo, typedValue);
                }
            }
        }
    }

    @NonNull
    private String replace(String value) {
        Pattern pattern = Pattern.compile("\\${2}|\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(value);
        StringBuilder sb = new StringBuilder(value);
        class Pair {
            int start;
            int end;
            String str = null;
        }
        List<Pair> pairs = new ArrayList<>();
        while (matcher.find()) {
            Pair pair = new Pair();
            pair.start = matcher.start();
            pair.end = matcher.end();
            if (matcher.groupCount() == 0) {
                pair.str = "$";
            } else {
                String groupName = matcher.group(1);
                if (matchGroup.containsKey(groupName)) {
                    pair.str = matchGroup.get(groupName);
                }
            }
            if (pair.str != null) {
                pairs.add(pair);
            }
        }

        for (int i = pairs.size() - 1; i >= 0; --i) {
            Pair pair = pairs.get(i);
            sb.replace(pair.start, pair.end, pair.str);
        }
        return sb.toString();
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
