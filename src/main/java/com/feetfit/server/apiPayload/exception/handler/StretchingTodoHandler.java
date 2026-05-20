package com.feetfit.server.apiPayload.exception.handler;

import com.feetfit.server.apiPayload.code.BaseErrorCode;
import com.feetfit.server.apiPayload.exception.GeneralException;

public class StretchingTodoHandler extends GeneralException {
    public StretchingTodoHandler(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
