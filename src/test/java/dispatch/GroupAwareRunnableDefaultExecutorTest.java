package dispatch;

class GroupAwareRunnableDefaultExecutorTest extends AbstractGroupAwareRunnableTest {

    @Override
    protected AbstractGroupAwareExecutor<Void, Runnable, GroupAwareRunnable> newExecutor() {
        return new GroupAwareRunnableExecutor();
    }

}
