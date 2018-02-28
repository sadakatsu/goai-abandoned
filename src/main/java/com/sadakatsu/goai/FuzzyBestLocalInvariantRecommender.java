package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.Pass.PASS;
import static com.sadakatsu.goai.ScoreRecommender.Relationship.*;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Game;
import com.sadakatsu.go.domain.Move;
import com.sadakatsu.goai.GoAi.Evaluation;

public class FuzzyBestLocalInvariantRecommender extends ScoreRecommender {
    private static class DistanceAndPositionState extends RecommenderComparisonState {
        final Integer lastColumn;
        final Integer lastRow;
        final Game game;
        final Map<Move, Integer> distances;
        final Map<Move, ZobristHash> hashes;
        
        DistanceAndPositionState( Evaluation evaluation ) {
            super(evaluation);
            this.distances = new HashMap<>();
            this.game = evaluation.getGame();
            this.hashes = new HashMap<>();
            
            Coordinate lastMove = getLastNonPassMove(game);
            if (lastMove != null) {
                lastColumn = lastMove.getColumn();
                lastRow = lastMove.getRow();
            } else {
                lastColumn = null;
                lastRow = null;
            }
        }
        
        private Coordinate getLastNonPassMove( Game game ) {
            Coordinate lastMove = null;
            
            Game cursor = game;
            try {
                while (lastMove == null) {
                    Move move = cursor.getPreviousMove();
                    if (move == PASS) {
                        cursor = cursor.getPreviousState();
                    } else {
                        lastMove = (Coordinate) move;
                    }
                }
            } catch (IllegalStateException e) {
                // The cursor has reached the beginning of the game, so there is no non-pass last move. 
            }
            
            return lastMove;
        }
        
        int getDistanceFromLastMove( Move move ) {
            Integer distance = 0;
            if (lastColumn != null && move != PASS) {
                distance = distances.get(move);
                if (distance == null) {
                    Coordinate coordinate = (Coordinate) move;
                    int column = coordinate.getColumn();
                    int row = coordinate.getRow();
                    int deltaC = column - lastColumn;
                    int deltaR = row - lastRow;
                    distance = deltaC * deltaC + deltaR * deltaR;
                    distances.put(move, distance);
                }
            }
            return distance;
        }
        
        ZobristHash getHashAfter( Move move ) {
            ZobristHash hash = hashes.get(move);
            if (hash == null) {
                Game next = game.play(move);
                hash = new ZobristHash(next);
                hashes.put(move, hash);
            }
            return hash;
        }
    }
    
    @Override
    protected RecommenderComparisonState buildComparisonState( Evaluation evaluation ) {
        return new DistanceAndPositionState(evaluation);
    }

    @Override
    protected Relationship compareScores( Score first, Score second, RecommenderComparisonState state ) {
        Relationship relationship;
        
        int comparison = 0;
        Move firstMove = first.getMove();
        Move secondMove = second.getMove();
        
        // Prefer first the move that gets the best difference in scores, then the move that gets the current player
        // the most points, unless the differences in the moves' scores is caused by rounding. 
        if (!first.isOutcomeSimilarTo(second)) {
            comparison = Double.compare(first.getReadableResult(), second.getReadableResult());
            if (comparison == 0) {
                comparison = Double.compare(first.getReadablePlayerScore(), second.getReadablePlayerScore());
            }
        }
        
        // Prefer a pass to a play since that is considered more elegant.
        if (comparison == 0) {
            comparison = (firstMove == PASS ? 1 : 0) + (secondMove == PASS ? -1 : 0);
        }
        
        // Prefer a move that is closer to the last move played than one that is further away.  We want the AI to learn
        // how to fight (and when not to!).
        if (comparison == 0) {
            DistanceAndPositionState actualState = (DistanceAndPositionState) state;
            int firstDistance = actualState.getDistanceFromLastMove(firstMove);
            int secondDistance = actualState.getDistanceFromLastMove(secondMove);
            comparison = Integer.compare(secondDistance, firstDistance);
        }
        
        // Convert the comparison into a Relationship.
        if (comparison == 0) {
            relationship = BOTH_ARE_EQUIVALENT;
        } else if (comparison > 0) {
            relationship = FIRST_IS_BETTER_THAN_SECOND;
        } else {
            relationship = SECOND_IS_BETTER_THAN_FIRST;
        }
        
        return relationship;
    }

    @Override
    protected boolean doesFirstLoseTie( Score first, Score second, RecommenderComparisonState state ) {
        // Since it is possible for two moves with equivalent scores and distances from the last move that are not
        // transformationally invariant in reference to the positions they produce, simply selecting a move randomly
        // can (and usually does) lead to different rollouts.  Guaranteeing transformational invariance thus requires
        // some way of preferring moves based upon the positions they create.  Selecting the move with the lower
        // Zobrist hash is unlikely to guarantee exploring higher quality variations, but at least it guarantees the
        // transformational invariance property my rollouts need.
        DistanceAndPositionState actualState = (DistanceAndPositionState) state;
        
        Move firstMove = first.getMove();
        Move secondMove = second.getMove();
        
        ZobristHash firstHash = actualState.getHashAfter(firstMove);
        ZobristHash secondHash = actualState.getHashAfter(secondMove);
        
        BigInteger firstCanonical = firstHash.getCanonicalHash();
        BigInteger secondCanonical = secondHash.getCanonicalHash();
        
        int comparison = firstCanonical.compareTo(secondCanonical);
        return comparison > 0;
    }

}
