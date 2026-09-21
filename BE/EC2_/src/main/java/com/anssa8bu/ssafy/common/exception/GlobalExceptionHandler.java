package com.anssa8bu.ssafy.common.exception;

import com.anssa8bu.ssafy.auth.security.SessionStoreUnavailableException;
import com.anssa8bu.ssafy.common.response.ErrorResponse;
import com.anssa8bu.ssafy.device.exception.DeviceAlreadyRegisteredException;
import com.anssa8bu.ssafy.device.exception.DeviceNotFoundException;
import com.anssa8bu.ssafy.deviceauth.exception.InvalidDeviceCredentialsException;
import com.anssa8bu.ssafy.deviceenrollment.exception.DeviceEnrollmentException;
import com.anssa8bu.ssafy.episodeupload.exception.EpisodeUploadException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(SessionStoreUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleSessionUnavailable(
      SessionStoreUnavailableException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("AUTH_SESSION_UNAVAILABLE", exception.getMessage()));
  }

  @ExceptionHandler(DeviceEnrollmentException.class)
  public ResponseEntity<ErrorResponse> handleDeviceEnrollment(DeviceEnrollmentException exception) {
    return ResponseEntity.status(exception.getStatus())
        .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(EpisodeUploadException.class)
  public ResponseEntity<ErrorResponse> handleEpisodeUpload(EpisodeUploadException exception) {
    return ResponseEntity.status(exception.getStatus())
        .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("RESOURCE_NOT_FOUND", exception.getMessage()));
  }

  @ExceptionHandler(BusinessConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(BusinessConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
  }

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

  @ExceptionHandler(InvalidDeviceCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidDeviceCredentials(
      InvalidDeviceCredentialsException exception) {
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
