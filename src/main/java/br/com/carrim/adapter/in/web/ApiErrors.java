package br.com.carrim.adapter.in.web;

import br.com.carrim.application.identity.IdentityProofException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message) {}

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class
    })
    ResponseEntity<ErrorBody> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST", "Dados inválidos."));
    }

    @ExceptionHandler(IdentityProofException.class)
    ResponseEntity<ErrorBody> proof(IdentityProofException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorBody("INVALID_INSTALLATION_PROOF", "Prova da instalação inválida."));
    }
}
