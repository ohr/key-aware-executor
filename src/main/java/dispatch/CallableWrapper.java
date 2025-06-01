package dispatch;

import lombok.SneakyThrows;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

class CallableWrapper<U> implements Supplier<U> {

    private final Callable<U> callable;

    private CallableWrapper(Callable<U> callable) {
        this.callable = callable;
    }

    static <V> Supplier<V> wrap(Callable<V> callable) {
        return new CallableWrapper<>(callable);
    }

    @SneakyThrows
    @Override
    public U get() {
        return callable.call();
    }
}
