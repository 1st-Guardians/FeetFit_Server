package com.feetfit.server.apiPayload.exception.handler;

import com.feetfit.server.apiPayload.code.BaseErrorCode;
import com.feetfit.server.apiPayload.exception.GeneralException;

public class DeviceHandler extends GeneralException {
    public DeviceHandler(BaseErrorCode errorCode) {
        super(errorCode);
    }

    public DeviceHandler(BaseErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public DeviceHandler(BaseErrorCode errorCode, Object result) {
        super(errorCode, result);
    }

    public DeviceHandler(BaseErrorCode errorCode, String customMessage, Object result) {
        super(errorCode, customMessage, result);
    }
}
