package dispatch;

import lombok.SneakyThrows;

import java.util.concurrent.Callable;

/**
 * Base interface for a group-aware {@link Callable}.
 *
 * @param <U> result type of the callable
 */
public interface GroupAwareCallable<U> extends GroupAwareTask<U>, Callable<U> {

    @SneakyThrows
    @Override
    default U execute() {
        return call();
    }

}
