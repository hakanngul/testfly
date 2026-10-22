package io.testfly.mobile;

/**
 * Any other mobilecli failure: a JSON-RPC error, or a transport failure such as a connection the
 * server closed without answering.
 */
public class MobileRpcException extends MobileException {

    private final Integer code;
    private final String data;

    public MobileRpcException(String message, Integer code, String data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public MobileRpcException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
        this.data = null;
    }

    /** JSON-RPC error code, or {@code null} for a transport failure. */
    public Integer getCode() {
        return code;
    }

    /** JSON-RPC {@code error.data} as text, or {@code null}. */
    public String getData() {
        return data;
    }
}
