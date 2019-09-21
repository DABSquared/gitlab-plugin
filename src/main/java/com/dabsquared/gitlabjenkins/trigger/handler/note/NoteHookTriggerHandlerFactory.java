package com.dabsquared.gitlabjenkins.trigger.handler.note;

/**
 * @author Nikolay Ustinov
 */
public final class NoteHookTriggerHandlerFactory {

    private NoteHookTriggerHandlerFactory() {}

    public static NoteHookTriggerHandler newNoteHookTriggerHandler(boolean triggerOnNoteRequest, String noteRegex, String userRegex) {
        if (triggerOnNoteRequest) {
            return new NoteHookTriggerHandlerImpl(noteRegex, userRegex);
        } else {
            return new NopNoteHookTriggerHandler();
        }
    }
}
