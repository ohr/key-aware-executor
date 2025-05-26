package dispatch;

/**
 * Base interface for a task that can be executed to produce a result of type
 * U and knows about a group of tasks it belongs to
 *
 * @param <U> result type
 */
public interface GroupAwareTask<U> {

    /**
     * Returns the group to which the task belongs to
     *
     * @return the group to which the task belongs to
     */
    Object getGroup();

    /**
     * Execute the task
     *
     * @return return value of the task
     */
    U execute();

}
