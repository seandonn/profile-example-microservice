package com.example.profile.service;

import java.util.WeakHashMap;

public class WriteLockCache {
  protected WeakHashMap<String, Object> locks;

  public WriteLockCache() {
    locks = new WeakHashMap<>();
  }

  public synchronized Object acquire(String key) {
    if (!locks.containsKey(key))
      locks.put(key, new Object());
    return locks.get(key);
  }

  public synchronized boolean contains(String key) {
    return locks.containsKey(key);
  }
}
