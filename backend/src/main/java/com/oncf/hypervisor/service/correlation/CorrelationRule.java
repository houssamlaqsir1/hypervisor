package com.oncf.hypervisor.service.correlation;

import java.util.List;

/**
 * Contract for a correlation rule. Implementations inspect the
 * {@link CorrelationContext} and emit zero or more {@link AlertDraft}s.
 * Rules must be stateless and side-effect free.
 */
public interface CorrelationRule {
    List<AlertDraft> evaluate(CorrelationContext ctx);
}
