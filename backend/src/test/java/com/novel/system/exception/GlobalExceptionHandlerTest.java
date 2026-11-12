package com.novel.system.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingEndpoint_returns404InsteadOf500() throws Exception {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "/novels");
        var resp = handler.handleNoResourceFound(e);
        assertEquals(404, resp.getCode());

        ResponseStatus status = GlobalExceptionHandler.class
                .getMethod("handleNoResourceFound", NoResourceFoundException.class)
                .getAnnotation(ResponseStatus.class);
        assertEquals(HttpStatus.NOT_FOUND, status.value());
    }

    @Test
    void businessException_usesItsOwnCode() {
        var resp = handler.handleBusinessException(new BusinessException(403, "无权访问"));
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals(403, resp.getBody().getCode());
    }
}
