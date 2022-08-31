package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.JavaRunner;
import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.ZooKeeperPeerServer;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread implements LoggingServer {

    private ZooKeeperPeerServer myServer;
    private LinkedBlockingQueue<TCPListener.MessageAndSocket> incomingTCPMessagesAndSockets;
    private LinkedBlockingQueue<Message> outgoingMessages;
    private JavaRunner javaRunner;
    private Logger logger;
    //boolean shutdown = false;

    public JavaRunnerFollower(ZooKeeperPeerServer server, LinkedBlockingQueue<TCPListener.MessageAndSocket> incomingTCPMessagesAndSockets, LinkedBlockingQueue<Message> outgoingMessages){
        this.myServer = server;
        this.incomingTCPMessagesAndSockets = incomingTCPMessagesAndSockets;
        this.outgoingMessages = outgoingMessages;
        try {
            this.logger = initializeLogging(this.myServer.getServerId(), "JavaRunnerFollower");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try{
            this.javaRunner = new JavaRunner();
        }catch (Exception e){
            logger.warning(e.getMessage());
        }
        logger.fine("initialized at " + System.currentTimeMillis());
    }

    public void shutdown(){
        logger.fine("shutdown");
        interrupt();
    }

    public void run(){

        while (this.myServer.getPeerState() == ZooKeeperPeerServer.ServerState.FOLLOWING) {
            TCPListener.MessageAndSocket incoming = null;
            while (incoming == null) {
                incoming = incomingTCPMessagesAndSockets.poll();
            }
            logger.fine("incoming message...");

            switch (incoming.getMessage().getMessageType()){
                case ELECTION:
                    //for stage 3, assume its from old election
                    break;
                case WORK:
                    InputStream is = new ByteArrayInputStream(incoming.getMessage().getMessageContents());
                    try {
                        String result = javaRunner.compileAndRun(is);
                        byte[] resultBytes = result.getBytes();
                        //HOW DOES THE MESSAGES NOW WORK? WHAT IS KEPT IN CONTENTS?
                        Message finishedWork = new Message(Message.MessageType.COMPLETED_WORK, resultBytes, this.myServer.getAddress().getHostName(), this.myServer.getAddress().getPort(), incoming.getMessage().getSenderHost() , incoming.getMessage().getSenderPort(), incoming.getMessage().getRequestID());
                        logger.log(Level.INFO,"sending to leader completed work of request #" + incoming.getMessage().getRequestID());
                        Socket socket = incoming.getSocket();
                        OutputStream sendStream = socket.getOutputStream();
                        sendStream.write(finishedWork.getNetworkPayload());
                        socket.close();
                    } catch (Exception e) {
                        logger.log(Level.INFO, e.getMessage());
                        Message errorInWork = new Message(Message.MessageType.COMPLETED_WORK, incoming.getMessage().getMessageContents(), this.myServer.getAddress().getHostName(), this.myServer.getAddress().getPort(), incoming.getMessage().getSenderHost() , incoming.getMessage().getSenderPort(), incoming.getMessage().getRequestID(), true);
                    }

                    //needs to be input as an input stream
                    break;
                case COMPLETED_WORK:
                    //shouldn't be receiving. ignore
                case GOSSIP:
                    //not for stage 3
                case NEW_LEADER_GETTING_LAST_WORK:
                    //not for stage 3

            }

        }
    }

    static String getStringContentsFromMessage(Message message) {
        //messsage.contents is a byte array that holds the election notification info.
        //get it, and then unpack it into an election notification constructor
        byte[] contents = message.getMessageContents();
        //
        if(message.getMessageType() == Message.MessageType.WORK || message.getMessageType() == Message.MessageType.COMPLETED_WORK) {
            return new String(contents, StandardCharsets.UTF_8); // for UTF-8 encoding
        } else {
            // in later stages for different types, might need to add else
            return null;
        }

    }
}
