package edu.yu.cs.com3800.stage4;

import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import static edu.yu.cs.com3800.Util.readAllBytesFromNetwork;

public class TCPListener extends Thread implements LoggingServer {

    private LinkedBlockingQueue<MessageAndSocket> tcpMessagesWithSockets;
    private ServerSocket listener;
    private boolean interrupted;
    private Logger logger;


    public TCPListener(int serverPort, LinkedBlockingQueue<MessageAndSocket> tcpMessagesWithSockets, Long id){
        this.tcpMessagesWithSockets = tcpMessagesWithSockets;
        interrupted = false;
        try {
            logger = initializeLogging(id, "TCPListener");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            this.listener = new ServerSocket(serverPort + 2); // by requirements doc, tcp port two more than udp port
        }catch (IOException e){
            //log exception here
        }
        logger.fine("Initialized");

    }

    @Override
    public void run(){
        while(!this.interrupted){
            try {
                logger.fine("socket listening");
                Socket socket = listener.accept();
                logger.fine("socket connecting");
                byte[] payload = readAllBytesFromNetwork(socket.getInputStream());
                logger.fine("payload read");
                Message message = new Message(payload);
                logger.fine("sharing class");
                boolean sent = tcpMessagesWithSockets.offer(new MessageAndSocket(message, socket)); // ALSO AD THE SOCKET TO THE QUEUE SO YOU CAN RETURN THE RESULTS THROUGH THE SAME CONNECTIONS
                logger.fine("message shared with handling sibling class: " + sent);
            } catch (IOException e) {
                //log error
            }
        }
    }

    public void shutdown(){
        logger.fine("shutdown commencing");
        try {
            this.listener.close();
        } catch (IOException e) {
            //LOG ERROR
        }
        this.interrupted = true;
        logger.fine("shutdown complete");
    }

    public class MessageAndSocket{
        private final Message message;
        private final Socket socket;

        public MessageAndSocket(Message message, Socket socket){
            this.message = message;
            this.socket = socket;
        }

        public Message getMessage(){
            return this.message;
        }

        public Socket getSocket(){
            return  this.socket;
        }

    }
}
