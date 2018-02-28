package com.sadakatsu.goai;

import static com.sadakatsu.goai.DesignTest.Channels.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ThreadLocalRandom;
import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Game;
import com.sadakatsu.go.domain.Group;
import com.sadakatsu.go.domain.Game.GameBuilder;
import com.sadakatsu.go.domain.Move;
import com.sadakatsu.go.domain.Pass;
import com.sadakatsu.go.domain.intersection.Intersection;
import com.sadakatsu.go.domain.intersection.Player;
import com.sadakatsu.go.domain.intersection.Stone;

public class DesignTest {
    static enum Channels {
        ON_BOARD,
        OFF_BOARD,
        PLAYER_STONE,
        ENEMY_STONE,
        NO_STONE,
        LEGAL_MOVE,
        ILLEGAL_MOVE,
        ENEMY_COULD_PLAY,
        ENEMY_COULD_NOT_PLAY,
        LIBERTIES_ONE,
        LIBERTIES_TWO,
        LIBERTIES_THREE,
        LIBERTIES_FOUR,
        LIBERTIES_FIVE,
        LIBERTIES_SIX,
        LIBERTIES_SEVEN,
        LIBERTIES_EIGHT_OR_MORE;
    }

    final static class SwivelingConvolution {
        private static final Random random;
        
        static {
//            long seed = System.nanoTime();
            // long seed = 257579890154209L; // incomplete game that ends without any playout
            // long seed = 2505913139841L; // almost complete game that plays out to an incorrect resolution
            // long seed = 2816488135031L; // 11,639 move game that ends in a correct count without needing any life/death resolution (Black wins)
            // long seed = 3416296093302L; // 3,680 move game that ends in a correct count without needing any life/death resolution (White wins)
            // long seed = 3785734800519L; // a bizarre 10 move game that results in a 178-move resolution in which the AI decides that all Blacks' stones die
            // long seed = 9655346546017L; // an interesting 50 move game with incorrect playout 166 move playout (White wins)
            long seed = 318349596315375L; // a very promising-looking seed; 44 moves, 162 move playout, concludes all of Black is dead.  This is debatable but plausible. 
            System.out.println(seed);
            random = new Random(seed);
        }
        
        private double              bias;
        private double[][][]        weights;
        private int                 depth;
        private int                 halfWidth;
        private int                 inputPadding;
        private int                 outputPadding;
        private int                 width;

        SwivelingConvolution( int depth, int width, int inputPadding, int outputPadding ) {
            double scale = 1. / Math.sqrt(depth * width * width);
            this.bias = random.nextGaussian() * scale;
            this.depth = depth;
            this.halfWidth = width >> 1;
            this.inputPadding = inputPadding;
            this.outputPadding = outputPadding;
            this.weights = new double[depth][width][width];
            this.width = width;
            for (int z = 0; z < depth; ++z) {
                for (int y = 0; y < width; ++y) {
                    for (int x = 0; x < width; ++x) {
                        this.weights[z][y][x] = random.nextGaussian() * scale;
                    }
                }
            }
        }

        double[][] calculate( double[][][] input ) {
            int inputWidth = input[0].length;
            int outputWidth = inputWidth + (outputPadding - inputPadding) * 2;
            double[][] results = new double[outputWidth][outputWidth];
            for (int y = inputPadding, outY = outputPadding; y < inputWidth - inputPadding; ++y, ++outY) {
                for (int x = inputPadding, outX = outputPadding; x < inputWidth - inputPadding; ++x, ++outX) {
                    results[outY][outX] = calculate(input, y, x);
                }
            }
            return results;
        }

