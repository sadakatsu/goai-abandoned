package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.Pass.PASS;
import static com.sadakatsu.goai.GoAi.InputChannels.*;
import static com.sadakatsu.util.Time.time;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Game;
import com.sadakatsu.go.domain.Group;
import com.sadakatsu.go.domain.Move;
import com.sadakatsu.go.domain.intersection.Intersection;
import com.sadakatsu.go.domain.intersection.Player;

public final class GoAi {
    public final class Evaluation {
        private double[][][]   expected;
        private double[][][]   input;
        private double[][][][] activations;
        private double[][][][] errors;
        private Game           game;
        private int[][][][]    orientations;
        private Set<Move>      legalMoves;
        
        // TODO: I want to include on-board and off-board channels for every activation layer except the last one.
        private Evaluation( Game game, double[][][] input ) {
            this.game = game;
            this.input = input;
            
            activations = buildDoubleStructure(true);
            orientations = buildIntStructure();
            legalMoves = game.getLegalMoves();
        }
        
        private double[][][][] buildDoubleStructure( boolean shouldPad ) {
            double[][][][] structure = new double[layers][][][];
            for (int layer = 0; layer < layers; ++layer) {
                if (layer < layers - 1) {
                    // The last hidden layer before the output layer should not pad its output.  The idea is to borrow
                    // DeepMind's idea of "convolving" only a single cell's penultimate features to generate its
                    // composite feature for the play values, or to use fully-connected neurons for the pass values.
                    int padding = shouldPad && layer < layers - 2 ? 1 : 0;
                    structure[layer] = getHiddenLayerDoubleArray(padding);
                } else {
                    structure[layer] = getOutputLayerDoubleArray();
                }
            }
            return structure;
        }
        
        private double[][][] getHiddenLayerDoubleArray( int padding ) {
            int breadth = boardSize + padding * 2;
            return new double[channels][breadth][breadth];
        }
        
        private double[][][] getOutputLayerDoubleArray() {
            return new double[][][] {
                new double[boardSize][boardSize],
                new double[boardSize][boardSize],
                new double[1][1],
                new double[1][1]
            };
        }
        
        private int[][][][] buildIntStructure() {
            int[][][][] structure = new int[layers][][][];
            for (int layer = 0; layer < layers; ++layer) {
                if (layer < layers - 1) {
                    structure[layer] = getHiddenLayerIntArray();
                } else {
                    structure[layer] = getOutputLayerIntArray();
                }
            }
            return structure;
        }
        
        private int[][][] getHiddenLayerIntArray() {
            return new int[channels][boardSize][boardSize];
        }
        
        private int[][][] getOutputLayerIntArray() {
            return new int[][][] {
                new int[boardSize][boardSize],
                new int[boardSize][boardSize],
                new int[1][1],
                new int[1][1]
            };
        }
        
        private void prepareForBackpropagation( double[][][] expected ) {
            this.expected = expected;
            errors = buildDoubleStructure(false);
        }
        
        public Game getGame() {
            return game;
        }
        
        public List<Score> getScores() {
            List<Score> scores = new ArrayList<>();
            
            double[][][] output = activations[layers - 1]; 
            for (Move move : legalMoves) {
                Score score = getScoreFor(move, output);
                scores.add(score);
            }
            
            return scores;
        }
        
        private Score getScoreFor( Move move, double[][][] output ) {
            double[][] playerChannel = null;
            double[][] opponentChannel = null;
            int column = 0;
            int row = 0;
            
            if (move != PASS) {
                playerChannel = output[0];
                opponentChannel = output[1];
                
                Coordinate coordinate = (Coordinate) move;
                column = coordinate.getColumn() - 1;
                row = coordinate.getRow() - 1;
            } else {
                playerChannel = output[2];
                opponentChannel = output[3];
            }
            
            double rawPlayerScore = playerChannel[row][column];
            double rawOpponentScore = opponentChannel[row][column];
            return Score.createFromRawScores(move, rawPlayerScore, rawOpponentScore);
        }
        
        public Score getScoreFor( Move move ) {
            if (!legalMoves.contains(move)) {
                String message = String.format("%s is not a legal move for the evaluated Game:\n%s", move, game);
                throw new IllegalArgumentException(message);
            }
            
            return getScoreFor(move, activations[layers - 1]);
        }
    }
    
