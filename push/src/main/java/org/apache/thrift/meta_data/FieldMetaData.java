package org.apache.thrift.meta_data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.TBase;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/FieldMetaData.class */
public class FieldMetaData implements Serializable {
    private static Map<Class<? extends TBase>, Map<?, FieldMetaData>> structMap = new HashMap();
    public final String fieldName;
    public final byte requirementType;
    public final FieldValueMetaData valueMetaData;

    public FieldMetaData(String str, byte b, FieldValueMetaData fieldValueMetaData) {
        this.fieldName = str;
        this.requirementType = b;
        this.valueMetaData = fieldValueMetaData;
    }

    public static void addStructMetaDataMap(Class<? extends TBase> cls, Map<?, FieldMetaData> map) {
        structMap.put(cls, map);
    }

    public static Map<?, FieldMetaData> getStructMetaDataMap(Class<? extends TBase> cls) {
        if (!structMap.containsKey(cls)) {
            try {
                cls.newInstance();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("IllegalAccessException for TBase class: " + cls.getName() + ", message: " + e.getMessage());
            } catch (InstantiationException e2) {
                throw new RuntimeException("InstantiationException for TBase class: " + cls.getName() + ", message: " + e2.getMessage());
            }
        }
        return structMap.get(cls);
    }
}
