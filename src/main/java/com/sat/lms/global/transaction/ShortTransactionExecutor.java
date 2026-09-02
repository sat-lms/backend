package com.sat.lms.global.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class ShortTransactionExecutor {
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public ShortTransactionExecutor(PlatformTransactionManager manager) {
        read = template(manager, true);
        write = template(manager, false);
    }

    public void requireNonTransactionalEntry() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("S3 orchestration must start without an active transaction");
        }
    }

    public <T> T read(Supplier<T> work) {
        return read.execute(status -> work.get());
    }

    public <T> T write(Supplier<T> work) {
        return write.execute(status -> work.get());
    }

    public <T> T writeWithRollbackConfirmation(Supplier<T> work) {
        return write.execute(status -> {
            try {
                return work.get();
            } catch (RuntimeException exception) {
                throw new ConfirmedRollbackException(exception);
            }
        });
    }

    public void write(Runnable work) {
        write.executeWithoutResult(status -> work.run());
    }

    private static TransactionTemplate template(PlatformTransactionManager manager, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(readOnly);
        return template;
    }

    public static final class ConfirmedRollbackException extends RuntimeException {
        public ConfirmedRollbackException(RuntimeException cause) {
            super(cause);
        }

        public RuntimeException originalException() {
            return (RuntimeException) getCause();
        }
    }
}
