package org.apache.thrift;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TBaseHelper.class */
public final class TBaseHelper {
    private static final Comparator comparator = new NestedStructureComparator();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:org/apache/thrift/TBaseHelper$NestedStructureComparator.class */
    private static class NestedStructureComparator implements Comparator {
        private NestedStructureComparator() {
        }

        @Override // java.util.Comparator
        public int compare(Object obj, Object obj2) {
            if (obj == null && obj2 == null) {
                return 0;
            }
            if (obj == null) {
                return -1;
            }
            if (obj2 == null) {
                return 1;
            }
            return obj instanceof List ? TBaseHelper.compareTo((List) obj, (List) obj2) : obj instanceof Set ? TBaseHelper.compareTo((Set) obj, (Set) obj2) : obj instanceof Map ? TBaseHelper.compareTo((Map) obj, (Map) obj2) : obj instanceof byte[] ? TBaseHelper.compareTo((byte[]) obj, (byte[]) obj2) : TBaseHelper.compareTo((Comparable) obj, (Comparable) obj2);
        }
    }

    private TBaseHelper() {
    }

    public static int byteBufferToByteArray(ByteBuffer byteBuffer, byte[] bArr, int i) {
        int iRemaining = byteBuffer.remaining();
        System.arraycopy(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), bArr, i, iRemaining);
        return iRemaining;
    }

    public static byte[] byteBufferToByteArray(ByteBuffer byteBuffer) {
        if (wrapsFullArray(byteBuffer)) {
            return byteBuffer.array();
        }
        byte[] bArr = new byte[byteBuffer.remaining()];
        byteBufferToByteArray(byteBuffer, bArr, 0);
        return bArr;
    }

    public static int compareTo(byte b, byte b2) {
        if (b < b2) {
            return -1;
        }
        return b2 < b ? 1 : 0;
    }

    public static int compareTo(double d, double d2) {
        if (d < d2) {
            return -1;
        }
        return d2 < d ? 1 : 0;
    }

    public static int compareTo(int i, int i2) {
        if (i < i2) {
            return -1;
        }
        return i2 < i ? 1 : 0;
    }

    public static int compareTo(long j, long j2) {
        if (j < j2) {
            return -1;
        }
        return j2 < j ? 1 : 0;
    }

    public static int compareTo(Comparable comparable, Comparable comparable2) {
        return comparable.compareTo(comparable2);
    }

    public static int compareTo(Object obj, Object obj2) {
        if (obj instanceof Comparable) {
            return compareTo((Comparable) obj, (Comparable) obj2);
        }
        if (obj instanceof List) {
            return compareTo((List) obj, (List) obj2);
        }
        if (obj instanceof Set) {
            return compareTo((Set) obj, (Set) obj2);
        }
        if (obj instanceof Map) {
            return compareTo((Map) obj, (Map) obj2);
        }
        if (obj instanceof byte[]) {
            return compareTo((byte[]) obj, (byte[]) obj2);
        }
        throw new IllegalArgumentException("Cannot compare objects of type " + obj.getClass());
    }

    public static int compareTo(String str, String str2) {
        return str.compareTo(str2);
    }

    public static int compareTo(List list, List list2) {
        int iCompareTo = compareTo(list.size(), list2.size());
        if (iCompareTo != 0) {
            return iCompareTo;
        }
        for (int i = 0; i < list.size(); i++) {
            int iCompare = comparator.compare(list.get(i), list2.get(i));
            if (iCompare != 0) {
                return iCompare;
            }
        }
        return 0;
    }

    public static int compareTo(Map map, Map map2) {
        int iCompareTo = compareTo(map.size(), map2.size());
        if (iCompareTo != 0) {
            return iCompareTo;
        }
        Comparator comparator2 = comparator;
        TreeMap treeMap = new TreeMap(comparator2);
        treeMap.putAll(map);
        Iterator it = treeMap.entrySet().iterator();
        TreeMap treeMap2 = new TreeMap(comparator2);
        treeMap2.putAll(map2);
        Iterator it2 = treeMap2.entrySet().iterator();
        while (it.hasNext() && it2.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            Map.Entry entry2 = (Map.Entry) it2.next();
            Comparator comparator3 = comparator;
            int iCompare = comparator3.compare(entry.getKey(), entry2.getKey());
            if (iCompare != 0) {
                return iCompare;
            }
            int iCompare2 = comparator3.compare(entry.getValue(), entry2.getValue());
            if (iCompare2 != 0) {
                return iCompare2;
            }
        }
        return 0;
    }

    public static int compareTo(Set set, Set set2) {
        int iCompareTo = compareTo(set.size(), set2.size());
        if (iCompareTo != 0) {
            return iCompareTo;
        }
        Comparator comparator2 = comparator;
        TreeSet treeSet = new TreeSet(comparator2);
        treeSet.addAll(set);
        TreeSet treeSet2 = new TreeSet(comparator2);
        treeSet2.addAll(set2);
        Iterator it = treeSet.iterator();
        Iterator it2 = treeSet2.iterator();
        while (it.hasNext() && it2.hasNext()) {
            int iCompare = comparator.compare(it.next(), it2.next());
            if (iCompare != 0) {
                return iCompare;
            }
        }
        return 0;
    }

    public static int compareTo(short s, short s2) {
        if (s < s2) {
            return -1;
        }
        return s2 < s ? 1 : 0;
    }

    public static int compareTo(boolean z, boolean z2) {
        return Boolean.valueOf(z).compareTo(Boolean.valueOf(z2));
    }

    public static int compareTo(byte[] bArr, byte[] bArr2) {
        int iCompareTo = compareTo(bArr.length, bArr2.length);
        if (iCompareTo != 0) {
            return iCompareTo;
        }
        for (int i = 0; i < bArr.length; i++) {
            int iCompareTo2 = compareTo(bArr[i], bArr2[i]);
            if (iCompareTo2 != 0) {
                return iCompareTo2;
            }
        }
        return 0;
    }

    public static ByteBuffer copyBinary(ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return null;
        }
        ByteBuffer byteBufferWrap = ByteBuffer.wrap(new byte[byteBuffer.remaining()]);
        if (byteBuffer.hasArray()) {
            System.arraycopy(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBufferWrap.array(), 0, byteBuffer.remaining());
        } else {
            byteBuffer.slice().get(byteBufferWrap.array());
        }
        return byteBufferWrap;
    }

    public static byte[] copyBinary(byte[] bArr) {
        if (bArr == null) {
            return null;
        }
        byte[] bArr2 = new byte[bArr.length];
        System.arraycopy(bArr, 0, bArr2, 0, bArr.length);
        return bArr2;
    }

    public static String paddedByteString(byte b) {
        return Integer.toHexString((b | 256) & 511).toUpperCase().substring(1);
    }

    public static ByteBuffer rightSize(ByteBuffer byteBuffer) {
        return wrapsFullArray(byteBuffer) ? byteBuffer : ByteBuffer.wrap(byteBufferToByteArray(byteBuffer));
    }

    public static void toString(ByteBuffer byteBuffer, StringBuilder sb) {
        byte[] bArrArray = byteBuffer.array();
        int iArrayOffset = byteBuffer.arrayOffset();
        int iLimit = byteBuffer.limit();
        int i = iLimit - iArrayOffset > 128 ? iArrayOffset + 128 : iLimit;
        for (int i2 = iArrayOffset; i2 < i; i2++) {
            if (i2 > iArrayOffset) {
                sb.append(" ");
            }
            sb.append(paddedByteString(bArrArray[i2]));
        }
        if (iLimit != i) {
            sb.append("...");
        }
    }

    public static boolean wrapsFullArray(ByteBuffer byteBuffer) {
        return byteBuffer.hasArray() && byteBuffer.position() == 0 && byteBuffer.arrayOffset() == 0 && byteBuffer.remaining() == byteBuffer.capacity();
    }
}
