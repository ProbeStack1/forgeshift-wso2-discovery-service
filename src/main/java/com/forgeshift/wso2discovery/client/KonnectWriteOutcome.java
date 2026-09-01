package com.forgeshift.wso2discovery.client;

/**
 * Distinguishes a write this service performed from one that was already in
 * place, so a re-run reports nothing changed instead of claiming fresh work.
 */
public enum KonnectWriteOutcome {
    CREATED,
    ALREADY_EXISTS
}
