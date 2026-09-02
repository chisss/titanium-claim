package com.titanium.claim.infrastructure.repository;

import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;

/**
 * 理赔配置子域 JSON 列反序列化工具（infrastructure 内部）
 * <p>
 * 配置表集合字段落库 JSON 文本（聚合 → DO 方向由 MapStruct 序列化），DO → 聚合方向
 * 经本工具还原为不可变集合，空文本安全返回空集合。
 * </p>
 */
final class JsonSupport {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Integer>> STRING_INT_MAP_TYPE = new TypeReference<>() {
    };

    private JsonSupport() {
    }

    /** JSON 数组文本 → 字符串列表（空安全） */
    static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> list = JSON.parseObject(json, STRING_LIST_TYPE);
        return list == null ? List.of() : list;
    }

    /** JSON 对象文本 → 字符串→整数映射（空安全） */
    static Map<String, Integer> toStringIntegerMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> map = JSON.parseObject(json, STRING_INT_MAP_TYPE);
        return map == null ? Map.of() : map;
    }
}
