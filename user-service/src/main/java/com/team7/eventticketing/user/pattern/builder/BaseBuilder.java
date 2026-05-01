package com.team7.eventticketing.user.pattern.builder;

/**
 * BaseBuilder — Builder base pattern structure (S1-P6).
 *
 * All DTO Builders in the user-service extend this abstract class.
 * It enforces the contract: every Builder must implement build().
 *
 * @param <T> the DTO type this builder produces
 * @param <B> the concrete Builder type (for fluent self-return)
 */
public abstract class BaseBuilder<T, B extends BaseBuilder<T, B>> {

    /**
     * Build and return the final DTO instance.
     * Every concrete Builder must implement this.
     */
    public abstract T build();

    /**
     * Returns the concrete builder instance (this).
     * Allows fluent chaining in subclasses without unchecked casts.
     */
    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }
}