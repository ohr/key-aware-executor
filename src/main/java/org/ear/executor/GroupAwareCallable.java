package org.ear.executor;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Base interface for a group-aware {@link Callable}.
 *
 * @param <U> result type of the callable
 */
public interface GroupAwareCallable<U> extends GroupAwareTask<U>, Callable<U> {

    @Override
    default U execute() throws Exception {
        return call();
    }

    /**
     * Creates a group-aware callable from a plain {@link Callable}.
     *
     * @param group non-null group to which the task belongs
     * @param callable the callable to be executed
     * @param <U> result type of the callable
     * @return a callable that belongs to the given group
     * @throws NullPointerException if group or callable is null
     */
    static <U> GroupAwareCallable<U> of(Object group, Callable<U> callable) {
        Objects.requireNonNull(group, "Group must not be null");
        Objects.requireNonNull(callable, "Callable must not be null");
        return new GroupAwareCallable<>() {
            @Override
            public U call() throws Exception {
                return callable.call();
            }

            @Override
            public Object getGroup() {
                return group;
            }
        };
    }

}
