package com.xiaomi.xmsf.push.utils;

import android.os.Build;

import androidx.annotation.NonNull;

import org.apache.thrift.TBase;
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

    JSONObject cfgMatch;
    JSONObject cfgReplace;
    Set<String> operation = new HashSet<>();
    boolean stop = true;

    Map<String, String> matchGroup;

    public PackageConfig(Configurations configurations) {
        this.configurations = configurations;
    }

    public boolean match(TBase data) throws NoSuchFieldException, IllegalAccessException {
        matchGroup = new HashMap<>();
        if (cfgMatch == null) {
            return true;
        }
        Iterator<String> cfgKeys = cfgMatch.keys();
        while (cfgKeys.hasNext()) {
            String cfgKey = cfgKeys.next();
            final Field field = data.getClass().getDeclaredField(cfgKey);
            Object value = field.get(data);

            if (value instanceof Map) {
                Map subMap = (Map) value;
                JSONObject cfgSubObj = null;
                try {
                    cfgSubObj = cfgMatch.getJSONObject(cfgKey);
                } catch (JSONException e) {
                    throw new NoSuchFieldException(
                            String.format("The type of field \"%s\" is %s, not %s", cfgKey,
                                    value.getClass().getSimpleName(), cfgMatch.opt(cfgKey).getClass()));
                }

                Iterator<String> cfgSubKeys = cfgSubObj.keys();
                while (cfgSubKeys.hasNext()) {
                    String cfgSubKey = cfgSubKeys.next();
                    if (mismatchField(cfgSubObj, cfgSubKey, subMap.get(cfgSubKey))) {
                        return false;
                    }
                }
            } else {
                if (mismatchField(cfgMatch, cfgKey, value)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void replace(TBase data) throws NoSuchFieldException, IllegalAccessException {
        JSONObject cfgReplace = this.cfgReplace;
        if (cfgReplace == null) {
            return;
        }
        Iterator<String> cfgKeys = cfgReplace.keys();
        while (cfgKeys.hasNext()) {
            String cfgKey = cfgKeys.next();
            final Field field = data.getClass().getDeclaredField(cfgKey);

            if (Map.class.isAssignableFrom(field.getType())) {
                Map subMap = (Map) field.get(data);
                JSONObject cfgSubObj = null;
                try {
                    cfgSubObj = cfgReplace.getJSONObject(cfgKey);
                } catch (JSONException e) {
                    throw new NoSuchFieldException(
                            String.format("The type of field \"%s\" is %s, not %s", cfgKey,
                                    field.getType().getSimpleName(), cfgReplace.opt(cfgKey).getClass()));
                }

                Iterator<String> cfgSubKeys = cfgSubObj.keys();
                while (cfgSubKeys.hasNext()) {
                    String cfgSubKey = cfgSubKeys.next();
                    if (cfgSubObj.isNull(cfgSubKey)) {
                        subMap.remove(cfgSubKey);
                    } else {
                        Object cfgSubVal = cfgSubObj.opt(cfgSubKey);
                        if (cfgSubVal instanceof JSONArray) {
                            Object value = configurations.evaluate(cfgSubVal, this);
                            if (value == null) {
                                subMap.remove(cfgSubKey);
                            } else {
                                subMap.put(cfgSubKey, value.toString());
                            }
                        } else {
                            String cfgValue = cfgSubObj.optString(cfgSubKey);
                            cfgValue = replace(cfgValue);
                            subMap.put(cfgSubKey, cfgValue);
                        }
                    }
                }
            } else {
                Object cfgValueObj = cfgReplace.opt(cfgKey);
                boolean evaluated = cfgValueObj instanceof JSONArray;
                if (evaluated) {
                    cfgValueObj = configurations.evaluate(cfgValueObj, this);
                }
                if (cfgReplace.isNull(cfgKey) || cfgValueObj == null) {
                    try {
                        String capitalizedKey = cfgKey.substring(0, 1).toUpperCase() + cfgKey.substring(1);
                        final Method method;
                        method = data.getClass().getDeclaredMethod("unset" + capitalizedKey);
                        method.invoke(data);
                    } catch (NoSuchMethodException | InvocationTargetException e) {
                        // Ignore
                    }
                } else {
                    String value = cfgValueObj.toString();
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
                    field.set(data, typedValue);
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

    private boolean mismatchField(JSONObject obj, String cfgKey, Object value) {
        if (obj.isNull(cfgKey)) {
            return value != null;
        } else if (value == null) {
            return true;
        }

        String regex = obj.optString(cfgKey);
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