    private abstract class Neuron {
        double bias;
        double[][][] weights;
        final int channels;
        final int width;
        
        Neuron( int channels, int width ) {
            final double scale = 1. / Math.sqrt(channels * width * width);
            
            this.channels = channels;
            this.width = width;
            this.weights = new double[channels][width][width];
            this.bias = random.nextGaussian() * scale;
            
            for (int channel = 0; channel < channels; ++channel) {
                double[][] channelWeights = weights[channel];
                for (int row = 0; row < width; ++row) {
                    double[] rowWeights = channelWeights[row];
                    for (int column = 0; column < width; ++column) {
                        rowWeights[column] = random.nextGaussian() * scale;
                    }
                }
            }
        }
        
        abstract void calculate(
            double[][][] input,
            int startRow,
            int startColumn,
            double[][] activation,
            int[][] orientation
        );
        
        // TODO: I probably need to add methods that help determine a Neuron's weights contributions to an error and to
        // update the bias and weights from the calculated errors.  I don't have ideas for them right now, so I will
        // come back to them later.
    }
    
    // Depending upon its configuration and use, SimpleNeuron can either be a fully connected neuron or a convolution.
    // Using it as a fully-connected neuron requires setting the width to the full width of the input channels, then
    // setting calculate()'s startRow and startColumn to 0.  Using it as a convolution requires setting a width less
    // than the channel's width, then convolving it over the input.  This allows the same code to support both
    // operations.
    private class SimpleNeuron extends Neuron {
        SimpleNeuron( int channels, int width ) {
            super(channels, width);
        }

        @Override
        void calculate(
            double[][][] input,
            int startRow,
            int startColumn,
            double[][] activation,
            int[][] orientation
        ) {
            double result = bias;
            
            for (int channel = 0; channel < channels; ++channel) {
                double[][] inputChannel = input[channel];
                double[][] weightChannel = weights[channel];
                
                for (int rowIndex = 0, row = startRow; rowIndex < width; ++rowIndex, ++row) {
                    double[] inputRow = inputChannel[row];
                    double[] weightRow = weightChannel[rowIndex];
                    
                    for (int columnIndex = 0, column = startColumn; columnIndex < width; ++columnIndex, ++column) {
                        result += inputRow[column] * weightRow[columnIndex];
                    }
                }
            }
            
            activation[startRow][startColumn] = result;
        }
    }
    
    private class RotationInvariantNeuron extends Neuron {
        final int outputPadding;
        
        RotationInvariantNeuron( int channels, int width, int outputPadding ) {
            super(channels, width);
            this.outputPadding = outputPadding;
        }
        
        @Override
        void calculate(
            double[][][] input,
            int startRow,
            int startColumn,
            double[][] activation,
            int[][] orientation
        ) {
            double[] results = new double[] { bias, bias, bias, bias, bias, bias, bias, bias };
            
            for (int channel = 0; channel < channels; ++channel) {
                double[][] inputChannel = input[channel];
                double[][] weightChannel = weights[channel];
                
                for (
                    int rowIndex = 0, oppositeRowIndex = width - 1, row = startRow;
                    rowIndex < width;
                    ++rowIndex, --oppositeRowIndex, ++row
                ) {
                    double[] inputRow = inputChannel[row];
                    double[] weightsByRow = weightChannel[rowIndex];
                    double[] weightsByRowOpposite = weightChannel[oppositeRowIndex];
                    
                    for (
                        int columnIndex = 0, oppositeColumnIndex = width - 1, column = startColumn;
                        columnIndex < width;
                        ++columnIndex, --oppositeColumnIndex, ++column
                    ) {
                        double inputValue = inputRow[column];
                        double[] weightsByColumn = weightChannel[columnIndex];
                        double[] weightsByColumnOpposite = weightChannel[oppositeColumnIndex];
                        
                        results[0] += inputValue * weightsByRow[columnIndex];
                        results[1] += inputValue * weightsByRow[oppositeColumnIndex];
                        results[2] += inputValue * weightsByRowOpposite[columnIndex];
                        results[3] += inputValue * weightsByRowOpposite[oppositeColumnIndex];
                        results[4] += inputValue * weightsByColumn[rowIndex];
                        results[5] += inputValue * weightsByColumn[oppositeRowIndex];
                        results[6] += inputValue * weightsByColumnOpposite[rowIndex];
                        results[7] += inputValue * weightsByColumnOpposite[oppositeRowIndex];
                    }
                }
            }
            
            double result = results[0];
            int selected = 0;
            for (int i = 1; i < 8; ++i) {
                double current = results[i];
                if (current > result) {
                    result = current;
                    selected = i;
                }
            }
            
            activation[startRow + outputPadding][startColumn + outputPadding] = result;
            orientation[startRow][startColumn] = selected;
        }
    }
    
