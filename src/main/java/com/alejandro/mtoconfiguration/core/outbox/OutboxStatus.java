package com.alejandro.mtoconfiguration.core.outbox;

/*
PENDING   -> evento pendiente de publicar
PUBLISHED -> evento publicado correctamente en RabbitMQ
FAILED    -> evento que ha superado el número máximo de intentos
*/
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
