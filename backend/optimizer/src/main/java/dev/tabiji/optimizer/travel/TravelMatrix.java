package dev.tabiji.optimizer.travel;

import java.util.Objects;
import java.util.Optional;

/**
 * Square matrix of travel legs between {@code size} locations, addressed by location index. A missing
 * entry means "no route with the allowed modes". The diagonal is always {@link Leg#stay()}.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class TravelMatrix {

    private final int size;
    private final Leg[] legs;

    private TravelMatrix(int size, Leg[] legs) {
        this.size = size;
        this.legs = legs;
    }

    public static Builder builder(int size) {
        return new Builder(size);
    }

    public int size() {
        return size;
    }

    public Optional<Leg> leg(int from, int to) {
        return Optional.ofNullable(legs[index(from, to)]);
    }

    public boolean isReachable(int from, int to) {
        return legs[index(from, to)] != null;
    }

    /**
     * Leg for a pair that the caller has already checked with {@link #isReachable(int, int)}. Used on the
     * solver's hot path to avoid allocating {@link Optional}s.
     *
     * @throws IllegalStateException if there is no route
     */
    public Leg requireLeg(int from, int to) {
        Leg leg = legs[index(from, to)];
        if (leg == null) {
            throw new IllegalStateException("no route from location " + from + " to " + to);
        }
        return leg;
    }

    private int index(int from, int to) {
        Objects.checkIndex(from, size);
        Objects.checkIndex(to, size);
        return from * size + to;
    }

    /** Mutable builder; {@link #build()} fills the diagonal with {@link Leg#stay()}. */
    public static final class Builder {
        private final int size;
        private final Leg[] legs;

        private Builder(int size) {
            if (size < 0) {
                throw new IllegalArgumentException("size must be >= 0: " + size);
            }
            this.size = size;
            this.legs = new Leg[Math.multiplyExact(size, size)];
        }

        public Builder set(int from, int to, Leg leg) {
            Objects.checkIndex(from, size);
            Objects.checkIndex(to, size);
            legs[from * size + to] = Objects.requireNonNull(leg, "leg");
            return this;
        }

        public TravelMatrix build() {
            Leg[] copy = legs.clone();
            for (int i = 0; i < size; i++) {
                copy[i * size + i] = Leg.stay();
            }
            return new TravelMatrix(size, copy);
        }
    }
}
