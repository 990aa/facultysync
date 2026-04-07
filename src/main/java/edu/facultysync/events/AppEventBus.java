package edu.facultysync.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Lightweight in-process event bus used to decouple UI modules.
 *
 * <p>Subscribers register by event type and receive events published with the same runtime class.
 * This avoids reflection and keeps the app independent from external event-bus dependencies.</p>
 */
public final class AppEventBus {

    private final String name;
    private final Map<Class<?>, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();

    public AppEventBus(String name) {
        this.name = name;
    }

    /**
     * Subscribes a handler for one event class.
     */
    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribers
                .computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>())
                .add((Consumer<Object>) handler);
    }

    /**
     * Publishes an event to handlers registered for the event runtime class.
     */
    public void post(Object event) {
        if (event == null) {
            return;
        }
        List<Consumer<Object>> handlers = subscribers.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        for (Consumer<Object> handler : handlers) {
            handler.accept(event);
        }
    }

    public String getName() {
        return name;
    }
}
