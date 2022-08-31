package edu.yu.cs.com3800.stage4;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.JavaRunner;
import edu.yu.cs.com3800.LoggingServer;
import edu.yu.cs.com3800.Message;
import edu.yu.cs.com3800.Util;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayServer implements LoggingServer {
    //Two pieces.

    // Part one:
    //   a) accepts client requests,
    //   b) connects via TCP to the leader
    //   c) gets response from leader
    //   d) sends response back to client

    // Part two:
    //   a) keep track of cluster leader through observing leader elections

    private HttpServer server;
    private InetSocketAddress leaderAddress;
    private GatewayPeerServerImpl gatewayPeer;

    private Logger logger;

    public GatewayServer(int HTTPport, GatewayPeerServerImpl gatewayPeer){
        //MAKE GATEWAY PEER HERE
        this.gatewayPeer = gatewayPeer;

        while (leaderAddress == null){
            leaderAddress = gatewayPeer.getLeaderInetSocket();
        }

        try {
            logger = initializeLogging(gatewayPeer.getServerId(), "GatewayServer");

        } catch (IOException e) {
            e.printStackTrace();
        }
        //LOGGING INIT HERE - SEE @130
        try {
            logger.fine("HTTP Server initilized");
            server = HttpServer.create(new InetSocketAddress(HTTPport), 10); // https://localhost:9000/
            server.createContext("/compileandrun", new MyHandler());
            //limits amount of requests that can be processed concurrently. Therefore, no threadpool needed in the server itself
            server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        }catch(IOException e){
            //LOG THE ERROR
            e.printStackTrace();
        }

    }

    public void start(){
        logger.info("http server started");
        server.start();
    }

    public void stop(){
        server.stop(0);
    }


    class MyHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            logger.info("New request at: " + new Date(System.currentTimeMillis()));
            //log the input java code, the output, and the status code returned
            Headers headers = t.getRequestHeaders();
            if ((!headers.containsKey("Content-Type")) || !headers.get("Content-Type").get(0).equals("text/x-java-source")){

                logger.info("Invalid headers. Content-Type must be \"text/x-java-source\". Responding with error code");

                OutputStream os = t.getResponseBody();
                InputStream is = t.getRequestBody();
                is.close();

                t.sendResponseHeaders(400, -1);
                OutputStream outputStreams = t.getResponseBody();// prevent connection from failing next time
                outputStreams.close();
            }else {
                InputStream is = t.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                is.transferTo(baos);
                //is.close();
                byte[] ba = baos.toByteArray();
                //InputStream is2 = new ByteArrayInputStream(baos.toByteArray());
                //logger.info("Input code:     " + new String(ba));


                //JavaRunner jr = new JavaRunner();
                //Message response = null;

              // try {

                    //Connect to leader and send him the work and wait for his response
                    //make a new socket for each handling of request? multiple threads per socket ok?
                    logger.fine("connecting to leader (port " +leaderAddress.getPort()+ "), sending request");

                    Socket socket = new Socket(leaderAddress.getHostName(), leaderAddress.getPort() + 2); //tcp port is + 2 of the udp port
                    OutputStream sendStream = socket.getOutputStream();
                    //write is2 to the stream
                    //Message workMessage = new Message(Message.MessageType.WORK, is2.readAllBytes(), gatewayPeer.getAddress().getHostName(),  gatewayPeer.getAddress().getPort(), leaderAddress.getHostName(), leaderAddress.getPort() + 2); // +2 for tcp port      // maybe ba instead of is2.rabs()?
                    Message workMessage = new Message(Message.MessageType.WORK, ba, gatewayPeer.getAddress().getHostName(),  gatewayPeer.getAddress().getPort(), leaderAddress.getHostName(), leaderAddress.getPort() + 2); // +2 for tcp port      // maybe ba instead of is2.rabs()?

                //is2.close();
                    logger.fine("writing message to output stream");
                    logger.fine(workMessage.toString());
                    sendStream.write(workMessage.getNetworkPayload());
                    //sendStream.flush();


                    logger.fine("waiting for leader reply");
                    byte[] responseBytes = Util.readAllBytesFromNetwork(socket.getInputStream());
                    Message response = new Message(responseBytes);
                    byte[] responseBodyBytes = response.getMessageContents();

                    t.sendResponseHeaders(200, responseBodyBytes.length);
                    OutputStream outputStreams = t.getResponseBody();
                    outputStreams.write(responseBodyBytes);
                    outputStreams.close();
                    logger.fine("Output:  " + response);
                    logger.fine("Status code: " + 200);
                    socket.close();

                    /*
                } catch (IllegalArgumentException e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    String errS = sw.toString();
                    byte[] err = errS.getBytes();
                    String errMessageS = e.getMessage() + "\n";
                    byte[] errMessage = errMessageS.getBytes();

                    //explanation message s in the error. just need to log -- bottom line is that the code didn't compile
                    //is2.close();
                    t.sendResponseHeaders(400, errMessage.length + err.length );
                    //logger.info("Status code:" + 400);

                    OutputStream outputStreams = t.getResponseBody();
                    //logger.info(new String(errMessageS + errS));

                    byte[] byteMessage = new byte[errMessage.length + err.length];

                    for (int i = 0; i < byteMessage.length; ++i) {
                        byteMessage[i] = i < errMessage.length ? errMessage[i] : err[i - errMessage.length];
                    }
                    System.out.println(byteMessage);

                    outputStreams.write(byteMessage); // is this going to lead to an error?
                    outputStreams.close();
                }
               */
            }
        }
    }

}
