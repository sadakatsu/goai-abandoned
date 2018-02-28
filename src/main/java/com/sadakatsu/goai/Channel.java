package com.sadakatsu.goai;

public final class Channel {
    private final int padding;
    private final int width;
    
    public Channel( int width, int padding ) {
        validate(width, 1, "width");
        validate(padding, 0, "padding");
        
        this.padding = padding;
        this.width = width;
    }
    
    private void validate( int value, int minimum, String label ) {
        if (value < minimum) {
            String message = String.format(
                "'%s' is not allowed to be less than %d, but was %d.",
                label,
                minimum,
                value
            );
            throw new IllegalArgumentException(message);
        }
    }

    public int getPadding() {
        return padding;
    }

    public int getWidth() {
        return width;
    }
    
    @Override
    public String toString() {
        return String.format("Channel{ width=%d, padding=%d }", width, padding);
    }
}
