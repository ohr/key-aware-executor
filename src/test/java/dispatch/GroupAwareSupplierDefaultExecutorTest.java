package dispatch;

import java.util.function.Supplier;

class GroupAwareSupplierDefaultExecutorTest extends AbstractGroupAwareSupplierTest {

    @Override
    protected AbstractGroupAwareExecutor<Result, Supplier<Result>, GroupAwareSupplier<Result>> newExecutor() {
        return new GroupAwareSupplierExecutor<>();
    }

}
