package edu.yu.cs.com3800;

import edu.yu.cs.com3800.stage3.ZooKeeperPeerServerImpl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaRunnerFollower extends Thread implements LoggingServer{

    ZooKeeperPeerServer myServer;
    LinkedBlockingQueue<Message> incomingMessages;
    LinkedBlockingQueue<Message> outgoingMessages;
    JavaRunner javaRunner;
    Logger logger;
    //boolean shutdown = false;

    public JavaRunnerFollower(ZooKeeperPeerServer server, LinkedBlockingQueue<Message> incomingMessages, LinkedBlockingQueue<Message> outgoingMessages){
        this.myServer = server;
        this.incomingMessages = incomingMessages;
        this.outgoingMessages = outgoingMessages;
        try {
            this.logger = initializeLogging(this.myServer.getServerId(), this.getClass().getCanonicalName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try{
            this.javaRunner = new JavaRunner();
        }catch (Exception e){
            logger.log(Level.INFO, e.getMessage());
        }
    }

    public void shutdown(){
        interrupt();
    }

    public void run(){

        while (this.myServer.getPeerState() == ZooKeeperPeerServer.ServerState.FOLLOWING) {
            Message incoming = null;
            while (incoming == null) {
                incoming = incomingMessages.poll();
            }

            switch (incoming.getMessageType()){
                case ELECTION:
                    //for stage 3, assume its from old election
                    break;
                case WORK:
                    InputStream is = new ByteArrayInputStream(incoming.getMessageContents());
                    try {
                        String result = javaRunner.compileAndRun(is);
                        byte[] resultBytes = result.getBytes();
                        //HOW DOES THE MESSAGES NOW WORK? WHAT IS KEPT IN CONTENTS?
                        Message finishedWork = new Message(Message.MessageType.COMPLETED_WORK, resultBytes, this.myServer.getAddress().getHostName(), this.myServer.getAddress().getPort(), incoming.getSenderHost() , incoming.getSenderPort(), incoming.getRequestID());
                        logger.log(Level.INFO,"sending to leader completed work of request #" + incoming.getRequestID());
                        this.outgoingMessages.add(finishedWork);
                    } catch (Exception e) {
                        logger.log(Level.INFO, e.getMessage());
                        Message errorInWork = new Message(Message.MessageType.COMPLETED_WORK, incoming.getMessageContents(), this.myServer.getAddress().getHostName(), this.myServer.getAddress().getPort(), incoming.getSenderHost() , incoming.getSenderPort(), incoming.getRequestID(), true);
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
