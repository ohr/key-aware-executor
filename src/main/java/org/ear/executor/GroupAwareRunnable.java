package org.ear.executor;

import java.util.Objects;

/**
 * Base interface for a group-aware {@link Runnable}.
 */
public interface GroupAwareRunnable extends GroupAwareTask<Void>, Runnable {

    @Override
    default Void execute() {
        run();
        return null;
    }

    /**
     * Creates a group-aware runnable from a plain {@link Runnable}.
     *
     * @param group non-null group to which the task belongs
     * @param runnable the runnable to be executed
     * @return a runnable that belongs to the given group
     * @throws NullPointerException if group or runnable is null
     */
    static GroupAwareRunnable of(Object group, Runnable runnable) {
        Objects.requireNonNull(group, "Group must not be null");
        Objects.requireNonNull(runnable, "Runnable must not be null");
        return new GroupAwareRunnable() {
            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public Object getGroup() {
                return group;
            }
        };
    }

}
