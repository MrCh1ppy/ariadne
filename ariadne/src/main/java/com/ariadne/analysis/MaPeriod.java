package com.ariadne.analysis;

public enum MaPeriod {
    MA30(30),
    MA60(60),
    MA120(120);

    private final int window;

    MaPeriod(int window) { this.window = window; }

    public int window() { return window; }
}
