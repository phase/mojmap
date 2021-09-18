package io.jadon.mappings;

public enum Side {
    CLIENT,
    SERVER;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
