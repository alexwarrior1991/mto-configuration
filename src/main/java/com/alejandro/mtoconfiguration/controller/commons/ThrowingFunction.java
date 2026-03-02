package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.core.exception.BaseException;

@FunctionalInterface
public interface ThrowingFunction<T, R> {

    R apply(T t) throws BaseException;

}
