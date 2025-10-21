package com.brandedhustlers.electionbackend.common.events;

public record EventEnvelope<T>(String topic, T event) {
}
