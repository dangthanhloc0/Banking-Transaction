package com.ldang03.common.http;

import com.ldang03.common.exception.ExistException;
import com.ldang03.common.exception.Forbidden;
import com.ldang03.common.exception.NotFoundException;
import com.ldang03.common.exception.Unauthorized;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ErrorHandler extends DefaultResponseErrorHandler {

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());

        if (status == null) {
            super.handleError(response);
            return;
        }

        switch (status) {
            case NOT_FOUND -> throw new NotFoundException(readBody(response));
            case UNAUTHORIZED -> throw new Unauthorized(readBody(response));
            case FORBIDDEN -> throw new Forbidden(readBody(response));
            case CONFLICT -> throw new ExistException(readBody(response));
            default -> super.handleError(response);
        }
    }

    private String readBody(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }
}
