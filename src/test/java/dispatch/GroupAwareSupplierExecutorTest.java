package dispatch;

import java.util.concurrent.Executors;
import java.util.function.Supplier;

class GroupAwareSupplierExecutorTest extends AbstractGroupAwareSupplierTest {

    @Override
    protected AbstractGroupAwareExecutor<Result, Supplier<Result>, GroupAwareSupplier<Result>> newExecutor() {
        return new GroupAwareSupplierExecutor<>(Executors.newFixedThreadPool(5));
    }


}
