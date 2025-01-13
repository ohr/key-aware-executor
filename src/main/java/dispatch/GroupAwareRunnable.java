package dispatch;

/**
 * A group-aware Runnable
 */
public interface GroupAwareRunnable extends GroupAwareTask<Void>, Runnable {

    @Override
    default Void execute() {
        run();
        return null;
    }

}

