package dispatch;

/**
 * Base interface for a group-aware {@link Runnable}.
 */
public interface GroupAwareRunnable extends GroupAwareTask<Void>, Runnable {

    @Override
    default Void execute() {
        run();
        return null;
    }

}

