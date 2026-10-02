package com.intechcore.polarion.extension.github.service;

import java.util.function.Supplier;

/**
 * Runs an action in a Polarion write transaction.
 */
@FunctionalInterface
public interface WriteTransaction {

    <T> T execute(Supplier<T> action);
}
