package dispatch;

import java.util.concurrent.Executors;
import java.util.function.Supplier;

class GroupAwareSupplierVirtualExecutorTest extends AbstractGroupAwareSupplierTest {

    @Override
    protected AbstractGroupAwareExecutor<Result, Supplier<Result>, GroupAwareSupplier<Result>> newExecutor() {
        return new GroupAwareSupplierExecutor<>(Executors.newVirtualThreadPerTaskExecutor());
    }


}
