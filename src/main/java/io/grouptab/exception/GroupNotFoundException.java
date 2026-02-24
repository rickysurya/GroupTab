package io.grouptab.exception;

//TODO
public class GroupNotFoundException extends RuntimeException {
    public GroupNotFoundException(Long id) {
        super("Group not found with id: " + id);
    }
}