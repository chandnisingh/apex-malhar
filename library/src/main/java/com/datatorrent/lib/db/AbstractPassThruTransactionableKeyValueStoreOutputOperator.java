/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.lib.db;

/**
 * This abstract class is intended for pass-through output adapter of any transactional key value store that wants the "transactional exactly once" feature.
 * "Pass-through" means it does not wait for end window to write to the store. It will begin transaction at begin window and write to the store as the tuples
 * come and commit the transaction at end window.
 *
 * @param <T> The tuple type
 * @param <S> The store type
 * @since 0.9.3
 */
public abstract class AbstractPassThruTransactionableKeyValueStoreOutputOperator<T, S extends TransactionableKeyValueStore>
        extends AbstractPassThruTransactionableStoreOutputOperator<T, S>
{
  @Override
  protected long getCommittedWindowId(String appId, int operatorId)
  {
    Object value = store.get(getCommittedWindowKey(appId, operatorId));
    return (value == null) ? -1 : Long.valueOf(value.toString());
  }

  @Override
  protected void storeCommittedWindowId(String appId, int operatorId, long windowId)
  {
    store.put(getCommittedWindowKey(appId, operatorId), windowId);
  }

  protected Object getCommittedWindowKey(String appId, int operatorId)
  {
    return "_dt_wid:" + appId + ":" + operatorId;
  }

}
