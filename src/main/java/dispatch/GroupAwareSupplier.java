package dispatch;

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
}
