package com.baeldung.security;

import static com.baeldung.web.util.Constats.APPROVE_PRIVILEGE;
import static com.baeldung.web.util.Constats.WRITE_PRIVILEGE;
import static com.baeldung.web.util.Constats.READ_PRIVILEGE;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.baeldung.persistence.model.User;
import com.baeldung.service.DeviceService;

@Component("myAuthenticationSuccessHandler")
public class MySimpleUrlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	private final Logger logger = LoggerFactory.getLogger(getClass());

    private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Autowired
    ActiveUserStore activeUserStore;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private Environment env;

    @Override
    public void onAuthenticationSuccess(final HttpServletRequest request, final HttpServletResponse response, final Authentication authentication) throws IOException {
        handle(request, response, authentication);
        final HttpSession session = request.getSession(false);
        if (session != null) {
            session.setMaxInactiveInterval(30 * 60);

            String username;
            if (authentication.getPrincipal() instanceof User) {
            	username = ((User)authentication.getPrincipal()).getEmail();
            }
            else {
            	username = authentication.getName();
            }
            LoggedUser user = new LoggedUser(username, activeUserStore);
            session.setAttribute("user", user);
        }
        clearAuthenticationAttributes(request);

        loginNotification(authentication, request);
    }

    private void loginNotification(Authentication authentication, HttpServletRequest request) {
        try {
            if (authentication.getPrincipal() instanceof User && isGeoIpLibEnabled()) {
                deviceService.verifyDevice(((User)authentication.getPrincipal()), request);
            }
        } catch (Exception e) {
            logger.error("An error occurred while verifying device or location", e);
            throw new RuntimeException(e);
        }

    }

    protected void handle(final HttpServletRequest request, final HttpServletResponse response, final Authentication authentication) throws IOException {
        final String targetUrl = determineTargetUrl(authentication);

        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }
        redirectStrategy.sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(final Authentication authentication) {
        boolean isUser = false;
        boolean isAdmin = false;
        boolean isManager = false;
        final Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (final GrantedAuthority grantedAuthority : authorities) {
            if (READ_PRIVILEGE.equals(grantedAuthority.getAuthority())) {
                isUser = true;
            } else if (WRITE_PRIVILEGE.equals(grantedAuthority.getAuthority())) {
                isAdmin = true;
                isUser = false;
                break;
            } else if (APPROVE_PRIVILEGE.equals(grantedAuthority.getAuthority())) {
            	isUser = false;
            	isManager = true;
            	break;
            }
        }
        if (isUser) {
        	 String username;
             if (authentication.getPrincipal() instanceof User) {
             	username = ((User)authentication.getPrincipal()).getEmail();
             }
             else {
             	username = authentication.getName();
             }
             return "/homepage.html?user="+username;
        } else if (isAdmin) {
            return "/console";
        } else if (isManager) {
            return "/management";
        } else {
            throw new IllegalStateException();
        }
    }

    protected void clearAuthenticationAttributes(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
    }

    public void setRedirectStrategy(final RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
    }

    protected RedirectStrategy getRedirectStrategy() {
        return redirectStrategy;
    }

    private boolean isGeoIpLibEnabled() {
        return Boolean.parseBoolean(env.getProperty("geo.ip.lib.enabled"));
    }
}