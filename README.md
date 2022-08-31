<h1>Fault Tolerant Serverless Computing Distributed System</h1>

<h2>Course Project - Distributed Systems, Fall 2021</h2> 

- Gateway node receives requests from clients via HTTP and sends to leader chosen using Zookeeper Election algorithm implementation.
- Leader delegates to workers over TCP by round robin, providing load balancing and serverless execution.
- Uses gossip style heartbeats over UDP to detect failed servers. Work of failed servers redelegated, and election reruns if leader fails.
- Extensive and organized logging for each class of each node. Logs from sample run included under stage5/logFiles
<p>(Note: Completed in 5 stages. See /stage5 for complete project)</p>