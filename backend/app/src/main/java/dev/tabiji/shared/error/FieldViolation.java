package dev.tabiji.shared.error;

/**
 * One invalid field. {@code code} is a constraint name such as {@code NotBlank} or a domain rule such as
 * {@code END_BEFORE_START}. Rejected values are deliberately never echoed back.
 */
public record FieldViolation(String field, String code) {}
