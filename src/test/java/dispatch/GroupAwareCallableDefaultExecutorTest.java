package dispatch;

import java.util.concurrent.Callable;

class GroupAwareCallableDefaultExecutorTest extends AbstractGroupAwareCallableTest {

    @Override
    protected AbstractGroupAwareExecutor<Result, Callable<Result>, GroupAwareCallable<Result>> newExecutor() {
        return new GroupAwareCallableExecutor<>();
    }

}
