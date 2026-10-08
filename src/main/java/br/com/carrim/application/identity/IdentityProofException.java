package br.com.carrim.application.identity;

public class IdentityProofException extends RuntimeException {
    public IdentityProofException() {
        super("Installation proof is invalid");
    }
}
