package com.forgeshift.wso2discovery.domain;

/**
 * Lifecycle states of a discovery job.
 */
public enum DiscoveryState {

    /** The job has been accepted and is waiting for the worker. */
    PENDING,

    /** Acquiring an OAuth2 token from WSO2. */
    AUTHENTICATING,

    /** Listing APIs and other top-level resources. */
    LISTING,

    /** Downloading individual artifacts and streaming them to GCS. */
    DOWNLOADING,

    /** Writing the manifest and finalizing the discovery. */
    FINALIZING,

    /** Successfully completed. All artifacts are persisted in GCS. */
    COMPLETED,

    /** Terminal failure. See lastError on the job document. */
    FAILED,

    /** Operator cancelled. */
    CANCELLED
}
