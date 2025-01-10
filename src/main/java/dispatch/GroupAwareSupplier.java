package dispatch;

import java.util.function.Supplier;

public interface GroupAwareSupplier<T> extends GroupAware, Supplier<T> {
}
