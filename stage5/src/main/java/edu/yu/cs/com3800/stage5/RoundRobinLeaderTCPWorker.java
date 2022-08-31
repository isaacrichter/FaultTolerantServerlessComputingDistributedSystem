package edu.yu.cs.com3800.stage5;

import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Util;
import edu.yu.cs.com3800.LoggingServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * This class is works hand in hand with the round robin leader. it:
 * 1) Takes the work that needs to be done along with the socket connection with the GatewayServer
 * 2) Sends the work to the worker the round robin is up to and gets the results
 * 3) Sends the results back to the GatewayServer RENAME THIS CLASS
 */
public class RoundRobinLeaderTCPWorker extends Thread implements LoggingServer {

    private Message workOrder;
    private Socket gatewayReturnSocket;
    private ZooKeeperPeerServerImpl server;
    Logger logger;

    public RoundRobinLeaderTCPWorker(Message workOrder, Socket gatewayReturnSocket, Long serverHostID, ZooKeeperPeerServerImpl server){
        try {
            logger = initializeLogging(serverHostID, "RoundRobinLeaderTCPWorker");
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.gatewayReturnSocket = gatewayReturnSocket;
        this.workOrder = workOrder;
        this.server = server;
        logger.fine("initialized at " + System.currentTimeMillis());
    }

    @Override
    public void run(){
        // Make socket to connect with worker
        try {
            logger.info("sending work #" + workOrder.getRequestID() +" to worker on port " + workOrder.getReceiverPort());
            Socket sendToWorkerSocket = new Socket(workOrder.getReceiverHost(), workOrder.getReceiverPort()); //sends it to corresponding worker
            OutputStream sendStream = sendToWorkerSocket.getOutputStream();
            sendStream.write(workOrder.getNetworkPayload());

            logger.info("getting results from worker on port " + workOrder.getReceiverPort());

            if(!server.isPeerDead(new InetSocketAddress(workOrder.getReceiverHost(), workOrder.getReceiverPort()))){
                // if the worker is still alive, response is good, and send back to gateway
                byte[] responseBytes = Util.readAllBytesFromNetwork(sendToWorkerSocket.getInputStream());

                logger.info("sending results of request #" + workOrder.getRequestID() + " back to gateway");
                OutputStream gatewaySendBackSocket = gatewayReturnSocket.getOutputStream();
                gatewaySendBackSocket.write(responseBytes);
            }else{
                // the worker is dead, so get next live peer and reassign it here
                while (server.isPeerDead(new InetSocketAddress(workOrder.getReceiverHost(), workOrder.getReceiverPort()))) {
                    sendToWorkerSocket.close(); // close old socket
                    sendStream.close();

                    Long nextLiveServer = -1L;
                    if (server.getRoundRobinLeader() != null) { // to prevent NPEs
                        nextLiveServer = server.getRoundRobinLeader().getNextLiveServer();
                    }
                    //need to write the work
                    workOrder.setReceiverPort(server.getPeerByID(nextLiveServer).getPort());
                    workOrder.setReceiverHost(server.getPeerByID(nextLiveServer).getHostName());

                    logger.info("Peer died while doing work. Resending work #" + workOrder.getRequestID() +" to worker on port " + workOrder.getReceiverPort());
                    sendToWorkerSocket = new Socket(workOrder.getReceiverHost(), workOrder.getReceiverPort()); //sends it to corresponding worker
                    sendStream = sendToWorkerSocket.getOutputStream();
                    sendStream.write(workOrder.getNetworkPayload());
                    logger.info("getting results from worker on port " + workOrder.getReceiverPort());
                }

                byte[] responseBytes = Util.readAllBytesFromNetwork(sendToWorkerSocket.getInputStream());

                logger.info("sending results of request #" + workOrder.getRequestID() + " back to gateway");
                OutputStream gatewaySendBackSocket = gatewayReturnSocket.getOutputStream();
                gatewaySendBackSocket.write(responseBytes);
            }

            sendToWorkerSocket.close();
            gatewayReturnSocket.close();

        } catch (IOException e) {
            logger.warning(e.getMessage());
        }
    }

}
