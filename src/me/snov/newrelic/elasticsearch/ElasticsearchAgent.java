package me.snov.newrelic.elasticsearch;

import com.newrelic.metrics.publish.Agent;
import com.newrelic.metrics.publish.configuration.ConfigurationException;
import com.newrelic.metrics.publish.util.Logger;

import me.snov.newrelic.elasticsearch.response.ClusterStats;
import me.snov.newrelic.elasticsearch.response.NodesStats;
import me.snov.newrelic.elasticsearch.service.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

/**
 * Agent for Elasticsearch cluster
 */
public class ElasticsearchAgent extends Agent {

    private static final String GUID = "me.snov.newrelic-elasticsearch";
    private static final String VERSION = "0.3.0";

    private final String clusterName;
    private final ClusterStatsParser clusterStatsParser;
    private final ClusterStatsService clusterStatsService;
    private final NodesStatsService nodesStatsService;
    private final NodesStatsParser nodesStatsParser;
    private final EpochCounterFactory processorFactory;
    private final Logger logger;

    /**
     * Constructor for Elastisearch Agent
     */
    public ElasticsearchAgent(String host, Integer port) throws ConfigurationException {
        super(GUID, VERSION);
        try {
            logger = Logger.getLogger(ElasticsearchAgent.class);
            clusterStatsParser = new ClusterStatsParser(host, port);
            nodesStatsParser = new NodesStatsParser(host, port);
            processorFactory = new EpochCounterFactory();
            clusterStatsService = new ClusterStatsService();
            nodesStatsService = new NodesStatsService();
            clusterName = getClusterName();
        } catch (MalformedURLException e) {
            throw new ConfigurationException("URL could not be parsed", e);
        } catch (IOException e) {
            throw new ConfigurationException(
                String.format("Can't connect to elasticsearch at %s:%d", host, port),
                e
            );
        }
    }

    private String getClusterName() throws IOException {
        return clusterStatsParser.request().cluster_name;
    }

    @Override
    public String getComponentHumanLabel() {
        return clusterName;
    }

    @Override
    public void pollCycle() {
        try {
            reportClusterStats(clusterStatsParser.request());
            reportNodesStats(nodesStatsParser.request());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private void reportClusterStats(ClusterStats clusterStats) {

        /******************* Cluster *******************/

        // Component/V1/ClusterStats/Indices/Docs/*
        reportMetric("V1/ClusterStats/Indices/Docs/Count", "documents", clusterStats.indices.docs.count);
        reportMetric("V1/ClusterStats/Indices/Docs/Deleted", "documents", clusterStats.indices.docs.deleted);

        // Component/V1/ClusterStats/Indices/DocsAdded
        reportProcessedMetric("V1/ClusterStats/Indices/DocsAdded", "documents/second",
            clusterStats.indices.docs.count);

        // Nodes (table)
        // Component/V1/ClusterStats/Nodes/Count/*
        reportMetric("V1/ClusterStats/Nodes/Count/Total", "nodes", clusterStats.nodes.count.total);
        reportMetric("V1/ClusterStats/Nodes/Count/Master and data", "nodes", clusterStats.nodes.count.master_data);
        reportMetric("V1/ClusterStats/Nodes/Count/Master only", "nodes", clusterStats.nodes.count.master_only);
        reportMetric("V1/ClusterStats/Nodes/Count/Data only", "nodes", clusterStats.nodes.count.data_only);
        reportMetric("V1/ClusterStats/Nodes/Count/Client", "nodes", clusterStats.nodes.count.client);

        // Indices and Shards (table)
        // Component/V1/ClusterStats/Indices/*
        reportMetric("V1/ClusterStats/Indices/Indices", "indices", clusterStats.indices.count);
        reportMetric("V1/ClusterStats/Indices/Shards", "shards", clusterStats.indices.shards.total);
        reportMetric("V1/ClusterStats/Indices/Primaries", "shards", clusterStats.indices.shards.total);
        reportMetric("V1/ClusterStats/Indices/Replication", "shards", clusterStats.indices.shards.replication);

        // ClusterStats/Indices/Segments/Count
        reportMetric("V1/ClusterStats/Indices/Segments/Count", "segments", clusterStats.indices.segments.count);

        // Component/V1/ClusterStats/Indices/Store/Size
        reportMetric("V1/ClusterStats/Indices/Store/Size", "bytes", clusterStats.indices.store.size_in_bytes);

        // Component/V1/ClusterStats/Indices/Store/SizePerSec
        reportProcessedMetric("V1/ClusterStats/Indices/Store/SizePerSec", "bytes/second",
            clusterStats.indices.store.size_in_bytes);

        // Component/V1/ClusterStats/Indices/Store/ThrottleTime
        reportProcessedMetric("V1/ClusterStats/Indices/Store/ThrottleTime", "millis",
            clusterStats.indices.store.throttle_time_in_millis);

        /******************* Summary *******************/

        // Component/V1/ClusterStats/Nodes/VersionMismatch
        reportMetric("V1/ClusterStats/NumberOfVersionsInCluster", "versions",
            clusterStatsService.getNumberOfVersionsInCluster(clusterStats));
    }

    private void reportNodeStats(NodesStats.NodeStats nodeStats) {
        String nodeName = nodeStats.name;

        /******************* Nodes *******************/

        // Documents
        // Component/V1/NodeStats/Nodes/Indices/Docs/Count/*
        reportNodeMetric("V1/NodeStats/Nodes/Indices/Docs/Count", "documents", nodeName,
            nodeStats.indices.docs.count);

        // Store size
        // Component/V1/NodeStats/Indices/Store/Size/*
        reportNodeMetric("V1/NodeStats/Indices/Store/Size", "bytes", nodeName,
            nodeStats.indices.store.size_in_bytes);

        // Store writes
        // Component/V1/NodeStats/Indices/Store/SizePerSec/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Store/SizePerSec", "bytes/second", nodeName,
            nodeStats.indices.store.size_in_bytes);

        // Deleted documents
        // Component/V1/NodeStats/Nodes/Indices/Docs/Deleted/*
        reportNodeMetric("V1/NodeStats/Nodes/Indices/Docs/Deleted", "documents", nodeName,
            nodeStats.indices.docs.deleted);

        /******************* Indexing *******************/

        // Index
        // Component/V1/NodeStats/Indices/Indexing/Index/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Indexing/Index", "queries", nodeName,
            nodeStats.indices.indexing.index_total);

        // Index time
        // Component/V1/NodeStats/Indices/Indexing/IndexTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Indexing/IndexTimeInMillis", "ms", nodeName,
            nodeStats.indices.indexing.index_time_in_millis);