    private class NeuronRunner extends RecursiveAction {
        private static final long serialVersionUID = 4511259319400548673L;
        
        Evaluation workspace;
        int channel;
        int layer;
        Neuron convolution;
        
        public NeuronRunner(
            Neuron convolution,
            int layer,
            int channel,
            Evaluation workspace
        ) {
            this.channel = channel;
            this.convolution = convolution;
            this.layer = layer;
            this.workspace = workspace;
        }
        
        @Override
        protected void compute() {
            runConvolution(convolution, layer, channel, workspace);
        }
    }
    
    // TODO: It is good to capture when a 
    static enum InputChannels {
        ON_BOARD,
        OFF_BOARD,
        PLAYER_STONE,
        OPPONENT_STONE,
        NO_STONE,
        LEGAL_MOVE,
        ILLEGAL_MOVE,
        OPPONENT_COULD_PLAY_AFTER_PASS,
        OPPONENT_COULD_NOT_PLAY_AFTER_PASS,
        OPPONENT_COULD_PLAY_IF_PASS_DID_NOT_END_GAME,
        OPPONENT_COULD_NOT_PLAY_IF_PASS_DID_NOT_END_GAME,
        LIBERTIES_ONE,
        LIBERTIES_TWO,
        LIBERTIES_THREE,
        LIBERTIES_FOUR,
        LIBERTIES_FIVE,
        LIBERTIES_SIX,
        LIBERTIES_SEVEN,
        LIBERTIES_EIGHT_OR_MORE;
        
        private static final int count = values().length;
    }
    
