package com.xiaomi.channel.commonutils.misc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/misc/CollectionUtils.class */
public class CollectionUtils {
    public static <T> List<T> appendFromPosition(List<T> list, List<T> list2, int i) {
        if (list == null || list.isEmpty()) {
            ArrayList<T> arrayList = new ArrayList<>();
            if (list2 != null) {
                arrayList.addAll(list2);
            }
            return arrayList;
        }
        if (list2 == null || list2.isEmpty()) {
            return list;
        }
        int size = i;
        if (i > list.size()) {
            size = list.size();
        }
        ArrayList<T> arrayList2 = new ArrayList<>(list);
        arrayList2.addAll(size, list2);
        return arrayList2;
    }

    public static <T> ArrayList<T> copyFrom(List<T> list) {
        if (list == null) {
            return null;
        }
        return new ArrayList<>(list);
    }

    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    public static <T> List<T> notNull(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    public static <T> List<T> replaceFromPosition(List<T> list, List<T> list2, int i) {
        if (list == null || list.isEmpty()) {
            return list2;
        }
        if (list2 == null || list2.isEmpty()) {
            return list;
        }
        int size = i;
        if (i > list.size()) {
            size = list.size();
        }
        ArrayList<T> arrayList = new ArrayList<>(list);
        int size2 = list2.size();
        arrayList.addAll(size, list2);
        while (arrayList.size() > size2 + size) {
            arrayList.remove(arrayList.size() - 1);
        }
        return arrayList;
    }
}
