package com.payton.touchblocker.display;

import java.util.HashMap;
import java.util.Map;

public final class DisplayGenerationTracker {
    private final Map<Integer, State> states = new HashMap<>();

    public synchronized long generationFor(int displayId, DisplayGeometrySignature signature) {
        if (signature == null) {
            throw new NullPointerException("signature == null");
        }
        State previous = states.get(displayId);
        if (previous != null && previous.signature.equals(signature)) {
            return previous.generation;
        }
        long generation = previous == null ? 1L : previous.generation + 1L;
        states.put(displayId, new State(signature, generation));
        return generation;
    }

    private static final class State {
        private final DisplayGeometrySignature signature;
        private final long generation;

        private State(DisplayGeometrySignature signature, long generation) {
            this.signature = signature;
            this.generation = generation;
        }
    }
}
