package com.anssa8bu.ssafy.common.exception;

import com.anssa8bu.ssafy.common.response.ErrorResponse;
import com.anssa8bu.ssafy.device.exception.DeviceAlreadyRegisteredException;
import com.anssa8bu.ssafy.device.exception.DeviceNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DeviceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleDeviceNotFound(DeviceNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("DEVICE_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(DeviceAlreadyRegisteredException.class)
  public ResponseEntity<ErrorResponse> handleDeviceAlreadyRegistered(
      DeviceAlreadyRegisteredException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("DEVICE_ALREADY_REGISTERED", exception.getMessage()));
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse("INVALID_CREDENTIALS", exception.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .distinct()
            .collect(Collectors.joining(", "));
    return ResponseEntity.status(422).body(new ErrorResponse("VALIDATION_ERROR", message));
  }
}
