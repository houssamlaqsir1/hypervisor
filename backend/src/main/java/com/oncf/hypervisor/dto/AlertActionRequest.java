package com.oncf.hypervisor.dto;

/**
 * Body for the operator lifecycle actions (acknowledge / resolve). Both
 * fields are optional: {@code operator} defaults to a placeholder when
 * omitted (auth isn't wired yet), and {@code note} is only meaningful when
 * resolving.
 */
public record AlertActionRequest(
        String operator,
        String note
) {}
