package com.sar.web.handler;

import com.sar.service.GroupService;
import com.sar.web.http.Request;
import com.sar.web.http.Response;
import com.sar.web.http.ReplyCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ApiHandler provides RESTful JSON API for group management.
 * 
 * This handler returns JSON responses, not HTML pages.
 * The index.html page uses JavaScript to call these endpoints via AJAX.
 * 
 * Endpoints:
 * - GET /api → Returns JSON array of all groups
 * - POST /api → Creates/updates a group, returns JSON response
 * 
 * Response format should be JSON with appropriate HTTP headers.
 */
public class ApiHandler extends AbstractRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
    private final GroupService groupService;

    public ApiHandler(GroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * Handles GET /api - Returns all groups as JSON.
     * 
     * The response should contain group data in JSON format that the
     * JavaScript in index.html can parse and display in the table.
     * 
     * Appropriate HTTP headers must be set for JSON responses.
     */
    @Override
    protected void handleGet(Request request, Response response) {
        logger.debug("GET /api - Fetching all groups");
        
        // return an HTML table (index.html expects the response to contain
        // a <table> element it can insert directly into the page)
        String html = groupService.generateGroupHtml();
        response.setCode(ReplyCode.OK);
        response.setText(html);
        response.setHeader("Content-Type", "text/html");
        response.setHeader("Content-Length", String.valueOf(html.getBytes().length));
    }

    /**
     * Handles POST /api - Create or update a group.
     * 
     * The form data from index.html contains group information that
     * should be validated and persisted using the GroupService.
     * 
     * Response should be JSON indicating success or failure.
     * Appropriate HTTP headers must be set.
     */
    @Override
    protected void handlePost(Request request, Response response) {
        logger.debug("POST /api - Creating/updating group");
        Properties p = request.getPostParameters();

        String gnum = p.getProperty("groupNumber");
        if (gnum == null || gnum.isEmpty()) {
            response.setCode(ReplyCode.BADREQ);
            response.setText("{\"success\":false,\"message\":\"missing groupNumber\"}");
        } else {
            List<String> nums = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; ; i++) {
                String num = p.getProperty("number" + i);
                String nam = p.getProperty("name"   + i);
                if (num == null || nam == null) break;
                nums.add(num);
                names.add(nam);
            }
            boolean counter = "on".equals(p.getProperty("counter"));

            try {
                // adapt to service signature (strings and arrays)
                groupService.saveGroup(gnum,
                        nums.toArray(new String[0]),
                        names.toArray(new String[0]),
                        counter);
                response.setCode(ReplyCode.OK);
                response.setText("{\"success\":true}");
                response.addCookie("lastGroupNumber="+gnum+"; Path=/");
                
                // Handle createCount cookie safely
                try {
                    String createCountStr = request.cookies.getProperty("createCount", "0");
                    int createCount = Integer.parseInt(createCountStr);
                    createCount++;
                    response.addCookie("createCount=" + createCount + "; Path=/");
                } catch (NumberFormatException nfe) {
                    // If parsing fails, just set to 1
                    logger.warn("Failed to parse createCount cookie, setting to 1");
                    response.addCookie("createCount=1; Path=/");
                }
            } catch (Exception ex) {
                response.setCode(ReplyCode.NOTDEFINED);
                response.setText("{\"success\":false,\"message\":\""+ex.getMessage()+"\"}");
                logger.error("Error saving group: {}", ex.getMessage(), ex);
            }
        }
        String body = response.text; // use public field instead of nonexistent getter
        response.setHeader("Content-Type","application/json");
        response.setHeader("Content-Length", String.valueOf(body.getBytes().length));
    }

    @Override
    protected void handleDelete(Request request, Response response) {
        logger.debug("DELETE /api - Deleting group");
        
        // Extract groupNumber from URL query string
        // URL format: /api?groupNumber=1
        String url = request.urlText;
        String groupNumber = null;
        
        int qPos = url.indexOf('?');
        if (qPos >= 0) {
            String query = url.substring(qPos + 1);
            String[] params = query.split("&");
            for (String param : params) {
                int eq = param.indexOf('=');
                if (eq >= 0) {
                    String name = param.substring(0, eq);
                    String value = param.substring(eq + 1);
                    if ("groupNumber".equals(name)) {
                        try {
                            groupNumber = java.net.URLDecoder.decode(value, "UTF-8");
                        } catch (Exception e) {
                            // ignore decode errors
                        }
                    }
                }
            }
        }
        
        if (groupNumber == null || groupNumber.isEmpty()) {
            response.setCode(ReplyCode.BADREQ);
            response.setText("{\"success\":false,\"message\":\"missing groupNumber\"}");
        } else {
            try {
                groupService.deleteGroup(groupNumber);
                response.setCode(ReplyCode.OK);
                response.setText("{\"success\":true}");
                
                // Handle deleteCount cookie safely
                try {
                    String deleteCountStr = request.cookies.getProperty("deleteCount", "0");
                    int deleteCount = Integer.parseInt(deleteCountStr);
                    deleteCount++;
                    response.addCookie("deleteCount=" + deleteCount + "; Path=/");
                } catch (NumberFormatException nfe) {
                    // If parsing fails, just set to 1
                    logger.warn("Failed to parse deleteCount cookie, setting to 1");
                    response.addCookie("deleteCount=1; Path=/");
                }
            } catch (Exception ex) {
                response.setCode(ReplyCode.NOTDEFINED);
                response.setText("{\"success\":false,\"message\":\""+ex.getMessage()+"\"}");
                logger.error("Error deleting group: {}", ex.getMessage(), ex);
            }
        }
        
        String body = response.text;
        response.setHeader("Content-Type", "application/json");
        response.setHeader("Content-Length", String.valueOf(body.getBytes().length));
    }
}