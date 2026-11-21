package com.alejandro.mtoconfiguration.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Apunta el SQL que Hibernate manda a la base, para los tests que afirman sobre <b>que</b>
 * sentencias cuesta una lectura y no solo sobre cuantas.
 *
 * <p>Se activa en el contexto del test que lo necesita, con
 * {@code spring.jpa.properties.hibernate.session_factory.statement_inspector} apuntando a esta clase.
 * Hibernate la instancia el mismo, asi que lo apuntado es estatico: {@link #clear()} antes de la
 * lectura que se mide.
 */
public class SqlStatementRecorder implements StatementInspector {

    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }

    public static void clear() {
        STATEMENTS.clear();
    }

    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    public static long count(Predicate<String> matching) {
        return STATEMENTS.stream().filter(matching).count();
    }
}
