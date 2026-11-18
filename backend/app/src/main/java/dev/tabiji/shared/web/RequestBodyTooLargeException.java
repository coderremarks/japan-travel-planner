package dev.tabiji.shared.web;

import java.io.IOException;

/** Thrown while reading a request body that exceeds the configured limit. */
public class RequestBodyTooLargeException extends IOException {

    public RequestBodyTooLargeException(long limitBytes) {
        super("request body exceeds " + limitBytes + " bytes");
    }
}
