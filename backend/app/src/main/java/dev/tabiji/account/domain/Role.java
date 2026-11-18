package dev.tabiji.account.domain;

/** Coarse roles. Ownership, not role, protects itineraries; ADMIN only unlocks operational endpoints. */
public enum Role {
    USER,
    ADMIN
}