    private static void runConvolution( Neuron convolution, int layer, int channel, Evaluation workspace ) {
        double[][][] input = layer == 0 ? workspace.input : workspace.activations[layer - 1];
        double[][] activation = workspace.activations[layer][channel];
        int[][] orientation = workspace.orientations[layer][channel];
        
        int last = input[0].length - convolution.width;
        for (int row = 0; row <= last; ++row) {
            for (int column = 0; column <= last; ++column) {
                convolution.calculate(input, row, column, activation, orientation);
            }
        }
    }
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GoAi.class);
    private static final boolean LOG = true;
    private static final boolean TIME = true;
    
    private final int boardSize;
    private final int layers;
    private final int channels;
    private final long seed;
    private final Neuron[][] network;
    private final Random random;
    
    public GoAi( int boardSize, int hiddenLayers, int channels ) {
        this(boardSize, hiddenLayers, channels, System.nanoTime());
    }
    
    public GoAi( int boardSize, int hiddenLayers, int channels, long seed ) {
        validateBoardSize(boardSize);
        validateHiddenLayers(hiddenLayers);
        validateChannels(channels);
        
        if (LOG) {
            LOGGER.debug(
                "Instantiating GoAi with boardSize {}, hiddenLayers {}, channels {}, and seed {}",
                boardSize,
                hiddenLayers,
                channels,
                seed
            );
        }
        
        this.boardSize = boardSize;
        this.channels = channels;
        this.layers = hiddenLayers + 1;
        this.seed = seed;
        this.random = new Random(seed);
        
        try {
            network = buildNetwork();
        } catch (OutOfMemoryError e) {
            throw new IllegalArgumentException(
                "The arguments specify a GoAi that is to large to fit in the heap.  Either shrink this AI by lowering" +
                " its inputs, or increase Java's heap space.",
                e
            );
        }
    }
    
    private void validateBoardSize( int boardSize ) {
        if (boardSize < 1 || boardSize > 19) {
            String message = String.format(
                "The boardSize must be between 1 and 19 inclusive, but it was %d.",
                boardSize
            );
            throw new IllegalArgumentException(message);
        }
    }
    
    private void validateHiddenLayers( int hiddenLayers ) {
        if (hiddenLayers < 0) {
            String message = String.format(
                "The hiddenLayers must be at least 0, but it was %d.",
                hiddenLayers
            );
            throw new IllegalArgumentException(message);
        }
    }
    
    private void validateChannels( int channels ) {
        if (channels < 1) {
            String message = String.format(
                "The channels must be at least 0, but it was %d.",
                channels
            );
            throw new IllegalArgumentException(message);
        }
    }
    
    private Neuron[][] buildNetwork() {
        Neuron[][] network = new Neuron[layers][];
        
        for (int layer = 0; layer < layers; ++layer) {
            boolean isFirstLayer = layer == 0;
            boolean isOutputLayer = layer == layers - 1;
            boolean shouldPadOutput = layer < layers - 2;
            
            int inputChannels = isFirstLayer ? InputChannels.count : channels;
            
            if (isOutputLayer) {
                network[layer] = new Neuron[] {
                    new SimpleNeuron(inputChannels, 1),
                    new SimpleNeuron(inputChannels, 1),
                    new SimpleNeuron(inputChannels, boardSize),
                    new SimpleNeuron(inputChannels, boardSize)
                };
            } else {
                int kernelWidth = isFirstLayer ? 5 : 3;
                int outputPadding = shouldPadOutput ? 1 : 0;
                
                network[layer] = new Neuron[channels];
                for (int channel = 0; channel < channels; ++channel) {
                    network[layer][channel] = new RotationInvariantNeuron(inputChannels, kernelWidth, outputPadding);
                }
            }
        }
        
        return network;
    }
    
    public int getBoardSize() {
        return boardSize;
    }
    
    public int getChannels() {
        return channels;
    }
    
    public int getLayers() {
        return layers;
    }
    
    public long getSeed() {
        return seed;
    }
    
    public Evaluation evaluate( Game game ) {
        return evaluate(game, true);
    }
    
    public Evaluation evaluate( Game game, boolean parallelize ) {
        validateGame(game);
        
        final Evaluation result;
        try {
            if (TIME) {
                double[][][] input = time(() -> encode(game), "GoAi.encode(Game)");
                result = time(() -> new Evaluation(game, input), "Evaluation constructor");
                if (parallelize) {
                    time(() -> runNetworkWithParallelizedLayers(result), "neural network with parallelized layers");
                } else {
                    time(() -> runNetworkSynchronously(result), "neural network with synchronous layers");
                }
            } else {
                double[][][] input = encode(game);
                result = new Evaluation(game, input);
                if (parallelize) {
                    runNetworkWithParallelizedLayers(result);
                } else {
                    runNetworkSynchronously(result);
                }
            }
        } catch (Exception e) {
            String message = String.format("Evaluating a Game should never fail, but it failed for\n%s", game);
            throw new IllegalStateException(message, e);
        }
        
        return result;
    }
    
    private void validateGame( Game game ) {
        if (game == null || game.isOver()) {
            String message = String.format("Received an unevaluatable game:\n%s", game);
            throw new IllegalArgumentException(message);
        }
    }
    
    private double[][][] encode( Game game ) throws Exception {
        final double[][][] encoding;
        
        int padding = layers > 1 ? 2 : 0;
        int breadth = boardSize + padding * 2;
        
        encoding = new double[InputChannels.count][breadth][breadth];
        prepareOnBoardChannel(encoding, padding);
        prepareOffBoardChannel(encoding, padding);
        populateGameSpecificChannel(game, encoding, padding);
        
        return encoding;
    }
    
    private void prepareOnBoardChannel( double[][][] encoding, int offset ) {
        int channel = ON_BOARD.ordinal();
        for (Coordinate coordinate : Coordinate.iterateOverBoard(boardSize)) {
            int row = coordinate.getRow() - 1 + offset;
            int column = coordinate.getColumn() - 1 + offset;
            encoding[channel][row][column] = 1.;
        }
    }
    
    private void prepareOffBoardChannel( double[][][] encoding, int offset ) {
        if (offset > 0) {
            int channel = OFF_BOARD.ordinal();
            int breadth = encoding[channel].length;
            int edge = boardSize - 1 + offset;
            for (int row = 0; row < breadth; ++row) {
                for (int column = 0; column < breadth; ++column) {
                    if (row < offset || row > edge || column < offset || column > edge) {
                        encoding[channel][row][column] = 1.;
                    }
                }
            }
        }
    }
    
    private void populateGameSpecificChannel( Game game, double[][][] encoding, int offset ) {
        int channels = InputChannels.count;
        Player player = game.getCurrentPlayer();
        Player opponent = player.getOpposite();
        Set<Move> legalMoves = game.getLegalMoves();
        
        Game afterPass = game.pass();
        Set<Move> opponentMovesAfterPass = afterPass.getLegalMoves();
        
        Set<Move> opponentMovesAfterPlay;
        if (game.wouldPassEndGame()) {
            Game beforePass = game.getPreviousState(); // before opponent passed
            opponentMovesAfterPlay = beforePass.getLegalMoves();
        } else {
            opponentMovesAfterPlay = opponentMovesAfterPass;
        }
        
        for (Coordinate coordinate : Coordinate.iterateOverBoard(boardSize)) {
            int row = coordinate.getRow() - 1 + offset;
            int column = coordinate.getColumn() - 1 + offset;
            
            Intersection value = game.get(coordinate);
            if (value.equals(player)) {
                encoding[PLAYER_STONE.ordinal()][row][column] = 1.;
            } else if (value.equals(opponent)) {
                encoding[OPPONENT_STONE.ordinal()][row][column] = 1.;
            } else {
                encoding[NO_STONE.ordinal()][row][column] = 1.;
            }
            
            if (legalMoves.contains(coordinate)) {
                encoding[LEGAL_MOVE.ordinal()][row][column] = 1.;
            } else {
                encoding[ILLEGAL_MOVE.ordinal()][row][column] = 1.;
            }
            
            if (opponentMovesAfterPass.contains(coordinate)) {
                encoding[OPPONENT_COULD_PLAY_AFTER_PASS.ordinal()][row][column] = 1.;
            } else {
                encoding[OPPONENT_COULD_NOT_PLAY_AFTER_PASS.ordinal()][row][column] = 1.;
            }
            
            if (opponentMovesAfterPlay.contains(coordinate)) {
                encoding[OPPONENT_COULD_PLAY_IF_PASS_DID_NOT_END_GAME.ordinal()][row][column] = 1.;
            } else {
                encoding[OPPONENT_COULD_NOT_PLAY_IF_PASS_DID_NOT_END_GAME.ordinal()][row][column] = 1.;
            }
        }
        
        for (Group group : game.getGroupsOfStones()) {
            int channel = group.liberties + LIBERTIES_ONE.ordinal() - 1;
            if (channel >= channels) {
                channel = channels - 1;
            }
            
            for (Coordinate member : group.members) {
                int row = member.getRow() - 1 + offset;
                int column = member.getColumn() - 1 + offset;
                encoding[channel][row][column] = 1.;
            }
        }
    }
    
    private void runNetworkSynchronously( Evaluation workspace ) {
        for (int layer = 0; layer < layers; ++layer) {
            Neuron[] currentLayer = network[layer];
            
            int count = layer < layers - 1 ? channels : 4;
            for (int channel = 0; channel < count; ++channel) {
                Neuron convolution = currentLayer[channel];
                GoAi.runConvolution(convolution, layer, channel, workspace);
            }
        }
    }
    
    private void runNetworkWithParallelizedLayers( Evaluation workspace ) {
        List<RecursiveAction> tasks = new ArrayList<>();
        
        for (int layer = 0; layer < layers; ++layer) {
            Neuron[] currentLayer = network[layer];
            
            tasks.clear();
            int count = layer < layers - 1 ? channels : 4;
            for (int channel = 0; channel < count; ++channel) {
                Neuron convolution = currentLayer[channel];
                NeuronRunner task = new NeuronRunner(convolution, layer, channel, workspace);
                task.fork();
                tasks.add(task);
            }
            
            for (RecursiveAction task : tasks) {
                task.join();
            }
        }
        
        tasks.clear();
        
    }
}