        double calculate( double[][][] input, int y, int x ) {
            double[] results = new double[] { bias, bias, bias, bias, bias, bias, bias, bias };
            
            final int minX = x - halfWidth;
            final int minY = y - halfWidth;
            final int maxIndex = width - 1;
            for (int k = 0; k < depth; ++k) {
                double[][] inputChannel = input[k];
                double[][] weightChannel = weights[k];
                
                for (
                    int j = minY, weightJ = 0, oppositeJ = maxIndex;
                    weightJ <= maxIndex;
                    ++j, ++weightJ, --oppositeJ
                ) {
                    double[] inputRow = inputChannel[j];
                    double[] weightRowJ = weightChannel[weightJ];
                    double[] weightRowJOpposite = weightChannel[oppositeJ];
                    
                    for (
                        int i = minX, weightI = 0, oppositeI = maxIndex;
                        weightI <= maxIndex;
                        ++i, ++weightI, --oppositeI
                    ) {
                        double inputValue = inputRow[i];
                        double[] weightRowI = weightChannel[weightI];
                        double[] weightRowIOpposite = weightChannel[oppositeI];
                        
                        results[0] += inputValue * weightRowJ[weightI];
                        results[1] += inputValue * weightRowJ[oppositeI];
                        results[2] += inputValue * weightRowJOpposite[weightI];
                        results[3] += inputValue * weightRowJOpposite[oppositeI];
                        results[4] += inputValue * weightRowI[weightJ];
                        results[5] += inputValue * weightRowI[oppositeJ];
                        results[6] += inputValue * weightRowIOpposite[weightJ];
                        results[7] += inputValue * weightRowIOpposite[oppositeJ];
                    }
                }
            }
            
            double greatest = Double.NEGATIVE_INFINITY;
            for (double result : results) {
                if (result > greatest) {
                    greatest = result;
                }
            }
            return greatest;
        }
    }

    private static class RunConvolution extends RecursiveTask<double[][]> {
        private static final long    serialVersionUID = -7976792059084271320L;
        private double[][][]         input;
        private SwivelingConvolution convolution;

        RunConvolution( SwivelingConvolution convolution, double[][][] input ) {
            this.convolution = convolution;
            this.input = input;
        }

        @Override
        protected double[][] compute() {
            return convolution.calculate(input);
        }
    }

    // private static final int DIMENSION = 19;
    // private static final int FILTERS = 256;
    // private static final int LAYERS = 32;
    private static final int DIMENSION = 9;
    private static final int FILTERS   = 64;
    private static final int LAYERS    = 8;

