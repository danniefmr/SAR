// com.sar.service.GroupServiceImpl.java
package com.sar.service;

import com.sar.model.Group;
import com.sar.repository.GroupRepository;
import com.sar.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * GroupServiceImpl implements business logic for group management.
 * 
 * Service Layer Responsibilities:
 * - Input validation and business rule enforcement
 * - Orchestrating repository calls
 * - Error handling and logging
 * - Data transformation (e.g., generating HTML representations)
 * 
 * Event Broadcasting Integration Point:
 * When groups are created, updated, or deleted, the EventBroadcaster service
 * should be notified to push real-time updates to connected SSE clients.
 * 
 * To integrate SSE:
 * 1. Inject EventBroadcaster via constructor (similar to GroupRepository)
 * 2. Call eventBroadcaster.broadcast() after successful database operations
 * 3. Format event data appropriately (e.g., JSON with event type and group data)
 * 
 * Example event types:
 * - "group.created" when saveGroup() creates a new group
 * - "group.updated" when saveGroup() updates an existing group
 * - "group.deleted" when deleteGroup() removes a group
 * - "group.accessed" when incrementAccessCount() is called
 */
public class GroupServiceImpl implements GroupService {
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);
    private final GroupRepository repository;
    private final EventBroadcaster eventBroadcaster;

    public GroupServiceImpl(GroupRepository repository, EventBroadcaster eventBroadcaster) {
        this.repository = repository;
        this.eventBroadcaster = eventBroadcaster;
    }

    @Override
    public List<Group> getAllGroups() {
        try {
            return repository.findAll();
        } catch (Exception e) {
            logger.error("Error getting all groups", e);
            throw new RuntimeException("Failed to retrieve groups", e);
        }
    }

    @Override
    public Group getGroup(String groupNumber) {
        try {
            return repository.findByGroupNumber(groupNumber)
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupNumber));
        } catch (Exception e) {
            logger.error("Error getting group: " + groupNumber, e);
            throw new RuntimeException("Failed to retrieve group", e);
        }
    }

    @Override
    public void saveGroup(String groupNumber, String[] numbers, String[] names, boolean counter) {
        try {
            // Input validation
            if (groupNumber == null || groupNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Group number cannot be empty");
            }
            if (numbers.length != Main.GROUP_SIZE || names.length != Main.GROUP_SIZE) {
                throw new IllegalArgumentException("Invalid number of members");
            }

            // Check if group exists (for event broadcasting)
            boolean isNewGroup = !repository.findByGroupNumber(groupNumber).isPresent();

            // Create or update group
            Group group = repository.findByGroupNumber(groupNumber)
                .orElse(new Group());

            group.setGroupNumber(groupNumber);
            group.setCounter(counter);
            group.setLastUpdate(Instant.now().toString());

            // Set members
            for (int i = 0; i < Main.GROUP_SIZE; i++) {
                group.setMember(i, numbers[i], names[i]);
            }

            repository.save(group);
            logger.info("Saved group: {}", groupNumber);

            // SSE Integration Point:
            // After successfully saving the group, broadcast an event to all connected SSE clients.
            // This allows real-time updates in the browser without page refresh.
            // 
            // Example implementation:
            String eventType = isNewGroup ? "group.created" : "group.updated";
            String eventData = "{\"type\":\"" + eventType + "\",\"groupNumber\":\"" + groupNumber + "\"}";
            eventBroadcaster.broadcast(eventData);
            
        } catch (Exception e) {
            logger.error("Error saving group: " + groupNumber, e);
            throw new RuntimeException("Failed to save group", e);
        }
    }

    @Override
    public void deleteGroup(String groupNumber) {
        try {
            repository.delete(groupNumber);
            logger.info("Deleted group: {}", groupNumber);
            
            // SSE Integration Point:
            // After successfully deleting the group, broadcast a deletion event.
            // Example: eventBroadcaster.broadcast("{\"type\":\"group.deleted\",\"groupNumber\":\"" + groupNumber + "\"}");
            String eventData = "{\"type\":\"group.deleted\",\"groupNumber\":\"" + groupNumber + "\"}";
            eventBroadcaster.broadcast(eventData);


        } catch (Exception e) {
            logger.error("Error deleting group: " + groupNumber, e);
            throw new RuntimeException("Failed to delete group", e);
        }
    }

    @Override
    public void incrementAccessCount(String groupNumber) {
        try {
            repository.incrementAccessCount(groupNumber);
            
            // SSE Integration Point:
            // Optionally broadcast when a group's access count is incremented.
            // This allows real-time statistics updates across connected clients.
            // Example: eventBroadcaster.broadcast("{\"type\":\"group.accessed\",\"groupNumber\":\"" + groupNumber + "\"}");
            
        } catch (Exception e) {
            logger.error("Error incrementing access count for group: " + groupNumber, e);
            throw new RuntimeException("Failed to increment access count", e);
        }
    }

    @Override
    public String getLastUpdate(String groupNumber) {
        try {
            return repository.getLastUpdate(groupNumber);
        } catch (Exception e) {
            logger.error("Error getting last update for group: " + groupNumber, e);
            throw new RuntimeException("Failed to get last update", e);
        }
    }

    @Override
    public boolean groupExists(String groupNumber) {
        try {
            return repository.findByGroupNumber(groupNumber).isPresent();
        } catch (Exception e) {
            logger.error("Error checking group existence: " + groupNumber, e);
            throw new RuntimeException("Failed to check group existence", e);
        }
    }

    @Override
    public String generateGroupHtml() {
        try {
            List<Group> groups = repository.findAll();
            StringBuilder html = new StringBuilder();
            
            html.append("<table border=\"1\">\r\n");
            html.append("<tr>\r\n<th>Grupo</th>");
            html.append("<th colspan=\"").append(Main.GROUP_SIZE).append("\">Membros</th>\r\n</tr>\r\n");

            for (Group group : groups) {
                html.append("<tr>\r\n");
                html.append("<td>").append(group.getGroupNumber()).append("</td>");
                
                for (int i = 0; i < Main.GROUP_SIZE; i++) {
                    Group.Member member = group.getMember(i);
                    html.append("<td>");
                    if (member != null) {
                        html.append(member.getNumber()).append(" - ")
                            .append(member.getName());
                    }
                    html.append("</td>");
                }
                
                html.append("\r\n</tr>\r\n");
            }
            
            html.append("</table>\r\n");
            return html.toString();
        } catch (Exception e) {
            logger.error("Error generating group HTML", e);
            throw new RuntimeException("Failed to generate group HTML", e);
        }
    }    
}
