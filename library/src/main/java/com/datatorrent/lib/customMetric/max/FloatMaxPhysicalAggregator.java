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
package com.datatorrent.lib.customMetric.max;

import java.io.Serializable;

import com.datatorrent.api.CustomMetric;
import com.datatorrent.api.annotation.Name;

@Name("Max")
public class FloatMaxPhysicalAggregator implements CustomMetric.Aggregator<Float>, Serializable
{
  @Override
  public Float aggregate(CustomMetric.PhysicalMetrics physicalMetrics)
  {
    Float max = null;
    for (Number metric : physicalMetrics.<Number>getValues()) {
      float value = metric.floatValue();
      if (max == null || value > max) {
        max = value;
      }
    }
    return max;
  }

  private static final long serialVersionUID = 201504081327L;
}