    public static void main( String[] args ) {
        long start, end;
        
        start = System.nanoTime();
        Game game = getGame();
        end = System.nanoTime();
        System.out.println("game created in " + (end - start) / 1e6 + " ms");
        
        start = System.nanoTime();
        SwivelingConvolution[][] network = buildNetwork(LAYERS, FILTERS);
        end = System.nanoTime();
        System.out.println("network created in " + (end - start) / 1e6 + " ms");
        
        boolean mayNotPass = false;
        do {
            double[][][] encoding = null;
            double[][][] results = null;
            
            while (!game.isOver()) {
                System.out.println(game);
                start = System.nanoTime();
                encoding = encode(game);
                end = System.nanoTime();
                System.out.println("game encoded in " + (end - start) / 1e6 + " ms");
                
                start = System.nanoTime();
                results = runNetwork(network, encoding);
                end = System.nanoTime();
                System.out.println("DCNN output calculated in " + (end - start) / 1e6 + " ms");
                
                System.out.println("Calculated move values: ");
                start = System.nanoTime();
                Move selected = chooseMove(game, results, mayNotPass);
                end = System.nanoTime();
                System.out.println("move selected in " + (end - start) / 1e6 + " ms\n");
                
                game = game.play(selected);
                mayNotPass = false;
            }
            
            System.out.println(game);
            
            // When trained, the neural network should calculate inverse scores between the two players.  If Black
            // selects a move where his best move guarantees a win of at least 7 points, White should select a move that
            // guarantees him a loss of no more than 7 points.  Theoretically, a player will pass only when further play
            // will not change the score, so this loop attempts to determine living and dead stones by carefully playing
            // out only those moves that do not lower either player's score.  I believe that a well-trained network will
            // not kill any of his living stones, so that means that any groups that get captured in this playout are
            // dead; any that are left alone are alive. However, before this training is complete, it may be possible
            // for part of a contiguous group to die while another part lives (since the group gets captured, then
            // partially replaced by a living group). I decided to treat this as a situation where the two players do
            // not agree on the life and death, thus forcing the game to continue.
            System.out.println("ATTEMPTING TO END GAME...");
            // int passScore = determineScore(results[2][0][0],
            // results[3][0][0]);
            Double passScore = null;
            Game finalize = game.resume();
            while (!finalize.isOver()) {
                encoding = encode(finalize);
                results = runNetwork(network, encoding);
                if (passScore == null) {
                    passScore = determineScore(results[2][0][0], results[3][0][0]);
                }
                List<Evaluation> moves = evaluateMoves(finalize, results);
                Collections.sort(moves);
                if (moves.size() == 0 || moves.get(0).score < passScore) {
                    finalize = finalize.pass();
                } else {
                    finalize = finalize.play(moves.get(0).move);
                }
                passScore = -passScore;
                String step = finalize.toString();
                String[] lines = step.split("[\\s&&[^ ]]+");
                for (int i = 0; i < lines.length; ++i) {
                    lines[i] = "\t" + lines[i];
                }
                System.out.println(String.join("\n", lines));
                System.out.println();
            }
            
            boolean contradiction = false;
            Set<Group> deadGroups = new HashSet<>();
            for (Group group : game.getGroupsOfStones()) {
                Boolean isDead = null;
                Stone expected = (Stone) group.type;
                for (Coordinate coordinate : group.members) {
                    Stone actual = (Stone) finalize.get(coordinate);
                    boolean flagAsDead = expected != actual;
                    if (isDead == null) {
                        isDead = flagAsDead;
                    } else if (isDead != flagAsDead) {
                        contradiction = true;
                        break;
                    }
                }
                if (contradiction) {
                    break;
                } else if (isDead) {
                    deadGroups.add(group);
                }
            }
            
            if (contradiction) {
                System.out.println("COULD NOT END GAME IN THIS POSITION; FORCING CONTINUATION.");
                mayNotPass = true;
            } else {
                game = game.score(deadGroups);
                System.out.println("GAME IS OVER!\n");
            }
        } while (!game.isOver());
        
        System.out.println(game);
    }

    private static Game getGame() {
        GameBuilder builder = Game.newBuilder(DIMENSION);
        return builder.build();
    }

    private static double[][][] encode( Game game ) {
        double[][][] encoding = new double[17][DIMENSION + 4][DIMENSION + 4];
        prepareOnBoardChannel(encoding);
        prepareOffBoardChannel(encoding);
        populateGameSpecificChannel(game, encoding);
        return encoding;
    }

    private static void prepareOnBoardChannel( double[][][] encoding ) {
        for (Coordinate coordinate : Coordinate.iterateOverBoard(DIMENSION)) {
            int i = coordinate.getRow() + 1;
            int j = coordinate.getColumn() + 1;
            encoding[ON_BOARD.ordinal()][i][j] = 1;
        }
    }

    private static void prepareOffBoardChannel( double[][][] encoding ) {
        for (int i = 0; i < DIMENSION + 4; ++i) {
            for (int j = 0; j < DIMENSION + 4; ++j) {
                if (i < 2 || i > DIMENSION + 1 || j < 2 || j > DIMENSION + 1) {
                    encoding[OFF_BOARD.ordinal()][i][j] = 1;
                }
            }
        }
    }

