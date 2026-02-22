package com.alejandro.mtoconfiguration.service.commons;

import java.io.Serializable;
import java.util.function.LongFunction;

@FunctionalInterface
public interface SerializableLongFunction<R> extends LongFunction<R>, Serializable {
}
