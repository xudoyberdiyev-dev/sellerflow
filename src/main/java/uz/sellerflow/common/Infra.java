package uz.sellerflow.common;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

/** M-10: vaqtinchalik infratuzilma xatosi (baza uzilgan va h.k.) — update qayta ishlanishi kerak, tashlab yuborilmaydi. */
public final class Infra {
    private Infra() {}

    public static boolean isTransient(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TransientDataAccessException || t instanceof RecoverableDataAccessException
                    || t instanceof DataAccessResourceFailureException || t instanceof CannotCreateTransactionException
                    || t instanceof SQLTransientException || t instanceof SQLRecoverableException
                    || t instanceof java.net.ConnectException) {
                return true;
            }
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("57P"))) {
                return true; // connection exception / admin shutdown
            }
        }
        return false;
    }
}
