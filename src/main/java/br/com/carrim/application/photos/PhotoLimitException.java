package br.com.carrim.application.photos;

public final class PhotoLimitException extends RuntimeException {
    public PhotoLimitException() {
        super("Photo limit reached");
    }
}
