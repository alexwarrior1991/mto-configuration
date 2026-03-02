package com.alejandro.mtoconfiguration.controller.commons;

import com.alejandro.mtoconfiguration.core.exception.BaseException;

import java.util.List;

@FunctionalInterface
public interface ThrowingListFunction<T, R> {

    List<R> apply(List<T> t) throws BaseException;

}
