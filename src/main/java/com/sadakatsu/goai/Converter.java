package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.Pass.PASS;

import java.util.HashMap;
import java.util.Map;

import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Move;

public abstract class Converter {
    private static final class NoTransformation extends Converter {
        NoTransformation( int dimension ) {
            super(dimension);
        }
        
        @Override
        public final Score performConversion( Score score ) {
            return score;
        }
    }
    
    private static final class FlipHorizontally extends Converter {
        FlipHorizontally( int dimension ) {
            super(dimension);
        }
    
        @Override
        Score performConversion( Score score ) {
            double rawPlayerScore = score.getRawPlayerScore();
            double rawOpponentScore = score.getRawOpponentScore();
            
            Move move = score.getMove();
            if (move != PASS) {
                Coordinate coordinate = (Coordinate) move;
                int column = dimension - coordinate.getColumn() + 1;
                int row = coordinate.getRow();
                move = (Move) Coordinate.get(column, row);
            }
            
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
    }
    
    private static final class FlipVertically extends Converter {
        FlipVertically( int dimension ) {
            super(dimension);
        }
    
        @Override
        Score performConversion( Score score ) {
            double rawPlayerScore = score.getRawPlayerScore();
            double rawOpponentScore = score.getRawOpponentScore();
            
            Move move = score.getMove();
            if (move != PASS) {
                Coordinate coordinate = (Coordinate) move;
                int column = coordinate.getColumn();
                int row = dimension - coordinate.getRow() + 1;
                move = (Move) Coordinate.get(column, row);
            }
            
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
    }
    
    private static final class FlipBoth extends Converter {
        FlipBoth( int dimension ) {
            super(dimension);
        }
    
        @Override
        Score performConversion( Score score ) {
            double rawPlayerScore = score.getRawPlayerScore();
            double rawOpponentScore = score.getRawOpponentScore();
            
            Move move = score.getMove();
            if (move != PASS) {
                Coordinate coordinate = (Coordinate) move;
                int column = dimension - coordinate.getColumn() + 1;
                int row = dimension - coordinate.getRow() + 1;
                move = (Move) Coordinate.get(column, row);
            }
            
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
    }
    
    private static final class FlipAlongUpperLeftDiagonal extends Converter {
        FlipAlongUpperLeftDiagonal( int dimension ) {
            super(dimension);
        }
        
        @Override
        Score performConversion( Score score ) {
            double rawPlayerScore = score.getRawPlayerScore();
            double rawOpponentScore = score.getRawOpponentScore();
            
            Move move = score.getMove();
            if (move != PASS) {
                Coordinate coordinate = (Coordinate) move;
                int column = coordinate.getRow();
                int row = coordinate.getColumn();
                move = (Move) Coordinate.get(column, row);
            }
            
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
    }
    
    private static final class RotateLeft extends Converter {
        RotateLeft( int dimension ) {
            super(dimension);
        }
        
        @Override
        Score performConversion( Score score ) {
            double rawPlayerScore = score.getRawPlayerScore();
            double rawOpponentScore = score.getRawOpponentScore();
            
            Move move = score.getMove();
            if (move != PASS) {
                Coordinate coordinate = (Coordinate) move;
                int column = dimension - coordinate.getRow() + 1;
                int row = coordinate.getColumn();
                move = (Move) Coordinate.get(column, row);
            }
            
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
    }
    
    private static final class RotateRight extends Converter {
        RotateRight( int dimension ) {
            super(dimension);
        }
        
        @Override
        Score performConversion( Score score ) {
            double rawPlayerScore = score.getRawPlayerScore();
            double rawOpponentScore = score.getRawOpponentScore();
            
            Move move = score.getMove();
            if (move != PASS) {
                Coordinate coordinate = (Coordinate) move;
                int column = coordinate.getRow();
                int row = dimension - coordinate.getColumn() + 1;
                move = (Move) Coordinate.get(column, row);
            }
            
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
    }
    
    private static final class FlipAlongUpperRightDiagonal extends Converter {
        FlipAlongUpperRightDiagonal( int dimension ) {
            super(dimension);
        }
        
        @Override
        Score performConversion( Score score ) {
            double rawPlayerScore = score.getRawPlayerScore();
            double rawOpponentScore = score.getRawOpponentScore();
            
            Move move = score.getMove();
            if (move != PASS) {
                Coordinate coordinate = (Coordinate) move;
                int column = dimension - coordinate.getRow() + 1;
                int row = dimension - coordinate.getColumn() + 1;
                move = (Move) Coordinate.get(column, row);
            }
            
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
    }
    
    public static Converter getConverter( ZobristHash start, ZobristHash end ) {
        validateHashes(start, end);
        
        int dimension = start.getDimension();
        Converter[] converters = getOrCreateConvertersForDimension(dimension);
        
        int startIndex = start.getCanonicalIndex();
        int endIndex = end.getCanonicalIndex();
        int transformationIndex = getCombinedTransformationIndex(startIndex, endIndex);
        
        return converters[transformationIndex];
    }
    
