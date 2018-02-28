package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.intersection.Stone.WHITE;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Game;
import com.sadakatsu.go.domain.Move;

public class NaiveMinimaxTreeTest {
//    private static final int DIMENSION = 9;
//    private static final int HIDDEN_LAYERS = 7;
//    private static final int CHANNELS = 64;
//    private static final long SEED = 10L;
//    
//    private GoAi heuristic = new GoAi(DIMENSION, HIDDEN_LAYERS, CHANNELS, SEED);
//    
//    @Test
//    public void movesWithSameMeaningPlayOutSimilarly() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
//        Field currentPlayer = Game.class.getDeclaredField("currentPlayer");
//        currentPlayer.setAccessible(true);
//        
//        Game blackFirst = Game.newBuilder(DIMENSION).build();
//        NaiveMinimaxTree root = new NaiveMinimaxTree(heuristic, blackFirst);
//        
//        Game whiteFirst = Game.newBuilder(DIMENSION).build();
//        currentPlayer.set(whiteFirst, WHITE);
//        NaiveMinimaxTree inverted = new NaiveMinimaxTree(heuristic, whiteFirst);
//        
//        assertHaveSameMeaning(root, inverted);
//        
//        Set<Coordinate> explored = new HashSet<>();
//        for (Coordinate coordinate : Coordinate.iterateOverBoard(DIMENSION)) {
//            if (explored.contains(coordinate)) {
//                continue;
//            }
//            
//            Set<Coordinate> forms = getTransformationsOf(coordinate);
//            explored.addAll(forms);
//            
//            int variations = forms.size() * 2;
//            NaiveMinimaxTree[] rollouts = new NaiveMinimaxTree[variations];
//        }
//    }
//    
//    private void assertHaveSameMeaning( NaiveMinimaxTree a, NaiveMinimaxTree b ) {
//        Game gameA = a.getGame();
//        Game gameB = b.getGame();
//        ZobristHash hashA = a.getZobristHash();
//        ZobristHash hashB = b.getZobristHash();
//        assertEquals(
//            String.format(
//                "Two Games expected to have the same meaning did not have the same Zobrist hash.\n%s\n%s\n\n%s\n%s",
//                hashA,
//                gameA,
//                hashB,
//                gameB
//            ),
//            hashA,
//            hashB
//        );
//        
//        Converter converter = Converter.getConverter(hashA, hashB);
//        
//        Map<Move, Score> evaluationA = a.getHeuristicEvaluations();
//        Map<Move, Score> evaluationB = b.getHeuristicEvaluations();
//        for (Move moveA : evaluationA.keySet()) {
//            Score scoreA = evaluationA.get(moveA);
//            Score expectedB = converter.convert(scoreA);
//            Move moveB = expectedB.getMove();
//            Score actualB = evaluationB.get(moveB);
//            if (!scoreA.isOutcomeSimilarTo(actualB)) {
//                System.out.println(scoreA);
//                assertTrue(
//                    String.format(
//                        "Two Games expected to have the same meaning had different scores for moves that should have been similar:\n%s\n%s\n\n%s\n%s",
//                        gameA,
//                        scoreA,
//                        gameB,
//                        actualB
//                    ),
//                    scoreA.isOutcomeSimilarTo(actualB)
//                );
//            }
//        }
//    }
//    
//    
//    
//    private Set<Coordinate> getTransformationsOf( Coordinate coordinate ) {
//        Set<Coordinate> forms = new HashSet<>();
//        forms.add(coordinate);
//        
//        int column = coordinate.getColumn();
//        int columnOpposite = getOppositeOf(column);
//        
//        int row = coordinate.getColumn();
//        int rowOpposite = getOppositeOf(row);
//        
//        Coordinate[] others = new Coordinate[] {
//            Coordinate.get(columnOpposite, row),
//            Coordinate.get(column, rowOpposite),
//            Coordinate.get(columnOpposite, rowOpposite),
//            Coordinate.get(row, column),
//            Coordinate.get(rowOpposite, column),
//            Coordinate.get(row, columnOpposite),
//            Coordinate.get(rowOpposite, columnOpposite)
//        };
//        for (Coordinate other : others) {
//            forms.add(other);
//        }
//        
//        return forms;
//    }
//    
//    private int getOppositeOf( int component ) {
//        return DIMENSION + 1 - component;
//    }
}
