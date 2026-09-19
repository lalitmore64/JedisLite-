package com.jedislite.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public sealed interface RedisObject permits
        RedisObject.StringObj,
        RedisObject.HashObj,
        RedisObject.ListObj,
        RedisObject.SetObj {

    RedisDataType type();

    boolean isEmpty();

    final class StringObj implements RedisObject {
        private byte[] value;

        public StringObj(byte[] value) {
            Objects.requireNonNull(value, "value cannot be null");
            this.value = value.clone();
        }

        public synchronized byte[] getValue() {
            return value.clone();
        }

        public synchronized void setValue(byte[] value) {
            Objects.requireNonNull(value, "value cannot be null");
            this.value = value.clone();
        }

        @Override
        public RedisDataType type() {
            return RedisDataType.STRING;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    final class HashObj implements RedisObject {
        private final Map<ByteArrayKey, byte[]> fields = new LinkedHashMap<>();

        public synchronized int set(Map<ByteArrayKey, byte[]> newFields) {
            int addedCount = 0;
            for (Map.Entry<ByteArrayKey, byte[]> entry : newFields.entrySet()) {
                if (fields.put(entry.getKey(), entry.getValue().clone()) == null) {
                    addedCount++;
                }
            }
            return addedCount;
        }

        public synchronized byte[] get(ByteArrayKey field) {
            byte[] val = fields.get(field);
            return val != null ? val.clone() : null;
        }

        public synchronized int del(List<ByteArrayKey> targetFields) {
            int removed = 0;
            for (ByteArrayKey field : targetFields) {
                if (fields.remove(field) != null) {
                    removed++;
                }
            }
            return removed;
        }

        public synchronized boolean exists(ByteArrayKey field) {
            return fields.containsKey(field);
        }

        public synchronized int len() {
            return fields.size();
        }

        public synchronized Map<ByteArrayKey, byte[]> getAll() {
            Map<ByteArrayKey, byte[]> copy = new LinkedHashMap<>(fields.size());
            for (Map.Entry<ByteArrayKey, byte[]> entry : fields.entrySet()) {
                copy.put(entry.getKey(), entry.getValue().clone());
            }
            return copy;
        }

        public synchronized List<ByteArrayKey> keys() {
            return new ArrayList<>(fields.keySet());
        }

        public synchronized List<byte[]> values() {
            List<byte[]> list = new ArrayList<>(fields.size());
            for (byte[] val : fields.values()) {
                list.add(val.clone());
            }
            return list;
        }

        @Override
        public synchronized boolean isEmpty() {
            return fields.isEmpty();
        }

        @Override
        public RedisDataType type() {
            return RedisDataType.HASH;
        }
    }

    final class ListObj implements RedisObject {
        private final LinkedList<byte[]> elements = new LinkedList<>();

        public synchronized int lpush(List<byte[]> values) {
            for (byte[] val : values) {
                elements.addFirst(val.clone());
            }
            return elements.size();
        }

        public synchronized int rpush(List<byte[]> values) {
            for (byte[] val : values) {
                elements.addLast(val.clone());
            }
            return elements.size();
        }

        public synchronized byte[] lpop() {
            if (elements.isEmpty()) {
                return null;
            }
            return elements.removeFirst();
        }

        public synchronized byte[] rpop() {
            if (elements.isEmpty()) {
                return null;
            }
            return elements.removeLast();
        }

        public synchronized int len() {
            return elements.size();
        }

        public synchronized byte[] lindex(int index) {
            int size = elements.size();
            int resolvedIndex = resolveIndex(index, size);
            if (resolvedIndex < 0 || resolvedIndex >= size) {
                return null;
            }
            return elements.get(resolvedIndex).clone();
        }

        public synchronized List<byte[]> lrange(int start, int stop) {
            int size = elements.size();
            if (size == 0) {
                return Collections.emptyList();
            }

            int resolvedStart = resolveIndex(start, size);
            int resolvedStop = resolveIndex(stop, size);

            if (resolvedStart < 0) resolvedStart = 0;
            if (resolvedStop >= size) resolvedStop = size - 1;

            if (resolvedStart > resolvedStop || resolvedStart >= size) {
                return Collections.emptyList();
            }

            List<byte[]> result = new ArrayList<>(resolvedStop - resolvedStart + 1);
            int i = 0;
            for (byte[] item : elements) {
                if (i >= resolvedStart && i <= resolvedStop) {
                    result.add(item.clone());
                }
                if (i > resolvedStop) {
                    break;
                }
                i++;
            }
            return result;
        }

        private int resolveIndex(int index, int size) {
            return index < 0 ? size + index : index;
        }

        @Override
        public synchronized boolean isEmpty() {
            return elements.isEmpty();
        }

        @Override
        public RedisDataType type() {
            return RedisDataType.LIST;
        }
    }

    final class SetObj implements RedisObject {
        private final Set<ByteArrayKey> members = new LinkedHashSet<>();

        public synchronized int sadd(List<ByteArrayKey> items) {
            int added = 0;
            for (ByteArrayKey item : items) {
                if (members.add(item)) {
                    added++;
                }
            }
            return added;
        }

        public synchronized int srem(List<ByteArrayKey> items) {
            int removed = 0;
            for (ByteArrayKey item : items) {
                if (members.remove(item)) {
                    removed++;
                }
            }
            return removed;
        }

        public synchronized boolean sismember(ByteArrayKey item) {
            return members.contains(item);
        }

        public synchronized Set<ByteArrayKey> smembers() {
            return new LinkedHashSet<>(members);
        }

        public synchronized int scard() {
            return members.size();
        }

        @Override
        public synchronized boolean isEmpty() {
            return members.isEmpty();
        }

        @Override
        public RedisDataType type() {
            return RedisDataType.SET;
        }
    }
}

