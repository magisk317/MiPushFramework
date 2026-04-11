package org.apache.thrift.meta_data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.TBase;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/meta_data/FieldMetaData.class */
public class FieldMetaData implements Serializable {
    private static Map<Class<? extends TBase>, Map<?, FieldMetaData>> structMap = new HashMap<>();
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
                cls.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to initialize TBase class: " + cls.getName() + ", message: " + e.getMessage());
            }
        }
        return structMap.get(cls);
    }
}
