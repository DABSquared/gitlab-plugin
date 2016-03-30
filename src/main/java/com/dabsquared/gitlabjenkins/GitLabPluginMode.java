/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.dabsquared.gitlabjenkins;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pablo Bendersky
 */
public enum GitLabPluginMode {
    LEGACY(GitLabPluginStrategyLegacy.class),
    MODERN(GitLabPluginStrategyModern.class);
    
    private final Class<? extends GitLabPluginStrategy> pushTriggerStrategyClass;
    
    GitLabPluginMode(Class<? extends GitLabPluginStrategy> pushTriggerStrategyClass) {
        this.pushTriggerStrategyClass = pushTriggerStrategyClass;
    }
    
    public GitLabPluginStrategy getGitLabPluginStrategy() {
        try {
            return this.pushTriggerStrategyClass.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(GitLabPluginMode.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return null;
    }
}