    private static void populateGameSpecificChannel( Game game, double[][][] encoding ) {
        Set<Move> legalMoves = game.getLegalMoves();
        Set<Move> opponentMoves = game.pass().getLegalMoves();
        Player player = game.getCurrentPlayer();
        int lastIndex = Channels.values().length;
        for (Coordinate coordinate : Coordinate.iterateOverBoard(DIMENSION)) {
            int i = coordinate.getRow() + 1;
            int j = coordinate.getColumn() + 1;
            Intersection value = game.get(coordinate);
            
            if (value.equals(player)) {
                encoding[PLAYER_STONE.ordinal()][i][j] = 1;
            } else if (value.equals(player.getOpposite())) {
                encoding[ENEMY_STONE.ordinal()][i][j] = 1;
            } else {
                encoding[NO_STONE.ordinal()][i][j] = 1;
            }
            
            if (legalMoves.contains(coordinate)) {
                encoding[LEGAL_MOVE.ordinal()][i][j] = 1;
            } else {
                encoding[ILLEGAL_MOVE.ordinal()][i][j] = 1;
            }
            
            if (opponentMoves.contains(coordinate)) {
                encoding[ENEMY_COULD_PLAY.ordinal()][i][j] = 1;
            } else {
                encoding[ENEMY_COULD_NOT_PLAY.ordinal()][i][j] = 1;
            }
        }
        for (Group group : game.getGroupsOfStones()) {
            int channelIndex = group.liberties + LIBERTIES_ONE.ordinal() - 1;
            if (channelIndex >= lastIndex) {
                channelIndex = lastIndex - 1;
            }
            for (Coordinate member : group.members) {
                int i = member.getColumn() + 1;
                int j = member.getRow() + 1;
                encoding[channelIndex][i][j] = 1;
            }
        }
    }

    // This is a terrible hack that is not robust at all, but it will do the trick for now.
    private static SwivelingConvolution[][] buildNetwork( int layers, int filtersPerLayer ) {
        SwivelingConvolution[][] network = new SwivelingConvolution[layers][filtersPerLayer];
        for (int i = 0; i < filtersPerLayer; ++i) {
            network[0][i] = new SwivelingConvolution(Channels.values().length, 5, 2, 1);
        }
        for (int j = 1; j < layers - 1; ++j) {
            for (int i = 0; i < filtersPerLayer; ++i) {
                network[j][i] = new SwivelingConvolution(filtersPerLayer, 3, 1, 1);
            }
        }
        for (int i = 0; i < 2; ++i) {
            network[layers - 1][i] = new SwivelingConvolution(filtersPerLayer, 3, 1, 0);
        }
        for (int i = 2; i < 4; ++i) {
            network[layers - 1][i] = new SwivelingConvolution(filtersPerLayer, DIMENSION, 0, 0);
        }
        return network;
    }

    private static double[][][] runNetwork( SwivelingConvolution[][] network, double[][][] encoding ) {
        double[][][] current = encoding;
        double[][][] next = null;
        
        List<RunConvolution> tasks = new ArrayList<>();
        for (SwivelingConvolution[] layer : network) {
            boolean isLast = layer == network[network.length - 1];
            int count = isLast ? 2 : layer.length;
            int length = isLast ? 4 : count;
            next = new double[length][][];
            tasks.clear();
            for (int i = 0; i < count; ++i) {
                RunConvolution task = new RunConvolution(layer[i], current);
                tasks.add(task);
                task.fork();
            }
            for (int i = 0; i < count; ++i) {
                next[i] = tasks.get(i).join();
            }
            if (!isLast) {
                current = next;
            }
        }
        
        int middle = (DIMENSION >> 1) + 1; // THIS ASSUMES THAT DIMENSION % 2 != 0
        next[2] = new double[][] { { network[network.length - 1][2].calculate(current, middle, middle) } };
        next[3] = new double[][] { { network[network.length - 1][3].calculate(current, middle, middle) } };
        
        return next;
    }

