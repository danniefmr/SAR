package com.sar.server;

import com.sar.controller.HttpController;
import com.sar.service.EventBroadcaster;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.Headers;
import com.sar.web.http.ReplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.TimeZone;

public class ConnectionThread extends Thread  {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionThread.class);
    private final HttpController controller;
    private final EventBroadcaster eventBroadcaster;

    private final Main HTTPServer;
    private final ServerSocket ServerSock;
    private final Socket client;
    private final DateFormat HttpDateFormat;
    
    /** Creates a new instance of httpThread */
    public ConnectionThread(Main HTTPServer, ServerSocket ServerSock, 
    Socket client, HttpController controller, EventBroadcaster eventBroadcaster) {
        this.HTTPServer = HTTPServer;
        this.ServerSock = ServerSock;
        this.client = client;
        this.controller = controller;
        this.eventBroadcaster = eventBroadcaster;
        this.HttpDateFormat = new SimpleDateFormat("EE, d MMM yyyy HH:mm:ss zz", Locale.UK);
        this.HttpDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        setPriority(NORM_PRIORITY - 1);
}
    

     /** Reads a new HTTP Request from the input steam in to a Request object
     * @param TextReader   input stream Buffered Reader coonected to client socket
     * @param echo  if true, echoes the received message to the screen
     * @return Request object containing the request received from the client, or null in case of error
     * @throws java.io.IOException 
     */
    public Request GetRequest (BufferedReader TextReader) throws IOException {
        // Get first line
        String request = TextReader.readLine( );  	// Reads the first line
        if (request == null) {
            logger.debug("Invalid request Connection closed");
            return null;
        }
        logger.info("Request: ", request);
        StringTokenizer st= new StringTokenizer(request);
        if (st.countTokens() != 3) {
           logger.debug("Invalid request received ", request);
           return null;  // Invalid request
        } 
        //create an object to store the http request
        Request req= new Request (client.getInetAddress ().getHostAddress (), client.getPort (), ServerSock.getLocalPort ());  
        req.method= st.nextToken();    // Store HTTP method
        req.urlText= st.nextToken();    // Store URL
        req.version= st.nextToken();  // Store HTTP version

        // Check if this is an HTTP (non-SSL) connection (port 20000)
        // If so, mark for redirect to HTTPS
        boolean needsRedirect = (ServerSock.getLocalPort()== 20000);
        if (needsRedirect) {
            req.urlText = "REDIRECT:" + req.urlText;
        }
     
        // read the remaining headers in to the headers property of the request object
        // (must do this even if redirecting, to keep HTTP protocol in sync)   
        for (;;) {
            String header = TextReader.readLine();
            if (header != null) {
                if (header.length() == 0) {
                    break;
                }
                int colonPos = header.indexOf(':');
                if (colonPos > 0) {
                    String name  = header.substring(0, colonPos).trim();
                    String value = header.substring(colonPos + 1).trim();
                    req.headers.setHeader(name, value);
                } else {
                    logger.warn("Bad header line: {}", header);
                }
            } else {
                // client closed connection unexpectedly
                break;
            }
        }
        req.parseCookies();
        // check if the Content-Length size is different than zero. If true read the body of the request (that can contain POST data)
        int clength= 0;
        try {
            String len= req.headers.getHeaderValue("Content-Length");
            if (len != null)
                clength= Integer.parseInt (len);
            else if (!TextReader.ready ())
                clength= 0;
        } catch (NumberFormatException e) {
            logger.error("Bad request\n");
            return null;
        }
        if (clength>0) {
            // Length is not 0 - read data to string
            String str= new String ();
            char [] cbuf= new char [clength];
            //the content is not formed by line ended with \n so it need to be read char by char
            int n, cnt= 0;
            while ((cnt<clength) && ((n= TextReader.read (cbuf)) > 0)) {
                str= str + new String (cbuf, 0, n);
                cnt += n;
            }
            if (cnt != clength) {
                logger.info("Read request with {} data bytes and Content-Length = {} bytes\n",cnt, clength);
                return null;
            }
            req.text= str;
            logger.debug("Contents('"+req.text+"')\n");

            // Parse form-encoded POST body into postParameters
            String contentType = req.headers.getHeaderValue("Content-Type");
            if (contentType != null && contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")){
                String[] pairs = req.text.split("&");
                for (String pair : pairs) {
                    int eq = pair.indexOf('=');
                    if (eq >= 0) {
                        String name = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                        String value = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                        req.getPostParameters().put(name, value);
                    }
                }
            }
        }

        return req;
    }    
   
    
     @Override
    public void run( ) {

        Response res= null;   // HTTP response object
        Request req = null;   //HTTP request object
        PrintStream TextPrinter= null;

        try {
            /*get the input and output Streams for the TCP connection and build
              a text (ASCII) reader (TextReader) and writer (TextPrinter) */
            InputStream in = client.getInputStream( );
            BufferedReader TextReader = new BufferedReader(
                    new InputStreamReader(in, "8859_1" ));
            OutputStream out = client.getOutputStream( );
            TextPrinter = new PrintStream(out, false, "8859_1");
            client.setSoTimeout(500); // 500ms timeout for faster page load response
            //keep alive loop
            boolean keepAlive = true;
            while (keepAlive) {
                // Read and parse request
                req= GetRequest(TextReader); //reads the input http request if everything was read ok it returns true
                // Check if redirect is needed
                if (req != null && req.urlText.startsWith("REDIRECT:")) {
                    res = new Response(HTTPServer.ServerName);
                    String originalUrl = req.urlText.substring("REDIRECT:".length());
                    String redirectUrl = "https://localhost:20043" + originalUrl;
                    
                    res.setCode(ReplyCode.MOVEDPERM); // 301 Moved Permanently
                    res.setHeader("Location", redirectUrl);
                    res.setHeader("Content-Length", "0");
                    res.send_Answer(TextPrinter);
                    
                    keepAlive = false;
                    break;
                }
                //Create response object. 
                res= new Response(HTTPServer.ServerName);
                String connHeader = (req != null) ? 
                req.headers.getHeaderValue("Connection") : "close";
            
                // In HTTP/1.1, default is keep-alive unless client says "close"
                if (connHeader != null && connHeader.equalsIgnoreCase("close")) {
                    keepAlive = false;
                    res.setHeader("Connection", "close");
                } else {
                    // Keep connection open
                    res.setHeader("Connection", "keep-alive");
                }
                // Process request
                if (req != null) {
                    controller.handleRequest(req, res);
                } else {
                    // Request parsing failed, close connection
                    keepAlive = false;
                }
                
                // Check if this is an SSE request before sending response
                boolean isSseRequest = (req != null && req.urlText.contains("/events"));
                
                // Send response
                res.send_Answer(TextPrinter);

                if (isSseRequest) {
                    logger.info("Client connected to SSE stream, registering with broadcaster");
    
                    // Register with broadcaster and keep connection open for streaming
                    try {
                        // Register the output stream so broadcaster can send events
                        eventBroadcaster.registerClient(out);
                        logger.info("SSE client registered. Total clients: {}", eventBroadcaster.getClientCount());
                        
                        // Keep thread alive for SSE streaming
                        // Exit on socket timeout or client disconnect
                        // Don't read new requests - this connection is now a long-lived SSE stream
                        keepAlive = false; // Exit main keep-alive loop
                        
                        // Keep the thread alive in a loop
                        // Socket timeout will trigger periodically to allow checking for shutdown
                        while (true) {
                            try {
                                Thread.sleep(1000); // Sleep longer to reduce CPU usage
                            } catch (InterruptedException ie) {
                                logger.info("SSE thread interrupted");
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // Handle all exceptions: SocketTimeoutException, IOException, InterruptedException, etc.
                        if (e instanceof SocketTimeoutException) {
                            logger.debug("SSE connection timed out (expected periodic timeout)");
                        } else if (e instanceof IOException) {
                            logger.info("SSE client disconnected: {}", e.getMessage());
                        } else {
                            logger.error("Error in SSE stream", e);
                        }
                    } finally {
                        eventBroadcaster.removeClient(out);
                        logger.info("SSE client unregistered. Total clients: {}", eventBroadcaster.getClientCount());
                    }
                }
            }


        } catch (Exception e) {
            logger.error("Error processing request", e);
        } finally {
            cleanup(TextPrinter);
        }
    }   


    private void cleanup(PrintStream TextPrinter) {
        try {
            if (TextPrinter != null) TextPrinter.close();
            if (client != null) client.close();
        } catch (IOException e) {
            logger.error("Error during cleanup", e);
        } finally {
            HTTPServer.thread_ended();
            logger.debug("Connection closed for client: {}:{}", 
                client.getInetAddress().getHostAddress(), 
                client.getPort());
        }
    }

}
