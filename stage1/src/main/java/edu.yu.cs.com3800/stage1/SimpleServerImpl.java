package edu.yu.cs.com3800.stage1;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import edu.yu.cs.com3800.JavaRunner;
import edu.yu.cs.com3800.SimpleServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class SimpleServerImpl implements SimpleServer {

    class MyHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            logger.info("New request at: " + new Date(System.currentTimeMillis()));
            //log the input java code, the output, and the status code returned
            Headers headers = t.getRequestHeaders();
            if ((!headers.containsKey("Content-Type")) || !headers.get("Content-Type").get(0).equals("text/x-java-source")){
                logger.info("Input code:     " + new String(t.getRequestBody().readAllBytes())); // SAFER TO NOT USE REQUEST BODY IN LATER STAGES
                logger.info("Invalid headers");
                logger.info("Output:  " + "no output -- invalid headers");

                logger.info("Status code: " + 400);



                OutputStream os = t.getResponseBody();

                InputStream is = t.getRequestBody();
                is.close();
                t.sendResponseHeaders(400, -1);
                //LOG BAD HEADERS
                OutputStream outputStreams = t.getResponseBody();// prevent connection from failing next time
                outputStreams.close();
            }else {
                InputStream is = t.getRequestBody();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                is.transferTo(baos);
                //is.close();
                byte[] ba = baos.toByteArray();
                InputStream is2 = new ByteArrayInputStream(baos.toByteArray());
                logger.info("Input code:     " + new String(ba));


                JavaRunner jr = new JavaRunner();
                String response = null;

                try {
                    response = jr.compileAndRun(is2);
                    is2.close();
                    t.sendResponseHeaders(200, response.length());
                    OutputStream outputStreams = t.getResponseBody();
                    outputStreams.write(response.getBytes());
                    outputStreams.close();
                    logger.info("Output:  " + response);
                    logger.info("Status code: " + 200);

                } catch (ReflectiveOperationException | IllegalArgumentException e) {
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
                    logger.info("Status code:" + 400);

                    OutputStream outputStreams = t.getResponseBody();
                    logger.info(new String(errMessageS + errS));

                    byte[] byteMessage = new byte[errMessage.length + err.length];

                    for (int i = 0; i < byteMessage.length; ++i) {
                        byteMessage[i] = i < errMessage.length ? errMessage[i] : err[i - errMessage.length];
                    }
                    System.out.println(byteMessage);

                    outputStreams.write(byteMessage); // is this going to lead to an error?
                    outputStreams.close();

                }
            }


        }
    }

    HttpServer server;
    Logger logger;
    FileHandler fh;
    File baseDir;

    public static void main(String[] args) {
        int port = 9000;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        SimpleServer myserver = null;
        try {
            myserver = new SimpleServerImpl(port);
            myserver.start();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.out.println("error");
            if (!(myserver == null)) myserver.stop();
        }
    }


    public SimpleServerImpl(int port) throws IOException {
        logger = Logger.getLogger("MyLog");
        fh = new FileHandler(System.getProperty("user.dir") + File.separator + "logFiles" + File.separator + "MyLogFile" +  String.valueOf(new Date(System.currentTimeMillis())).replaceAll("\\s+","").replaceAll(":", ".") + ".log");
        logger.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);
        logger.info("Initialized at: " +  new Date(System.currentTimeMillis()) + "\n");

        server = HttpServer.create(new InetSocketAddress(port), 10); // https://localhost:9000/
        server.createContext("/compileandrun", new MyHandler());
        server.setExecutor(null); // creates a default executor
    }

    public void start(){
        server.start();
    }

    public void stop(){
        server.stop(0);
    }
}