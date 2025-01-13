package dispatch;

/**
 * A task that can be executed to produce a result of type U and knows
 * about a group of task it belongs to
 *
 * @param <U>
 */
public interface GroupAwareTask<U> {

    Object getGroup();

    U execute();

}
