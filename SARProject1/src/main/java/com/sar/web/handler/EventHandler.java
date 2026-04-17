package com.sar.web.handler;

import com.sar.service.EventBroadcaster;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class EventHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(EventHandler.class);
    private final EventBroadcaster eventBroadcaster;

    public EventHandler(EventBroadcaster eventBroadcaster) {
        this.eventBroadcaster = eventBroadcaster;
    }

    /**
     * Handles GET /events - Establishes SSE connection
     * 
     * Steps:
     * 1. Set SSE headers (Content-Type: text/event-stream, etc.)
     * 2. Get the output stream and register with broadcaster
     * 3. Send initial "connected" message
     * 4. Keep connection open (don't close the stream)
     * 
     * The connection will stay open until:
     * - Client closes the browser/tab
     * - Network error occurs
     * - Server shuts down
     */
    @Override
    protected void handleGet(Request request, Response response) {
        logger.debug("GET /events - SSE connection request");
        
        try {
            // Set SSE response headers
            response.setCode(ReplyCode.OK);
            response.setHeader("Content-Type", "text/event-stream");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");
            response.setHeader("X-Accel-Buffering", "no"); // Disable proxy buffering for streaming
            
            // Send initial SSE message to trigger browser's onopen event
            // This tells the browser the connection is established and valid
            response.setText(":connected\n\n");
            
            logger.info("SSE connection prepared, will be registered in ConnectionThread");
            
        } catch (Exception e) {
            logger.error("Error setting up SSE connection", e);
            response.setCode(ReplyCode.SERVERERROR);
            response.setText("Error establishing SSE connection");
        }
    }

    /**
     * POST is not supported on SSE endpoint
     */
    @Override
    protected void handlePost(Request request, Response response) {
        response.setCode(ReplyCode.NOTIMPLEMENTED);
        response.setText("POST not supported on /events endpoint");
    }

    @Override
    protected void handleDelete(Request request, Response response) {
        response.setCode(ReplyCode.NOTIMPLEMENTED);
        response.setText("DELETE not supported on /events endpoint");
    }
}