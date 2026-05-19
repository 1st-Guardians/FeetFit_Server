package com.feetfit.server.apiPayload.exception.handler;

import com.feetfit.server.apiPayload.code.BaseErrorCode;
import com.feetfit.server.apiPayload.exception.GeneralException;

public class DeviceHandler extends GeneralException {
    public DeviceHandler(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
