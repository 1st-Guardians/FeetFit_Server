package com.feetfit.server.apiPayload.exception;

import com.feetfit.server.apiPayload.code.BaseErrorCode;
import com.feetfit.server.apiPayload.code.ErrorReasonDTO;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode code;
    private final String customMessage;
    private final Object result;

    public GeneralException(BaseErrorCode code) {
        this(code, null, null);
    }

    public GeneralException(BaseErrorCode code, String customMessage) {
        this(code, customMessage, null);
    }

    public GeneralException(BaseErrorCode code, Object result) {
        this(code, null, result);
    }

    public GeneralException(BaseErrorCode code, String customMessage, Object result) {
        this.code = code;
        this.customMessage = customMessage;
        this.result = result;
    }

    public ErrorReasonDTO getErrorReason() {
        return overrideMessage(this.code.getReason());
    }

    public ErrorReasonDTO getErrorReasonHttpStatus() {
        return overrideMessage(this.code.getReasonHttpStatus());
    }

    private ErrorReasonDTO overrideMessage(ErrorReasonDTO reason) {
        if (customMessage == null) {
            return reason;
        }

        return ErrorReasonDTO.builder()
                .httpStatus(reason.getHttpStatus())
                .isSuccess(reason.getIsSuccess())
                .code(reason.getCode())
                .message(customMessage)
                .build();
    }
}
