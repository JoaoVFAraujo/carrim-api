package br.com.carrim.adapter.in.web;

final class ApiInputs {
    private ApiInputs() {}

    static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 1000000)
            throw new IllegalArgumentException("Invalid page");
    }

    static void barcode(String code) {
        if (!code.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13})")) throw new IllegalArgumentException("Invalid barcode");
    }
}
