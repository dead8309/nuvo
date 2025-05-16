package xyz.dead8309.nuvo.core.model

/**
 * Authorization Status
 */
enum class AuthStatus {
    /**
     * Initial State
     */
    NOT_CHECKED,

    /**
     * MCP server requires auth, but user is not authorized
     */
    REQUIRED_NOT_AUTHORIZED,

    /**
     * MCP server doesn't need auth
     */
    NOT_REQUIRED,

    /**
     * MCP server requires auth, currently performing metadata discovery
     */
    REQUIRED_DISCOVERY,

    /**
     * MCP server requires auth, currently performing dynamic client registration
     */
    REQUIRED_REGISTRATION,

    /**
     * MCP server requires auth, waiting for user to authorize using browser
     */
    REQUIRED_USER_ACTION,

    /**
     * User sent to browser, waiting for callback
     */
    REQUIRED_AWAITING_CALLBACK,

    /**
     * Callback received, exchanging code for token
     */
    REQUIRED_TOKEN_EXCHANGE,

    /**
     * Successfully authorized, process valid (or refreshable) token
     */
    AUTHORIZED,

    /**
     * An error occurred during the authorization process
     */
    ERROR
}