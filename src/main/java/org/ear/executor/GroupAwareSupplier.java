package org.ear.executor;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Base interface for a group-aware {@link Supplier}.
 *
 * @param <U> result type of the supplier
 */
public interface GroupAwareSupplier<U> extends GroupAwareTask<U>, Supplier<U> {

    @Override
    default U execute() {
        return get();
    }

    /**
     * Creates a group-aware supplier from a plain {@link Supplier}.
     *
     * @param group non-null group to which the task belongs
     * @param supplier the supplier to be executed
     * @param <U> result type of the supplier
     * @return a supplier that belongs to the given group
     * @throws NullPointerException if group or supplier is null
     */
    static <U> GroupAwareSupplier<U> of(Object group, Supplier<U> supplier) {
        Objects.requireNonNull(group, "Group must not be null");
        Objects.requireNonNull(supplier, "Supplier must not be null");
        return new GroupAwareSupplier<>() {
            @Override
            public U get() {
                return supplier.get();
            }

            @Override
            public Object getGroup() {
                return group;
            }
        };
    }
}
