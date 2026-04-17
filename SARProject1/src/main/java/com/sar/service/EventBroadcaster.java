package com.sar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages SSE client connections and broadcasts events to all of them.
 * Thread-safe for concurrent access from multiple ConnectionThreads.
 */
public class EventBroadcaster {
    private static final Logger logger = LoggerFactory.getLogger(EventBroadcaster.class);
    
    // Thread-safe list of connected client output streams
    private final CopyOnWriteArrayList<OutputStream> clients = new CopyOnWriteArrayList<>();
    
    /**
     * Register a new SSE client connection.
     * Called by EventHandler when a browser connects to /events.
     */
    public void registerClient(OutputStream clientStream) {
        clients.add(clientStream);
        logger.info("SSE client registered. Total clients: {}", clients.size());
    }
    
    /**
     * Remove a disconnected client.
     * Called when client closes connection or throws IOException.
     */
    public void removeClient(OutputStream clientStream) {
        clients.remove(clientStream);
        logger.info("SSE client removed. Total clients: {}", clients.size());
    }
    
    /**
     * Broadcast an event to all connected clients.
     * Called by GroupServiceImpl after group operations.
     * 
     * @param eventData JSON string with event information
     *                  Example: {"type":"group.created","groupNumber":"42"}
     */
    public void broadcast(String eventData) {
        if (clients.isEmpty()) {
            logger.debug("No SSE clients connected, skipping broadcast");
            return;
        }
        
        // Format event as SSE message: "data: <json>\n\n"
        String sseMessage = "data: " + eventData + "\n\n";
        byte[] message = sseMessage.getBytes();
        
        logger.debug("Broadcasting to {} clients: {}", clients.size(), eventData);
        
        // Send to each client
        for (OutputStream clientStream : clients) {
            try {
                clientStream.write(message);
                clientStream.flush();
                logger.debug("Event sent to client");
            } catch (IOException e) {
                // Client disconnected or error writing
                logger.warn("Failed to send event to client, removing: {}", e.getMessage());
                removeClient(clientStream);
            }
        }
    }
    
    /**
     * Get the number of currently connected SSE clients.
     * Useful for debugging.
     */
    public int getClientCount() {
        return clients.size();
    }
}