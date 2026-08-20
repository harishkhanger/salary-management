package com.acme.salary.service.validation;

import java.util.Optional;

/**
 * Validator pipeline (pragmatic chain-of-responsibility): validators run in
 * @Order sequence; the first one returning a reason parks the change in the
 * review queue instead of applying it. Empty = pass to the next validator.
 */
public interface RaiseValidator {

    Optional<String> validate(RaiseContext context);
}