        // Delete
        // Component/V1/NodeStats/Indices/Indexing/DeleteTotal/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Indexing/DeleteTotal", "queries", nodeName,
            nodeStats.indices.indexing.delete_total);

        // Delete time
        // Component/V1/NodeStats/Indices/Indexing/DeleteTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Indexing/DeleteTimeInMillis", "ms", nodeName,
            nodeStats.indices.indexing.delete_time_in_millis);

        // Refresh
        // Component/V1/NodeStats/Indices/Refresh/Total/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Refresh/Total", "refreshes", nodeName,
            nodeStats.indices.refresh.total);

        // Refresh time
        // Component/V1/NodeStats/Indices/Refresh/TotalTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Refresh/TotalTimeInMillis", "ms", nodeName,
            nodeStats.indices.refresh.total_time_in_millis);

        // Flush
        // Component/V1/NodeStats/Indices/Flush/Total/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Flush/Total", "flushes", nodeName,
            nodeStats.indices.flush.total);

        // Flush time
        // Component/V1/NodeStats/Indices/Flush/TotalTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Flush/TotalTimeInMillis", "ms", nodeName,
            nodeStats.indices.flush.total_time_in_millis);

        // Warmer
        // Component/V1/NodeStats/Indices/Warmer/Total/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Warmer/Total", "queries", nodeName,
            nodeStats.indices.warmer.total);

        // Warmer time
        // Component/V1/NodeStats/Indices/Warmer/TotalTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Warmer/TotalTimeInMillis", "ms", nodeName,
            nodeStats.indices.warmer.total_time_in_millis);


        /******************* Search *******************/

        // Query
        // Component/V1/NodeStats/Indices/Search/QueryTotal/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Search/QueryTotal", "requests", nodeName,
            nodeStats.indices.search.query_total);

        // Query time
        // Component/V1/NodeStats/Indices/Search/QueryTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Search/QueryTimeInMillis", "ms", nodeName,
            nodeStats.indices.search.query_time_in_millis);

        // Fetch
        // Component/V1/NodeStats/Indices/Search/FetchTotal/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Search/FetchTotal", "requests", nodeName,
            nodeStats.indices.search.fetch_total);

        // Fetch time
        // Component/V1/NodeStats/Indices/Search/FetchTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Search/FetchTimeInMillis", "ms", nodeName,
            nodeStats.indices.search.fetch_time_in_millis);

        // Get
        // Component/V1/NodeStats/Indices/Get/Total/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Get/Total", "requests", nodeName,
            nodeStats.indices.get.total);

        // Get time
        // Component/V1/NodeStats/Indices/Get/TimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Get/TimeInMillis", "ms", nodeName,
            nodeStats.indices.get.time_in_millis);

        // Suggest
        // Component/V1/NodeStats/Indices/Suggest/Total/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Suggest/Total", "requests", nodeName,
            nodeStats.indices.suggest.total);

        // Suggest
        // Component/V1/NodeStats/Indices/Suggest/TimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Suggest/TimeInMillis", "ms", nodeName,
            nodeStats.indices.suggest.time_in_millis);


        /******************* Merges *******************/

        // Merges
        // Component/V1/NodeStats/Indices/Merges/Total/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Merges/Total", "merges", nodeName,
            nodeStats.indices.merges.total);

        // Size
        // Component/V1/NodeStats/Indices/Merges/TotalSizeInBytes/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Merges/TotalSizeInBytes", "bytes/second", nodeName,
            nodeStats.indices.merges.total_size_in_bytes);

        // Time
        // Component/V1/NodeStats/Indices/Merges/TotalTimeInMillis/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Merges/TotalTimeInMillis", "ms", nodeName,
            nodeStats.indices.merges.total_time_in_millis);

        // Docs
        // Component/V1/NodeStats/Indices/Merges/TotalDocs/*
        reportNodeProcessedMetric("V1/NodeStats/Indices/Merges/TotalDocs", "docs", nodeName,
            nodeStats.indices.merges.total_docs);

        // Segments
        // Component/V1/NodeStats/Indices/Segments/Count/*
        reportNodeMetric("V1/NodeStats/Indices/Segments/Count", "segments", nodeName,
            nodeStats.indices.segments.count);


        /******************* Cache *******************/

        // Filter cache
        // Component/V1/NodeStats/Indices/FilterCache/Size/*
        reportNodeMetric("V1/NodeStats/Indices/FilterCache/Size", "bytes", nodeName,
            nodeStats.indices.filter_cache.memory_size_in_bytes);

        // Filter evictions
        // Component/V1/NodeStats/Indices/FilterCache/Evictions/*
        reportNodeMetric("V1/NodeStats/Indices/FilterCache/Evictions", "evictions", nodeName,
            nodeStats.indices.filter_cache.evictions);

        // Field data
        // Component/V1/NodeStats/Indices/Fielddata/Size/*
        reportNodeMetric("V1/NodeStats/Indices/Fielddata/Size", "bytes", nodeName,
            nodeStats.indices.fielddata.memory_size_in_bytes);

        // Field evictions
        // Component/V1/NodeStats/Indices/Fielddata/Evictions/*
        reportNodeMetric("V1/NodeStats/Indices/Fielddata/Evictions", "evictions", nodeName,
            nodeStats.indices.fielddata.evictions);

        // Id cache
        // Component/V1/NodeStats/Indices/IdCache/Size/*
        reportNodeMetric("V1/NodeStats/Indices/IdCache/Size", "bytes", nodeName,
            nodeStats.indices.id_cache.memory_size_in_bytes);

        // Completion
        // Component/V1/NodeStats/Indices/Completion/Size/*
        reportNodeMetric("V1/NodeStats/Indices/Completion/Size", "bytes", nodeName,
            nodeStats.indices.completion.size_in_bytes);

        /******************* JVM & System *******************/

        // Heap used, %
        // Component/V1/NodeStats/Jvm/Mem/HeapUsedPercent/*
        reportNodeMetric("V1/NodeStats/Jvm/Mem/HeapUsedPercent", "percent", nodeName,
            nodeStats.jvm.mem.heap_used_percent);

        // CPU used, %
        // Component/V1/NodeStats/Process/Cpu/Percent/*
        reportNodeMetric("V1/NodeStats/Process/Cpu/Percent", "percent", nodeName,
            nodeStats.process.cpu.percent);

        // Load average
        // Component/V1/NodeStats/Os/LoadAverage/*
        if (nodeStats.os.load_average != null && nodeStats.os.load_average.size() > 0) {
            reportNodeMetric("V1/NodeStats/Os/LoadAverage", "units", nodeName,
                nodeStats.os.load_average.get(0));
        }

        // GC collections (old)
        // Component/V1/NodeStats/Jvm/Gc/Old/CollectionCount/*
        reportNodeProcessedMetric("NodeStats/Jvm/Gc/Old/CollectionCount", "collections", nodeName,
            nodeStats.jvm.gc.collectors.old.collection_count);

        // GC collection time (old)
        // Component/V1/NodeStats/Jvm/Gc/Old/CollectionTime/*
        reportNodeProcessedMetric("NodeStats/Jvm/Gc/Old/CollectionTime", "milliseconds", nodeName,
            nodeStats.jvm.gc.collectors.old.collection_time_in_millis);

        // GC collections (young)
        // Component/V1/NodeStats/Jvm/Gc/Young/CollectionCount/*
        reportNodeProcessedMetric("NodeStats/Jvm/Gc/Young/CollectionCount", "collections", nodeName,
            nodeStats.jvm.gc.collectors.young.collection_count);

        // GC collection time (young)
        // Component/V1/NodeStats/Jvm/Gc/Young/CollectionTime/*
        reportNodeProcessedMetric("NodeStats/Jvm/Gc/Young/CollectionTime", "milliseconds", nodeName,
            nodeStats.jvm.gc.collectors.young.collection_time_in_millis);

        // Swap usage, %
        // Component/V1/NodeStats/Os/Swap/Percent/*
        Long swap_used = 0l;
        if (nodeStats.os.swap.used_in_bytes != null && nodeStats.os.swap.free_in_bytes != null) {
            Long swap_total = nodeStats.os.swap.used_in_bytes + nodeStats.os.swap.free_in_bytes;
            swap_used = nodeStats.os.swap.used_in_bytes / swap_total;
        }
        reportNodeMetric("V1/NodeStats/Os/Swap/Percent", "percent", nodeName, swap_used);


        /******************* I/O *******************/

        // Disk reads
        // Component/V1/NodeStats/Fs/Total/DiskReadSizeInBytes/*
        reportNodeProcessedMetric("NodeStats/Fs/Total/DiskReadSizeInBytes", "bytes", nodeName,
            nodeStats.fs.total.disk_read_size_in_bytes);

        // Disk writes
        // Component/V1/NodeStats/Fs/Total/DiskWriteSizeInBytes/*
        reportNodeProcessedMetric("NodeStats/Fs/Total/DiskWriteSizeInBytes", "bytes", nodeName,
            nodeStats.fs.total.disk_write_size_in_bytes);

        // Open file descriptors
        // Component/V1/NodeStats/Process/OpenFileDescriptors/*
        reportNodeMetric("V1/NodeStats/Process/OpenFileDescriptors", "descriptors", nodeName,
            nodeStats.process.open_file_descriptors);

        // Store throttle time
        // Component/V1/NodeStats/Indices/Store/ThrottleTimeInMillis/*
        reportNodeProcessedMetric("NodeStats/Indices/Store/ThrottleTimeInMillis", "ms", nodeName,
            nodeStats.indices.store.throttle_time_in_millis);


        /******************* Network *******************/

        // Transport connections
        // Component/V1/NodeStats/Transport/ServerOpen/*
        reportNodeProcessedMetric("NodeStats/Transport/ServerOpen", "bytes", nodeName,
            nodeStats.transport.server_open);

        // Client connections
        // Component/V1/NodeStats/Http/TotalOpened/*
        reportNodeProcessedMetric("NodeStats/Http/TotalOpened", "connections", nodeName,
            nodeStats.http.total_opened);

        // Transmit
        // Component/V1/NodeStats/Transport.TxSizeInBytes/*
        reportNodeProcessedMetric("NodeStats/Transport/TxSizeInBytes", "bytes", nodeName,
            nodeStats.transport.tx_size_in_bytes);

        // Receive
        // Component/V1/NodeStats/Transport.RxSizeInBytes/*
        reportNodeProcessedMetric("NodeStats/Transport/RxSizeInBytes", "bytes", nodeName,
            nodeStats.transport.rx_size_in_bytes);


        /******************* Thread pool *******************/

        // Search
        // Component/V1/NodeStats/ThreadPool/Search/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Search/Completed", "threads", nodeName,
            nodeStats.thread_pool.search.completed);

        // Search queue
        // Component/V1/NodeStats/ThreadPool/Search/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Search/Queue", "threads", nodeName,
            nodeStats.thread_pool.search.queue);

        // Index
        // Component/V1/NodeStats/ThreadPool/Index/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Index/Completed", "threads", nodeName,
            nodeStats.thread_pool.index.completed);

        // Index queue
        // Component/V1/NodeStats/ThreadPool/Index/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Index/Queue", "threads", nodeName,
            nodeStats.thread_pool.index.queue);

        // Bulk
        // Component/V1/NodeStats/ThreadPool/Bulk/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Bulk/Completed", "threads", nodeName,
            nodeStats.thread_pool.bulk.completed);

        // Bulk queue
        // Component/V1/NodeStats/ThreadPool/Bulk/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Bulk/Queue", "threads", nodeName,
            nodeStats.thread_pool.bulk.queue);

        // Get
        // Component/V1/NodeStats/ThreadPool/Get/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Get/Completed", "threads", nodeName,
            nodeStats.thread_pool.get.completed);

        // Get queue
        // Component/V1/NodeStats/ThreadPool/Get/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Get/Queue", "threads", nodeName,
            nodeStats.thread_pool.get.queue);

        // Merge
        // Component/V1/NodeStats/ThreadPool/Merge/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Merge/Completed", "threads", nodeName,
            nodeStats.thread_pool.merge.completed);

        // Merge queue
        // Component/V1/NodeStats/ThreadPool/Merge/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Merge/Queue", "threads", nodeName,
            nodeStats.thread_pool.merge.queue);

        // Suggest
        // Component/V1/NodeStats/ThreadPool/Suggest/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Suggest/Completed", "threads", nodeName,
            nodeStats.thread_pool.suggest.completed);

        // Suggest queue
        // Component/V1/NodeStats/ThreadPool/Suggest/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Suggest/Queue", "threads", nodeName,
            nodeStats.thread_pool.suggest.queue);

        // Warmer
        // Component/V1/NodeStats/ThreadPool/Warmer/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Warmer/Completed", "threads", nodeName,
            nodeStats.thread_pool.warmer.completed);

        // Warmer queue
        // Component/V1/NodeStats/ThreadPool/Warmer/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Warmer/Queue", "threads", nodeName,
            nodeStats.thread_pool.warmer.queue);

        // Flush
        // Component/V1/NodeStats/ThreadPool/Flush/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Flush/Completed", "threads", nodeName,
            nodeStats.thread_pool.flush.completed);

        // Flush queue
        // Component/V1/NodeStats/ThreadPool/Flush/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Flush/Queue", "threads", nodeName,
            nodeStats.thread_pool.flush.queue);

        // Refresh
        // Component/V1/NodeStats/ThreadPool/Refresh/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Refresh/Completed", "threads", nodeName,
            nodeStats.thread_pool.refresh.completed);

        // Refresh queue
        // Component/V1/NodeStats/ThreadPool/Refresh/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Refresh/Queue", "threads", nodeName,
            nodeStats.thread_pool.refresh.queue);

        // Generic
        // Component/V1/NodeStats/ThreadPool/Generic/Completed/*
        reportNodeProcessedMetric("NodeStats/ThreadPool/Generic/Completed", "threads", nodeName,
            nodeStats.thread_pool.generic.completed);

        // Generic queue
        // Component/V1/NodeStats/ThreadPool/Generic/Queue/*
        reportNodeMetric("V1/NodeStats/ThreadPool/Generic/Queue", "threads", nodeName,
            nodeStats.thread_pool.generic.queue);
    }

    private void reportCalculatedClusterStats(NodesStats nodesStats) {
        /******************* Queries stats *******************/

        NodesStatsService.QueriesStat queriesStat = nodesStatsService.getTotalNumberOfQueries(nodesStats);

        // Component/V1/QueriesStats/*
        reportMetric("V1/QueriesStats/Search", "queries", queriesStat.search);
        reportMetric("V1/QueriesStats/Fetch", "queries", queriesStat.fetch);
        reportMetric("V1/QueriesStats/Get", "queries", queriesStat.get);
        reportMetric("V1/QueriesStats/Index", "queries", queriesStat.index);
        reportMetric("V1/QueriesStats/Delete", "queries", queriesStat.delete);
    }

    private void reportNodesStats(NodesStats nodesStats) {
        if (nodesStats.nodes != null) {
            reportCalculatedClusterStats(nodesStats);
            for (Map.Entry<String, NodesStats.NodeStats> entry : nodesStats.nodes.entrySet()) {
                reportNodeStats(entry.getValue());
            }
        }
    }

    private String nodeMetricName(String metricName, String nodeName) {
        return metricName + "/" + nodeName;
    }

    private void reportNodeMetric(String metricName, String units, String nodeName, Number value)
    {
        reportMetric(nodeMetricName(metricName, nodeName), units, value);
    }

    private void reportNodeProcessedMetric(String metricName, String units, String nodeName, Number value)
    {
        Number processedValue = processorFactory.getProcessorForNode(metricName, nodeName).process(value);
        reportNodeMetric(metricName, units, nodeName, processedValue);
    }

    private void reportProcessedMetric(String metricName, String units, Number value)
    {
        Number processedValue = processorFactory.getProcessor(metricName).process(value);
        reportMetric(metricName, units, processedValue);
    }
}