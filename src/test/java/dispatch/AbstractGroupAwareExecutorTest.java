package dispatch;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

abstract class AbstractGroupAwareExecutorTest<U, T, G extends GroupAwareTask<U>> {

    protected static final List<Result> results = new CopyOnWriteArrayList<>();
    protected static final AtomicInteger counter = new AtomicInteger(0);
    protected static final Random random = new SecureRandom();

    protected static final int KEY_RANGE = 100;
    protected static final int MAX_WAIT = 10;
    protected static final int LOOPS = 1000;

    protected abstract AbstractGroupAwareExecutor<U, T, G> newExecutor();

    protected abstract void produceResult(AbstractGroupAwareExecutor<U, T, G> executor);

    @BeforeEach
    public void before() {
        results.clear();
        counter.set(0);
    }

    @Test
    public void singleThreadedProducer() {
        var executor = newExecutor();
        produceResult(executor);
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
            .until(() -> results, hasSize(LOOPS));

        assertThat(executor.getGroupMapSize(), equalTo(0));
        // Check that tasks with the same group have increasing counter values
        var map = new HashMap<Integer, Integer>();
        // results.forEach(System.out::println);
        results.forEach(r ->
            map.merge(r.group(), r.counter(), (current, next) -> {
                assertThat(next, greaterThan(current));
                return next;
            })
        );
    }

}
