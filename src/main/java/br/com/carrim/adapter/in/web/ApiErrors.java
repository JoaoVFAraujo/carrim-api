package br.com.carrim.adapter.in.web;

import br.com.carrim.application.catalog.CatalogConflictException;
import br.com.carrim.application.identity.IdentityProofException;
import br.com.carrim.application.shopping.ReplayConflictException;
import br.com.carrim.application.shopping.ShoppingConflictException;
import java.util.NoSuchElementException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message) {}

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class
    })
    ResponseEntity<ErrorBody> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST", "Dados inválidos."));
    }

    @ExceptionHandler(IdentityProofException.class)
    ResponseEntity<ErrorBody> proof(IdentityProofException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorBody("INVALID_INSTALLATION_PROOF", "Prova da instalação inválida."));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ErrorBody> missing(NoSuchElementException ignored) {
        return ResponseEntity.status(404).body(new ErrorBody("NOT_FOUND", "Recurso não encontrado."));
    }

    @ExceptionHandler({CatalogConflictException.class, ShoppingConflictException.class})
    ResponseEntity<ErrorBody> version(RuntimeException ignored) {
        return ResponseEntity.status(409).body(new ErrorBody("VERSION_CONFLICT", "A versão foi alterada."));
    }

    @ExceptionHandler(ReplayConflictException.class)
    ResponseEntity<ErrorBody> replay(ReplayConflictException ignored) {
        return ResponseEntity.status(409)
                .body(new ErrorBody("IDEMPOTENCY_CONFLICT", "Chave reutilizada com outro pedido."));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ErrorBody> state(IllegalStateException ignored) {
        return ResponseEntity.status(409)
                .body(new ErrorBody("INVALID_STATE", "A operação não é permitida neste estado."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict(DataIntegrityViolationException ignored) {
        return ResponseEntity.status(409).body(new ErrorBody("DATA_CONFLICT", "Conflito de dados."));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorBody> unavailable(DataAccessException ignored) {
        return ResponseEntity.status(503).body(new ErrorBody("DATABASE_UNAVAILABLE", "Persistência indisponível."));
    }
}
