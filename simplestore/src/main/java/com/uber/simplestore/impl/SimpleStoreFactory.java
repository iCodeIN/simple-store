/*
 * Copyright (C) 2019. Uber Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.uber.simplestore.impl;

import android.content.Context;
import com.google.common.annotations.VisibleForTesting;
import com.uber.simplestore.ScopeConfig;
import com.uber.simplestore.SimpleStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;

/** Obtain an instance of a storage scope. Only one instance per scope may exist at any time. */
public final class SimpleStoreFactory {

  private static final Object scopesLock = new Object();

  @GuardedBy("scopesLock")
  private static Map<String, SimpleStoreImpl> scopes = new HashMap<>();

  /**
   * Obtain a store for a scope with default configuration.
   *
   * @param context to store in
   * @param scope slash-delimited scope
   * @return open store
   */
  public static SimpleStore create(Context context, String scope) {
    return create(context, scope, ScopeConfig.DEFAULT);
  }

  /**
   * Obtain a store for a scope.
   *
   * @param context to store in
   * @param scope slash-delimited scope
   * @param config to use
   * @return open store
   */
  public static SimpleStore create(Context context, String scope, ScopeConfig config) {
    Context appContext = context.getApplicationContext();
    SimpleStoreImpl store;
    synchronized (scopesLock) {
      if (scopes.containsKey(scope)) {
        store = scopes.get(scope);
        if (!Objects.requireNonNull(store).openIfClosed()) {
          // Never let two references be issued.
          throw new IllegalStateException("scope '" + scope + "' already open");
        }
      } else {
        store = new SimpleStoreImpl(appContext, scope, config);
        scopes.put(scope, store);
      }
    }
    return store;
  }

  static void tombstone(SimpleStoreImpl store) {
    synchronized (scopesLock) {
      if (store.tombstone()) {
        scopes.remove(store.getScope());
      }
    }
  }

  @VisibleForTesting
  public static void crashIfAnyOpen() {
    synchronized (scopesLock) {
      for (Map.Entry<String, SimpleStoreImpl> e : scopes.entrySet()) {
        if (e.getValue().available.get() == 0) {
          throw new IllegalStateException("Leaked scope " + e.getKey());
        }
      }
    }
  }
}
