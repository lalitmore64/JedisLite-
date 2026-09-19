package com.jedislite.storage;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class RedisStore {

    private final Map<ByteArrayKey, RedisObject> store = new ConcurrentHashMap<>();
    private final Map<ByteArrayKey, Long> expirations = new ConcurrentHashMap<>();

    public int activeExpireCycle(int sampleSize, long maxDurationMillis) {
        if (expirations.isEmpty()) {
            return 0;
        }
        long startTime = System.currentTimeMillis();
        int totalPurged = 0;

        do {
            int sampled = 0;
            int expiredInSample = 0;
            java.util.Iterator<Map.Entry<ByteArrayKey, Long>> it = expirations.entrySet().iterator();

            while (it.hasNext() && sampled < sampleSize) {
                Map.Entry<ByteArrayKey, Long> entry = it.next();
                sampled++;
                if (System.currentTimeMillis() >= entry.getValue()) {
                    ByteArrayKey key = entry.getKey();
                    store.remove(key);
                    it.remove();
                    expiredInSample++;
                    totalPurged++;
                }
            }

            if (sampled == 0) {
                break;
            }

            boolean highExpiredRatio = (expiredInSample * 4) >= sampled;
            boolean timeBudgetRemaining = (System.currentTimeMillis() - startTime) < maxDurationMillis;

            if (!highExpiredRatio || !timeBudgetRemaining) {
                break;
            }
        } while (true);

        return totalPurged;
    }

    public int getExpiringKeysCount() {
        return expirations.size();
    }

    public boolean isExpired(ByteArrayKey key) {
        Long expireAt = expirations.get(key);
        if (expireAt != null && System.currentTimeMillis() >= expireAt) {
            store.remove(key);
            expirations.remove(key);
            return true;
        }
        return false;
    }

    public boolean del(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        expirations.remove(key);
        return store.remove(key) != null;
    }

    public boolean del(byte[] key) {
        return del(ByteArrayKey.of(key));
    }

    public int del(List<ByteArrayKey> keys) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        int count = 0;
        for (ByteArrayKey key : keys) {
            if (del(key)) {
                count++;
            }
        }
        return count;
    }

    public boolean exists(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return false;
        }
        return store.containsKey(key);
    }

    public boolean exists(byte[] key) {
        return exists(ByteArrayKey.of(key));
    }

    public int exists(List<ByteArrayKey> keys) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        int count = 0;
        for (ByteArrayKey key : keys) {
            if (exists(key)) {
                count++;
            }
        }
        return count;
    }

    public boolean expire(ByteArrayKey key, long seconds) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key) || !store.containsKey(key)) {
            return false;
        }
        if (seconds <= 0) {
            del(key);
            return true;
        }
        long expireAt = System.currentTimeMillis() + (seconds * 1000L);
        expirations.put(key, expireAt);
        return true;
    }

    public boolean expire(byte[] key, long seconds) {
        return expire(ByteArrayKey.of(key), seconds);
    }

    public boolean pexpire(ByteArrayKey key, long millis) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key) || !store.containsKey(key)) {
            return false;
        }
        if (millis <= 0) {
            del(key);
            return true;
        }
        long expireAt = System.currentTimeMillis() + millis;
        expirations.put(key, expireAt);
        return true;
    }

    public long ttl(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key) || !store.containsKey(key)) {
            return -2L;
        }
        Long expireAt = expirations.get(key);
        if (expireAt == null) {
            return -1L;
        }
        long remaining = (expireAt - System.currentTimeMillis() + 999) / 1000L;
        return Math.max(0L, remaining);
    }

    public long ttl(byte[] key) {
        return ttl(ByteArrayKey.of(key));
    }

    public RedisDataType type(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (isExpired(key)) {
            return RedisDataType.NONE;
        }
        RedisObject obj = store.get(key);
        return obj == null ? RedisDataType.NONE : obj.type();
    }

    public RedisDataType type(byte[] key) {
        return type(ByteArrayKey.of(key));
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        expirations.clear();
        store.clear();
    }

    public record DumpEntry(ByteArrayKey key, RedisObject object, Long expireAtMillis) {}

    public List<DumpEntry> dumpAll() {
        List<DumpEntry> list = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<ByteArrayKey, RedisObject> entry : store.entrySet()) {
            ByteArrayKey key = entry.getKey();
            Long expireAt = expirations.get(key);
            if (expireAt != null && now >= expireAt) {
                continue;
            }
            list.add(new DumpEntry(key, entry.getValue(), expireAt));
        }
        return list;
    }

    public void restore(ByteArrayKey key, RedisObject object, Long expireAtMillis) {
        if (expireAtMillis != null && System.currentTimeMillis() >= expireAtMillis) {
            return;
        }
        store.put(key, object);
        if (expireAtMillis != null) {
            expirations.put(key, expireAtMillis);
        } else {
            expirations.remove(key);
        }
    }

    public void set(ByteArrayKey key, byte[] value) {
        set(key, value, null, false, false);
    }

    public void set(byte[] key, byte[] value) {
        set(ByteArrayKey.of(key), value);
    }

    public boolean set(ByteArrayKey key, byte[] value, Long expireAtMillis, boolean nx, boolean xx) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");
        boolean keyExists = exists(key);
        if (nx && keyExists) {
            return false;
        }
        if (xx && !keyExists) {
            return false;
        }

        store.put(key, new RedisObject.StringObj(value));
        if (expireAtMillis != null) {
            expirations.put(key, expireAtMillis);
        } else {
            expirations.remove(key);
        }
        return true;
    }

    public byte[] get(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.StringObj str = getExisting(key, RedisObject.StringObj.class);
        return str != null ? str.getValue() : null;
    }

    public byte[] get(byte[] key) {
        return get(ByteArrayKey.of(key));
    }

    public long incrBy(ByteArrayKey key, long delta) {
        Objects.requireNonNull(key, "Key cannot be null");
        isExpired(key);

        long[] resultHolder = new long[1];
        store.compute(key, (k, existing) -> {
            long currentVal;
            if (existing == null) {
                currentVal = 0;
            } else if (existing instanceof RedisObject.StringObj str) {
                String s = new String(str.getValue(), StandardCharsets.US_ASCII);
                try {
                    currentVal = Long.parseLong(s);
                } catch (NumberFormatException e) {
                    throw new NumberFormatException("value is not an integer or out of range");
                }
            } else {
                throw new WrongTypeException();
            }

            try {
                long newVal = Math.addExact(currentVal, delta);
                resultHolder[0] = newVal;
                byte[] newBytes = Long.toString(newVal).getBytes(StandardCharsets.US_ASCII);
                return new RedisObject.StringObj(newBytes);
            } catch (ArithmeticException e) {
                throw new ArithmeticException("increment or decrement would overflow");
            }
        });

        return resultHolder[0];
    }

    public int hset(ByteArrayKey key, Map<ByteArrayKey, byte[]> fieldValues) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(fieldValues, "fieldValues cannot be null");
        RedisObject.HashObj hash = getOrCreate(key, RedisObject.HashObj.class, RedisObject.HashObj::new);
        return hash.set(fieldValues);
    }

    public byte[] hget(ByteArrayKey key, ByteArrayKey field) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(field, "Field cannot be null");
        RedisObject.HashObj hash = getExisting(key, RedisObject.HashObj.class);
        return hash != null ? hash.get(field) : null;
    }

    public int hdel(ByteArrayKey key, List<ByteArrayKey> fields) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(fields, "Fields cannot be null");
        RedisObject.HashObj hash = getExisting(key, RedisObject.HashObj.class);
        if (hash == null) {
            return 0;
        }
        int removed = hash.del(fields);
        evictIfEmpty(key, hash);
        return removed;
    }

    public boolean hexists(ByteArrayKey key, ByteArrayKey field) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(field, "Field cannot be null");
        RedisObject.HashObj hash = getExisting(key, RedisObject.HashObj.class);
        return hash != null && hash.exists(field);
    }

    public int hlen(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.HashObj hash = getExisting(key, RedisObject.HashObj.class);
        return hash != null ? hash.len() : 0;
    }

    public Map<ByteArrayKey, byte[]> hgetall(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.HashObj hash = getExisting(key, RedisObject.HashObj.class);
        return hash != null ? hash.getAll() : Collections.emptyMap();
    }

    public List<ByteArrayKey> hkeys(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.HashObj hash = getExisting(key, RedisObject.HashObj.class);
        return hash != null ? hash.keys() : Collections.emptyList();
    }

    public List<byte[]> hvals(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.HashObj hash = getExisting(key, RedisObject.HashObj.class);
        return hash != null ? hash.values() : Collections.emptyList();
    }

    public int lpush(ByteArrayKey key, List<byte[]> values) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(values, "Values cannot be null");
        RedisObject.ListObj list = getOrCreate(key, RedisObject.ListObj.class, RedisObject.ListObj::new);
        return list.lpush(values);
    }

    public int rpush(ByteArrayKey key, List<byte[]> values) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(values, "Values cannot be null");
        RedisObject.ListObj list = getOrCreate(key, RedisObject.ListObj.class, RedisObject.ListObj::new);
        return list.rpush(values);
    }

    public byte[] lpop(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.ListObj list = getExisting(key, RedisObject.ListObj.class);
        if (list == null) {
            return null;
        }
        byte[] popped = list.lpop();
        evictIfEmpty(key, list);
        return popped;
    }

    public byte[] rpop(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.ListObj list = getExisting(key, RedisObject.ListObj.class);
        if (list == null) {
            return null;
        }
        byte[] popped = list.rpop();
        evictIfEmpty(key, list);
        return popped;
    }

    public int llen(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.ListObj list = getExisting(key, RedisObject.ListObj.class);
        return list != null ? list.len() : 0;
    }

    public byte[] lindex(ByteArrayKey key, int index) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.ListObj list = getExisting(key, RedisObject.ListObj.class);
        return list != null ? list.lindex(index) : null;
    }

    public List<byte[]> lrange(ByteArrayKey key, int start, int stop) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.ListObj list = getExisting(key, RedisObject.ListObj.class);
        return list != null ? list.lrange(start, stop) : Collections.emptyList();
    }

    public int sadd(ByteArrayKey key, List<ByteArrayKey> members) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(members, "Members cannot be null");
        RedisObject.SetObj set = getOrCreate(key, RedisObject.SetObj.class, RedisObject.SetObj::new);
        return set.sadd(members);
    }

    public int srem(ByteArrayKey key, List<ByteArrayKey> members) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(members, "Members cannot be null");
        RedisObject.SetObj set = getExisting(key, RedisObject.SetObj.class);
        if (set == null) {
            return 0;
        }
        int removed = set.srem(members);
        evictIfEmpty(key, set);
        return removed;
    }

    public boolean sismember(ByteArrayKey key, ByteArrayKey member) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(member, "Member cannot be null");
        RedisObject.SetObj set = getExisting(key, RedisObject.SetObj.class);
        return set != null && set.sismember(member);
    }

    public Set<ByteArrayKey> smembers(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.SetObj set = getExisting(key, RedisObject.SetObj.class);
        return set != null ? set.smembers() : Collections.emptySet();
    }

    public int scard(ByteArrayKey key) {
        Objects.requireNonNull(key, "Key cannot be null");
        RedisObject.SetObj set = getExisting(key, RedisObject.SetObj.class);
        return set != null ? set.scard() : 0;
    }

    @SuppressWarnings("unchecked")
    private <T extends RedisObject> T getOrCreate(ByteArrayKey key, Class<T> expectedClass, Supplier<T> factory) {
        isExpired(key);
        RedisObject obj = store.compute(key, (k, existing) -> {
            if (existing == null) {
                return factory.get();
            }
            if (!expectedClass.isInstance(existing)) {
                throw new WrongTypeException();
            }
            return existing;
        });
        return (T) obj;
    }

    @SuppressWarnings("unchecked")
    private <T extends RedisObject> T getExisting(ByteArrayKey key, Class<T> expectedClass) {
        if (isExpired(key)) {
            return null;
        }
        RedisObject obj = store.get(key);
        if (obj == null) {
            return null;
        }
        if (!expectedClass.isInstance(obj)) {
            throw new WrongTypeException();
        }
        return (T) obj;
    }

    private void evictIfEmpty(ByteArrayKey key, RedisObject obj) {
        if (obj != null && obj.isEmpty()) {
            store.computeIfPresent(key, (k, existing) -> {
                if (existing.isEmpty()) {
                    expirations.remove(key);
                    return null;
                }
                return existing;
            });
        }
    }
}

