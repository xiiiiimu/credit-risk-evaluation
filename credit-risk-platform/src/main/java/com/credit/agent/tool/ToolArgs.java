package com.credit.agent.tool;

import cn.hutool.core.convert.Convert;

import java.util.List;
import java.util.Map;

public final class ToolArgs {

    private ToolArgs() {
    }

    public static String getString(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static Integer getInt(Map<String, Object> args, String key, int defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        return Convert.toInt(args.get(key), defaultValue);
    }

    public static Boolean getBoolean(Map<String, Object> args, String key, boolean defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        Object v = args.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return Convert.toBool(v, defaultValue);
    }

    public static Long getLong(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        return Convert.toLong(args.get(key), null);
    }

    public static Double getDouble(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        return Convert.toDouble(args.get(key));
    }

    @SuppressWarnings("unchecked")
    public static List<Long> getLongList(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        Object raw = args.get(key);
        if (raw instanceof List) {
            List<?> list = (List<?>) raw;
            return list.stream().map(Convert::toLong).collect(java.util.stream.Collectors.toList());
        }
        return null;
    }
}
