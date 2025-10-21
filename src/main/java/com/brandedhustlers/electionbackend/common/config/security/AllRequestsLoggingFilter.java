package com.brandedhustlers.electionbackend.common.config.security;

import com.brandedhustlers.electionbackend.auth.domain.RequestLog;
import com.brandedhustlers.electionbackend.common.config.persistence.AllRequestLoggingRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class AllRequestsLoggingFilter implements Filter {

    private final AllRequestLoggingRepository loggingRepository;
    private final Logger logger = LoggerFactory.getLogger(AllRequestsLoggingFilter.class);

    public AllRequestsLoggingFilter(AllRequestLoggingRepository loggingRepository) {
        this.loggingRepository = loggingRepository;
    }

    /**
     * @param filterConfig
     * @throws ServletException
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    /**
     * @param request
     * @param response
     * @param chain
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Wrap the response to capture the response body
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

        // Log request data
        logRequest(httpRequest);

        // Proceed with the request and response
        chain.doFilter(request, responseWrapper);

//        // Log response data
        logResponse(responseWrapper);

        // Ensure response is written back to the client
        responseWrapper.copyBodyToResponse();
    }

    /**
     *
     */
    @Override
    public void destroy() {
        Filter.super.destroy();
    }

    // Log request information
    private void logRequest(HttpServletRequest request) {
        String userId = getAuthenticatedUser();  // Extract from headers, session, or context
        String menuId = request.getHeader("MENU_ID");  // Similarly from headers or request
        String requestUrl = request.getRequestURI();
        String ipAddr = request.getRemoteAddr();
        LocalDateTime accessDt = LocalDateTime.now();
        String searchConditionCn = request.getParameter("SEARCH_CONDITION_CN");  // Query parameter
        String firstRegisterId = getAuthenticatedUser();  // Can be from session or context
        String lastUpdateId = getAuthenticatedUser();  // Can be from session or context


        // Save the log to the database
        saveLog(userId, menuId, requestUrl, ipAddr, accessDt, searchConditionCn, firstRegisterId, lastUpdateId);
    }

    //Log response information
    private void logResponse(ContentCachingResponseWrapper responseWrapper) {
        String responseBody = new String(responseWrapper.getContentAsByteArray());
        int status = responseWrapper.getStatus();

        // Check if the responseBody exceeds 255 characters
        if (responseBody.length() > 255) {
            // Replace the responseBody with "success"
            responseBody = "success";
        }

        // Split the response body into words (if it hasn't been replaced with "success")
        String[] words = responseBody.split("\\s+");

        // Limit the number of words to 100
        StringBuilder limitedResponse = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, 100); i++) {
            limitedResponse.append(words[i]).append(" ");
        }

        // Trim the final string to remove any trailing space
        String limitedResponseBody = limitedResponse.toString().trim();

        // Update or create a log entry with the status and the limited response data
        saveLogWithResponse(status, limitedResponseBody);
    }


    private void saveLog(String userId, String menuId, String requestUrl, String ipAddr, LocalDateTime accessDt,
                         String searchConditionCn, String firstRegisterId, String lastUpdateId) {
        RequestLog log = new RequestLog();
        log.setUserId(userId);
        log.setMenuId(menuId);
        log.setRequestUrl(requestUrl);
        log.setIpAddr(ipAddr);
        log.setAccessDt(accessDt);
        log.setSearchConditionCn(searchConditionCn);
        log.setFirstRegisterId(firstRegisterId);
        log.setLastUpdateId(lastUpdateId);
        logger.info(log.toString());
        loggingRepository.save(log);
    }

    private void saveLogWithResponse(int status, String responseBody) {
        // Get the latest log entry, updateStation it with response data
        RequestLog log = loggingRepository.findTopByOrderByIdDesc();
        if (log != null) {
            log.setResponseStatus(status);
            log.setResponseBody(responseBody);
            loggingRepository.save(log);
        }
    }


    private String getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            return "anonymousUser";
        }
        return authentication.getName();
    }
}
