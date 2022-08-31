package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    Logger logger;

    public RoundRobinLeaderTCPWorker(Message workOrder, Socket gatewayReturnSocket, Long serverHostID){
        try {
            logger = initializeLogging(serverHostID, "RoundRobinLeaderTCPWorker");
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.gatewayReturnSocket = gatewayReturnSocket;
        this.workOrder = workOrder;
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
            byte[] responseBytes = Util.readAllBytesFromNetwork(sendToWorkerSocket.getInputStream());

            logger.info("sending results of request #" + workOrder.getRequestID() + " back to gateway");
            OutputStream gatewaySendBackSocket =  gatewayReturnSocket.getOutputStream();
            gatewaySendBackSocket.write(responseBytes);
            sendToWorkerSocket.close();
            gatewayReturnSocket.close();

        } catch (IOException e) {
            logger.warning(e.getMessage());
        }
    }

}
