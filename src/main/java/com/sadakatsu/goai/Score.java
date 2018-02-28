package com.sadakatsu.goai;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.sadakatsu.go.domain.Move;

public class Score {
    public static Score createFromRawScores( Move move, double rawPlayerScore, double rawOpponentScore ) {
        validateMove(move);
        
        Score score = new Score();
        score.move = move;
        score.rawOpponentScore = rawOpponentScore;
        score.rawPlayerScore = rawPlayerScore;
        score.readableOpponentScore = convertToReadable(rawOpponentScore);
        score.readablePlayerScore = convertToReadable(rawPlayerScore);
        
        return score;
    }

    private static void validateMove( Move move ) {
        if (move == null) {
            throw new IllegalArgumentException("The Move may not be null.");
        }
    }
    
    private static double convertToReadable( double rawScore ) {
        double scaled = rawScore * SCALE;
        double floor = Math.floor(scaled);
        double remainder = scaled - floor;
        int offset = (int) Math.floor(remainder * 8);
        double fraction;
        if (offset < 1) {
            fraction = 0.;
        } else if (offset < 3) {
            fraction = 0.25;
        } else if (offset < 5) {
            fraction = 0.5;
        } else if (offset < 7) {
            fraction = 0.75;
        } else {
            fraction = 1.;
        }
        return floor + fraction;
    }
    
    public static Score createFromReadableScores(
        Move move,
        double readablePlayerScore,
        double readableOpponentScore
    ) {
        validateMove(move);
        
        Score score = new Score();
        score.move = move;
        score.rawOpponentScore = convertToRaw(readableOpponentScore);
        score.rawPlayerScore = convertToRaw(readablePlayerScore);
        score.readableOpponentScore = readableOpponentScore;
        score.readablePlayerScore = readablePlayerScore;
        
        return score;
    }
    
    private static double convertToRaw( double readableScore ) {
        return readableScore * INVERSE;
    }
    
    // so a raw score of 1 is equivalent to a readable score of 361
    private static final int SCALE = 361;
    private static final double INVERSE = 1. / SCALE;
    
    
    private Move move;
    private double rawOpponentScore;
    private double rawPlayerScore;
    private double readableOpponentScore;
    private double readablePlayerScore;
    
    private Score() {}
    
    public Move getMove() {
        return move;
    }

    public double getRawOpponentScore() {
        return rawOpponentScore;
    }

    public double getRawPlayerScore() {
        return rawPlayerScore;
    }

    public double getReadableOpponentScore() {
        return readableOpponentScore;
    }

    public double getReadablePlayerScore() {
        return readablePlayerScore;
    }
    
    public double getRawResult() {
        return rawPlayerScore - rawOpponentScore;
    }
    
    public double getReadableResult() {
        return readablePlayerScore - readableOpponentScore;
    }
    
    public Score invert() {
        return Score.createFromRawScores(move, rawOpponentScore, rawPlayerScore);
    }
    
    public Score tidyRaws() {
        return Score.createFromReadableScores(move, readablePlayerScore, readableOpponentScore);
    }
    
    public boolean isOutcomeSimilarTo( Score that ) {
        return
            that != null &&
            areEquivalent(this.rawPlayerScore, that.rawPlayerScore) &&
            areEquivalent(this.rawOpponentScore, that.rawOpponentScore);
    }
    
    // Usually, two raw values that convert to different readable values can be trusted to be different scores, even
    // after FLOP rounding errors.  However, it is possible that two raw values are close to each other but round to
    // different readable values because they are on opposite sides of the splitting plane.  I do not think that it
    // is feasible to calculate a good epsilon to compare two raws since the errors will depend upon the number of
    // FLOPs the neural network that ultimately generates these values performs.  The best heuristic I can devise is
    // that two numbers should be considered equivalent if they are closer to each other than at least one of them is
    // to the value to which it is rounded.
    private boolean areEquivalent( double a, double b ) {
        double distanceBetweenScores = Math.abs(a - b);
        double distanceFromAToRounded = distanceFromRoundedValue(a);
        double distanceFromBToRounded = distanceFromRoundedValue(b);
        return distanceBetweenScores < distanceFromAToRounded || distanceBetweenScores < distanceFromBToRounded;
    }
    
    private double distanceFromRoundedValue( double value ) {
        double readable = convertToReadable(value);
        double tidyRaw = convertToRaw(readable);
        return Math.abs(value - tidyRaw);
    }
    
    @Override
    public boolean equals( Object other ) {
        boolean result = this == other;
        if (!result && other != null && Score.class.equals(other.getClass())) {
            Score that = (Score) other;
            result =
                this.move.equals(that.move) &&
                this.readablePlayerScore == that.readablePlayerScore &&
                this.readableOpponentScore == that.readableOpponentScore;
        }
        return result;
    }
    
    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(move);
        builder.append(readablePlayerScore);
        builder.append(readableOpponentScore);
        // The raw scores are NOT included to ensure the equals/hashCode implicit contract.
        return builder.toHashCode();
    }
    
    @Override
    public String toString() {
        return String.format(
            "Score{ move: %s, result: %+1.2f, player: %+1.2f, opponent: %+1.2f, rawPlayer: %+1.2f, rawOpponent: %+1.2f }",
            move,
            getReadableResult(),
            readablePlayerScore,
            readableOpponentScore,
            rawPlayerScore,
            rawOpponentScore
        );
    }
}