    private static Move chooseMove( Game game, double[][][] dcnnResults, boolean mayNotPass ) {
        List<Evaluation> moves = new ArrayList<>();
        Set<Move> legal = game.getLegalMoves();
        Coordinate previous = getPreviousMove(game);
        for (int row = 0; row < DIMENSION; ++row) {
            for (int column = 0; column < DIMENSION; ++column) {
                if (column > 0) {
                    System.out.print("  ");
                }
                Coordinate current = Coordinate.get(column + 1, row + 1);
                if (legal.contains(current)) {
                    double score = determineScore(dcnnResults[0][row][column], dcnnResults[1][row][column]);
                    System.out.format("%+7.2f", score);
                    double distance = (previous != null ? calculateDistance(current, previous) : 0.);
                    Evaluation evaluation = new Evaluation(current, score, distance);
                    moves.add(evaluation);
                } else {
                    System.out.print("_______");
                }
            }
            System.out.println();
        }
        double passScore = determineScore(dcnnResults[2][0][0], dcnnResults[3][0][0]);
        System.out.format("PASS: %+7.2f\n\n", passScore);
        Evaluation pass = new Evaluation(Pass.PASS, passScore, 0.);
        if (!mayNotPass) {
            moves.add(pass);
        }
        Collections.sort(moves);
        return moves.get(0).move;
    }

    private static List<Evaluation> evaluateMoves( Game game, double[][][] dcnnResults ) {
        List<Evaluation> moves = new ArrayList<>();
        Set<Move> legal = game.getLegalMoves();
        Coordinate previous = getPreviousMove(game);
        for (int row = 0; row < DIMENSION; ++row) {
            for (int column = 0; column < DIMENSION; ++column) {
                Coordinate current = Coordinate.get(column + 1, row + 1);
                if (legal.contains(current)) {
                    double score = determineScore(dcnnResults[0][row][column], dcnnResults[1][row][column]);
                    double distance = (previous != null ? calculateDistance(current, previous) : 0.);
                    Evaluation evaluation = new Evaluation(current, score, distance);
                    moves.add(evaluation);
                }
            }
        }
        return moves;
    }

    private static Coordinate getPreviousMove( Game game ) {
        Game cursor = game;
        Move previous;
        try {
            previous = cursor.getPreviousMove();
            while (previous != null && previous == Pass.PASS) {
                cursor = cursor.getPreviousState();
                previous = cursor.getPreviousMove();
            }
        } catch (IllegalStateException e) {
            previous = null;
        }
        return (Coordinate) previous;
    }

    private static double determineScore( double first, double second ) {
        double score = (first - second) * DIMENSION * DIMENSION;
        double floor = Math.floor(score);
        double fraction = score - floor; // will always generate a positive number; e.g., -3 is the floor of -2.5
        int offset = (int) Math.floor(fraction * 8.);
        double roundedFraction;
        if (offset < 1) {
            roundedFraction = 0.;
        } else if (offset < 3) {
            roundedFraction = 0.25;
        } else if (offset < 5) {
            roundedFraction = 0.5;
        } else if (offset < 7) {
            roundedFraction = 0.75;
        } else {
            roundedFraction = 1.;
        }
        return floor + roundedFraction;
    }

    private static double calculateDistance( Coordinate a, Coordinate b ) {
        double dx = a.getColumn() - b.getColumn();
        double dy = a.getRow() - b.getRow();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static class Evaluation implements Comparable<Evaluation> {
        final Move   move;
        final double score;
        final double distance;
        final double jitter;

        Evaluation( Move move, double score, double distance ) {
            this.move = move;
            this.score = score;
            this.distance = distance;
            this.jitter = ThreadLocalRandom.current().nextDouble();
        }

        @Override
        public int compareTo( Evaluation that ) {
            int result = -Double.compare(score, that.score);
            if (result == 0) {
                result = (move == Pass.PASS ? -1 : 0) + (that.move == Pass.PASS ? 1 : 0);
                if (result == 0 && this.move != Pass.PASS) {
                    result = Double.compare(distance, that.distance);
                    if (result == 0) {
                        result = Double.compare(jitter, that.jitter);
                        if (result == 0) {
                            result = Integer.compare(((Coordinate) move).ordinal(), ((Coordinate) that.move).ordinal());
                        }
                    }
                }
            }
            return result;
        }
    }
}