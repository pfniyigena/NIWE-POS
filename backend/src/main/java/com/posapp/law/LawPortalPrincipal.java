package com.posapp.law;

/** Who is calling a client portal endpoint. Never a firm user: it has no staff authority at all. */
public record LawPortalPrincipal(Long accountId, Long customerId, String name, String email) {
}
