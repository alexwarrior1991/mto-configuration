package com.alejandro.mtoconfiguration.core.exception.web;

/**
 * Un error concreto dentro de una respuesta de error.
 *
 * @param field   ruta JSON del campo señalado ({@code tracks[0].name}), o {@code null} si el error
 *                no es atribuible a un campo
 * @param code    código estable del catálogo ({@code VAL-001}), para que el cliente pueda
 *                reaccionar sin parsear el texto
 * @param message mensaje ya resuelto y formateado, para mostrar
 */
public record ApiErrorDetail(String field, String code, String message) {
}
