package com.banu.productservice.exception;

/**
 * A write arrived with no {@code If-Match} header. Rejecting it is the whole point of the
 * scheme: an unconditional PUT is exactly the blind overwrite optimistic locking exists to
 * prevent, so "no header" cannot be allowed to mean "overwrite whatever is there".
 *
 * <p>{@link ApiExceptionHandler} maps it to 428 Precondition Required, the status RFC 6585
 * defines for precisely this - telling a client its request was refused for being
 * unconditional, so it can re-send with the precondition.
 */
public class IfMatchRequiredException extends RuntimeException {

    public IfMatchRequiredException() {
        super("This request must carry an If-Match header with the ETag from a prior GET");
    }
}
