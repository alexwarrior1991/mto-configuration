package com.alejandro.mtoconfiguration.utils;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

public class WindowIterator<T> implements Iterator<List<T>> {

    private final Function<T, List<T>> nextWindowFunction;
    private List<T> currentWindow;
    private T lastElement;
    private boolean hasMore = true;

    public WindowIterator(Function<T, List<T>> nextWindowFunction) {
        this.nextWindowFunction = nextWindowFunction;
        this.currentWindow = nextWindowFunction.apply(null);// Carga inicial
        updateState();
    }

    private void updateState() {
        if (currentWindow != null && !currentWindow.isEmpty()) {
            lastElement = currentWindow.getLast();
        } else {
            hasMore = false;
        }
    }

    @Override
    public boolean hasNext() {
        return hasMore;
    }

    @Override
    public List<T> next() {
        if (!hasNext()) throw new NoSuchElementException();
        List<T> window = currentWindow;
        currentWindow = nextWindowFunction.apply(lastElement);
        updateState();
        return window;
    }
}
