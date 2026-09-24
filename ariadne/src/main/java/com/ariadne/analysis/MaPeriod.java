package com.ariadne.analysis;

public enum MaPeriod {
    MA5(5),
    MA15(15),
    MA30(30),
    MA60(60),
    MA120(120);

    private final int window;

    MaPeriod(int window) { this.window = window; }

    public int window() { return window; }
}
