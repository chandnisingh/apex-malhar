/**
 * Copyright (c) 2015 DataTorrent, Inc.
 * All rights reserved.
 */
package com.datatorrent.contrib.dimensions;

import com.datatorrent.api.AppData;
import com.datatorrent.api.AppData.EmbeddableQuery;
import com.datatorrent.api.Context;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.api.annotation.InputPortFieldAnnotation;
import com.datatorrent.lib.appdata.query.QueryExecutor;
import com.datatorrent.lib.appdata.query.QueryManagerAsynchronous;
import com.datatorrent.lib.appdata.query.SimpleQueueManager;
import com.datatorrent.lib.appdata.query.serde.MessageDeserializerFactory;
import com.datatorrent.lib.appdata.query.serde.MessageSerializerFactory;
import com.datatorrent.lib.appdata.schemas.DataQueryDimensional;
import com.datatorrent.lib.appdata.schemas.Message;
import com.datatorrent.lib.appdata.schemas.Result;
import com.datatorrent.lib.appdata.schemas.ResultFormatter;
import com.datatorrent.lib.appdata.schemas.SchemaQuery;
import com.datatorrent.lib.appdata.schemas.SchemaRegistry;
import com.datatorrent.lib.appdata.schemas.SchemaResult;
import com.datatorrent.lib.dimensions.aggregator.AggregatorRegistry;
import com.datatorrent.lib.dimensions.aggregator.IncrementalAggregator;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.mutable.MutableLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a base class for App Data enabled Dimensions Stores. This class holds all the template code required
 * for processing AppData queries.
 */
public abstract class AbstractAppDataDimensionStoreHDHT extends DimensionsStoreHDHT
{
  /**
   * This is the result formatter used to format data sent as a result to an App Data query.
   */
  @NotNull
  protected ResultFormatter resultFormatter = new ResultFormatter();
  /**
   * This is the {@link AggregatorRegistry} which holds the mapping from aggregator names and aggregator ids to
   * aggregators.
   */
  @NotNull
  protected AggregatorRegistry aggregatorRegistry = AggregatorRegistry.DEFAULT_AGGREGATOR_REGISTRY;
  /**
   * This is the queue manager for schema queries.
   */
  protected transient SimpleQueueManager<SchemaQuery, Void, Void> schemaQueueManager;
  /**
   * This is the queue manager for data queries.
   */
  protected transient DimensionsQueueManager dimensionsQueueManager;
  /**
   * This is the query manager for schema queries.
   */
  protected transient QueryManagerAsynchronous<SchemaQuery, Void, Void, SchemaResult> schemaProcessor;
  /**
   * This is the query manager for data queries.
   */
  protected transient QueryManagerAsynchronous<DataQueryDimensional, QueryMeta, MutableLong, Result> queryProcessor;
  /**
   * This is the factory used to deserializes queries.
   */
  protected transient MessageDeserializerFactory queryDeserializerFactory;
  /**
   * This is the schema registry that holds all the schema information for the operator.
   */
  @VisibleForTesting
  public SchemaRegistry schemaRegistry;
  /**
   * This is the factory used to serialize results.
   */
  protected transient MessageSerializerFactory resultSerializerFactory;
  /**
   * This is the output port that serialized query results are emitted from.
   */
  @AppData.ResultPort
  public final transient DefaultOutputPort<String> queryResult = new DefaultOutputPort<String>();
  /**
   * This is the input port from which queries are recieved.
   */
  @InputPortFieldAnnotation(optional = true)
  @AppData.QueryPort
  public transient final DefaultInputPort<String> query = new DefaultInputPort<String>()
  {
    @Override
    public void process(String s)
    {
      LOG.debug("Received {}", s);

      //Deserialize a query
      Message query;
      try {
        query = queryDeserializerFactory.deserialize(s);
      }
      catch (IOException ex) {
        LOG.error("error parsing query {}", s, ex);
        return;
      }

      if (query instanceof SchemaQuery) {
        //If the query is a {@link SchemaQuery} add it to the schemaQuery queue.
        schemaQueueManager.enqueue((SchemaQuery) query, null, null);
      }
      else if (query instanceof DataQueryDimensional) {
        //If the query is a {@link DataQueryDimensional} add it to the dataQuery queue.
        dimensionsQueueManager.enqueue((DataQueryDimensional) query, null, null);
      }
      else {
        LOG.error("Invalid query {}", s);
      }
    }
  };

  private EmbeddableQuery<String> embeddableQuery;

  /**
   * Constructor to create operator.
   */
  @SuppressWarnings("unchecked")
  public AbstractAppDataDimensionStoreHDHT()
  {
    //Do nothing
  }

