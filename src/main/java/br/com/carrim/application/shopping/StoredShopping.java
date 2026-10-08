package br.com.carrim.application.shopping;

import br.com.carrim.domain.shopping.ShoppingSession;

public record StoredShopping(ShoppingSession value, long version) {}
