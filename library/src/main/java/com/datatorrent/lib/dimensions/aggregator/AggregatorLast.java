/*
 * Copyright (c) 2015 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datatorrent.lib.dimensions.aggregator;

import com.datatorrent.lib.appdata.gpo.GPOUtils;
import com.datatorrent.lib.dimensions.Aggregate.InputEvent;
import com.datatorrent.lib.appdata.schemas.Type;
import com.datatorrent.lib.dimensions.Aggregate;

/**
 * <p>
 * This aggregator creates an aggregate out of the last {@link InputEvent} encountered by this aggregator. All previous
 * {@link InputEvent}s are ignored.
 * </p>
 * <p>
 * <b>Note:</b> when aggregates are combined in a unifier it is not possible to tell which came first or last, so
 * one is picked arbitrarily to be the last.
 * </p>
 */
public class AggregatorLast<EVENT> extends AbstractIncrementalAggregator<EVENT>
{
  private static final long serialVersionUID = 20154301647L;

  /**
   * The singleton instance of this class.
   */
  public static final AggregatorLast INSTANCE = new AggregatorLast();

  /**
   * Singleton constructor.
   */
  private AggregatorLast()
  {
    //Do nothing
  }

  @Override
  public Type getOutputType(Type inputType)
  {
    return AggregatorUtils.IDENTITY_TYPE_MAP.get(inputType);
  }

  @Override
  public void aggregate(Aggregate dest, EVENT src)
  {
    GPOUtils.copyPOJOToGPO(dest.getAggregates(), this.getValueGetters(), src);
  }

  @Override
  public void aggregate(Aggregate dest, Aggregate src)
  {
    GPOUtils.
  }
}