    private static Converter[] getOrCreateConvertersForDimension( int dimension ) {
        Converter[] converters;
        
        if (CONVERTERS.containsKey(dimension)) {
            converters = CONVERTERS.get(dimension);
        } else {
            converters = new Converter[] {
                new NoTransformation(dimension),
                new FlipHorizontally(dimension),
                new FlipVertically(dimension),
                new FlipBoth(dimension),
                new FlipAlongUpperLeftDiagonal(dimension),
                new RotateLeft(dimension),
                new RotateRight(dimension),
                new FlipAlongUpperRightDiagonal(dimension)
            };
            CONVERTERS.put(dimension, converters);
        }
        
        return converters;
    }
    
    public static Converter getIdentity( ZobristHash space ) {
        ensureThatHashIsNotNull(space, "space");
        
        int dimension = space.getDimension();
        Converter[] converters = getOrCreateConvertersForDimension(dimension);
        return converters[0];
    }
    
    private static void validateHashes( ZobristHash start, ZobristHash end ) {
        ensureThatHashIsNotNull(start, "start");
        ensureThatHashIsNotNull(end, "end");
        ensureThatHashesCanBeConverted(start, end);
    }
    
    private static void ensureThatHashIsNotNull( ZobristHash hash, String name ) {
        if (hash == null) {
            String message = String.format("'%s' is not allowed to be null.", name);
            throw new IllegalArgumentException(message);
        }
    }
    
    private static void ensureThatHashesCanBeConverted( ZobristHash start, ZobristHash end ) {
        if (!start.equals(end)) {
            String message = String.format(
                "A Converter can only be used between two ZobristHashes that represent the same position, but " +
                "getConverter() was passed a 'start' of %s and an 'end' of %s.",
                start,
                end
            );
            throw new IllegalArgumentException(message);
        }
    }
    
    private static final int getCombinedTransformationIndex( int startIndex, int endIndex ) {
        return COMBINED_TRANSFORMATIONS[startIndex][endIndex];
    }
    
    // Given a starting canonical index as the first index and an ending canonical index as the second, this array
    // provides the index of the Converter object to use to translate a Coordinate from the starting space into the
    // ending space.  Note that while inversions of the space (from Black to White and vice-versa) are saved by the
    // ZobristHash's canonical index, these make no difference to where a move is played.  This requires only eight
    // Converters per dimension.
    private static final int[][] COMBINED_TRANSFORMATIONS = new int[][] {
        { 0, 1, 2, 3, 4, 6, 5, 7, 0, 1, 2, 3, 4, 6, 5, 7 },
        { 1, 0, 3, 2, 6, 4, 7, 5, 1, 0, 3, 2, 6, 4, 7, 5 },
        { 2, 3, 0, 1, 5, 7, 4, 6, 2, 3, 0, 1, 5, 7, 4, 6 },
        { 3, 2, 1, 0, 7, 5, 6, 4, 3, 2, 1, 0, 7, 5, 6, 4 },
        { 4, 5, 6, 7, 0, 2, 1, 3, 4, 5, 6, 7, 0, 2, 1, 3 },
        { 5, 4, 7, 6, 2, 0, 3, 1, 5, 4, 7, 6, 2, 0, 3, 1 },
        { 6, 7, 4, 5, 1, 3, 0, 2, 6, 7, 4, 5, 1, 3, 0, 2 },
        { 7, 6, 5, 4, 3, 1, 2, 0, 7, 6, 5, 4, 3, 1, 2, 0 },
        { 0, 1, 2, 3, 4, 6, 5, 7, 0, 1, 2, 3, 4, 6, 5, 7 },
        { 1, 0, 3, 2, 6, 4, 7, 5, 1, 0, 3, 2, 6, 4, 7, 5 },
        { 2, 3, 0, 1, 5, 7, 4, 6, 2, 3, 0, 1, 5, 7, 4, 6 },
        { 3, 2, 1, 0, 7, 5, 6, 4, 3, 2, 1, 0, 7, 5, 6, 4 },
        { 4, 5, 6, 7, 0, 2, 1, 3, 4, 5, 6, 7, 0, 2, 1, 3 },
        { 5, 4, 7, 6, 2, 0, 3, 1, 5, 4, 7, 6, 2, 0, 3, 1 },
        { 6, 7, 4, 5, 1, 3, 0, 2, 6, 7, 4, 5, 1, 3, 0, 2 },
        { 7, 6, 5, 4, 3, 1, 2, 0, 7, 6, 5, 4, 3, 1, 2, 0 }
    };
    private static final Map<Integer, Converter[]> CONVERTERS = new HashMap<>();
    
    final int dimension;
    
    Converter( int dimension ) {
        this.dimension = dimension;
    }
    
    public Score convert( Score score ) {
        if (score == null) {
            throw new IllegalArgumentException("The passed Score may not be null.");
        }
        return performConversion(score);
    }
    
    abstract Score performConversion( Score score );
}