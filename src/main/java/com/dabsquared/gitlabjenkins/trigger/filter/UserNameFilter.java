package com.dabsquared.gitlabjenkins.trigger.filter;

/**
 * @author Robin Müller
 */
public interface UserNameFilter {

    boolean isUserNameAllowed(String userName);
}
