package io.testfly.mobile.internal.rpc;

import java.util.OptionalInt;

/** Sanitized internal transport failure; daemon messages, bodies and headers are never retained. */
public final class JsonRpcException extends RuntimeException {
    public enum Kind { TRANSPORT, PROTOCOL, RPC, DEADLINE, INTERRUPTED }

    private final Kind kind;
    private final Integer rpcCode;

    JsonRpcException(Kind kind, String message) {
        this(kind, message, null);
    }

    JsonRpcException(Kind kind, String message, Integer rpcCode) {
        super(message);
        this.kind = kind;
        this.rpcCode = rpcCode;
    }

    public Kind kind() {
        return kind;
    }

    public OptionalInt rpcCode() {
        return rpcCode == null ? OptionalInt.empty() : OptionalInt.of(rpcCode);
    }
}
