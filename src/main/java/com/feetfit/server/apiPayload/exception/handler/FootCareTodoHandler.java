package com.feetfit.server.apiPayload.exception.handler;

import com.feetfit.server.apiPayload.code.BaseErrorCode;
import com.feetfit.server.apiPayload.exception.GeneralException;

public class FootCareTodoHandler extends GeneralException {
    public FootCareTodoHandler(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