  @SuppressWarnings("unchecked")
  @Override
  public void setup(Context.OperatorContext context)
  {
    if(embeddableQuery != null) {
      //If an embeddableQuery operator is set, then enable it.
      embeddableQuery.setInputPort(query);
      embeddableQuery.setup(context);
    }

    aggregatorRegistry.setup();

    schemaRegistry = getSchemaRegistry();

    resultSerializerFactory = new MessageSerializerFactory(resultFormatter);

    queryDeserializerFactory = new MessageDeserializerFactory(SchemaQuery.class, DataQueryDimensional.class);
    queryDeserializerFactory.setContext(DataQueryDimensional.class, schemaRegistry);

    dimensionsQueueManager = new DimensionsQueueManager(this, schemaRegistry);
    queryProcessor =
    new QueryManagerAsynchronous<DataQueryDimensional, QueryMeta, MutableLong, Result>(queryResult,
                                                                                       dimensionsQueueManager,
                                                                                       new DimensionsQueryExecutor(this, schemaRegistry),
                                                                                       resultSerializerFactory);

    schemaQueueManager = new SimpleQueueManager<SchemaQuery, Void, Void>();
    schemaProcessor = new QueryManagerAsynchronous<SchemaQuery, Void, Void, SchemaResult>(queryResult,
                                                                                          schemaQueueManager,
                                                                                          new SchemaQueryExecutor(),
                                                                                          resultSerializerFactory);


    dimensionsQueueManager.setup(context);
    queryProcessor.setup(context);

    schemaQueueManager.setup(context);
    schemaProcessor.setup(context);
    super.setup(context);
  }

  @Override
  public void beginWindow(long windowId)
  {
    if(embeddableQuery != null) {
      //If embeddable query operator is set
      embeddableQuery.beginWindow(windowId);
    }

    schemaQueueManager.beginWindow(windowId);
    schemaProcessor.beginWindow(windowId);

    dimensionsQueueManager.beginWindow(windowId);
    queryProcessor.beginWindow(windowId);

    super.beginWindow(windowId);
  }

  @Override
  public void endWindow()
  {
    if(embeddableQuery != null) {
      //If embeddable query operator is set
      embeddableQuery.endWindow();
    }

    super.endWindow();

    queryProcessor.endWindow();
    dimensionsQueueManager.endWindow();

    schemaProcessor.endWindow();
    schemaQueueManager.endWindow();
  }

  @Override
  public void teardown()
  {
    if(embeddableQuery != null) {
      //If embeddable query operator is set
      embeddableQuery.teardown();
    }

    queryProcessor.teardown();
    dimensionsQueueManager.teardown();

    schemaProcessor.teardown();
    schemaQueueManager.teardown();

    super.teardown();
  }

  /**
   * Processes schema queries.
   * @param schemaQuery a schema query
   * @return The corresponding schema result.
   */
  protected abstract SchemaResult processSchemaQuery(SchemaQuery schemaQuery);

  /**
   * Gets the {@link SchemaRegistry} used by this operator.
   * @return The {@link SchemaRegistry} used by this operator.
   */
  protected abstract SchemaRegistry getSchemaRegistry();

  @Override
  public IncrementalAggregator getAggregator(int aggregatorID)
  {
    return aggregatorRegistry.getIncrementalAggregatorIDToAggregator().get(aggregatorID);
  }

  @Override
  protected int getAggregatorID(String aggregatorName)
  {
    return aggregatorRegistry.getIncrementalAggregatorNameToID().get(aggregatorName);
  }

  /**
   * Sets the {@link ResultFormatter} to use on App Data results emitted by this operator.
   * @param resultFormatter The {@link ResultFormatter} to use on App Data results emitted
   * by this operator.
   */
  public void setResultFormatter(ResultFormatter resultFormatter)
  {
    this.resultFormatter = resultFormatter;
  }

  /**
   * Returns the {@link ResultFormatter} to use on App Data results emitted by this operator.
   * @return The {@link ResultFormatter} to use on App Data results emitted by this operator.
   */
  public ResultFormatter getResultFormatter()
  {
    return resultFormatter;
  }

  /**
   * Returns the {@link AggregatorRegistry} used by this operator.
   * @return The {@link AggregatorRegistry} used by this operator.
   */
  public AggregatorRegistry getAggregatorRegistry()
  {
    return aggregatorRegistry;
  }

  /**
   * Sets the {@link AggregatorRegistry} used by this operator.
   * @param aggregatorRegistry The {@link AggregatorRegistry} used by this operator.
   */
  public void setAggregatorRegistry(@NotNull AggregatorRegistry aggregatorRegistry)
  {
    this.aggregatorRegistry = aggregatorRegistry;
  }

  /**
   * Returns the {@link EmbeddableQuery} operator set on this operator.
   * @return The {@link EmbeddableQuery} operator.
   */
  public EmbeddableQuery<String> getEmbeddableQuery()
  {
    return embeddableQuery;
  }

  /**
   * Sets the {@link EmbeddableQuery} operator on this operator.
   * @param embeddableQuery The {@link EmbeddableQuery} operator to set on this operator.
   */
  public void setEmbeddableQuery(EmbeddableQuery<String> embeddableQuery)
  {
    this.embeddableQuery = embeddableQuery;
  }

  /**
   * This is a {@link QueryExecutor} that is responsible for executing schema queries.
   */
  public class SchemaQueryExecutor implements QueryExecutor<SchemaQuery, Void, Void, SchemaResult>
  {
    /**
     * Creates a {@link SchemaQueryExecutor}
     */
    public SchemaQueryExecutor()
    {
      //Do nothing
    }

    @Override
    public SchemaResult executeQuery(SchemaQuery query, Void metaQuery, Void queueContext)
    {
      return processSchemaQuery(query);
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(AbstractAppDataDimensionStoreHDHT.class);
}
