package com.feetfit.server.apiPayload.exception.handler;

import com.feetfit.server.apiPayload.code.BaseErrorCode;
import com.feetfit.server.apiPayload.exception.GeneralException;

public class ShoeHandler extends GeneralException {
    public ShoeHandler(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
