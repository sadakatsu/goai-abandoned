package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.Pass.PASS;
import static com.sadakatsu.util.Time.time;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Game;
import com.sadakatsu.go.domain.Group;
import com.sadakatsu.go.domain.Pass;
import com.sadakatsu.go.domain.Game.GameBuilder;
import com.sadakatsu.go.domain.Move;
import com.sadakatsu.go.domain.intersection.Intersection;
import com.sadakatsu.go.domain.intersection.Player;
import com.sadakatsu.go.domain.intersection.Stone;
import com.sadakatsu.go.sgf.Sgf;
import com.sadakatsu.goai.GoAi.Evaluation;

public class App {
    private static final int BOARD_SIZE = 9; // 19;
    private static final int HIDDEN_LAYERS = 7; // 31;
    private static final int CHANNELS = 64; // 256;
//    private static final int BOARD_SIZE = 19;
//    private static final int HIDDEN_LAYERS = 31;
//    private static final int CHANNELS = 256;
    private static final long SEED =
//        10L;
//        5L; // 11926 moves... probably a lot to be learned
//        74533897544435L;
//        74665133788022L;
        System.nanoTime();
//        25690675367014L;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    
    public static void main( String[] args ) throws Exception {
        GoAi ai = createAi();
        Game game = createGame();
        ScoreRecommender recommender = new BestRawScoreRecommender();
        
        boolean avoidPassing = false;
        do {
            Evaluation results = null;
            
            // Play out the game until both sides want to pass.
            while (!game.isOver()) {
                LOGGER.info("Game's current state:\n\n{}", game);
                results = evaluateGame(ai, game);
                Score selectedMove;
                if (avoidPassing) {
                    selectedMove = recommender.recommendGamePlayAvoidingPass(results).get();
                } else {
                    selectedMove = recommender.recommendGamePlay(results).get();
                }
                
                LOGGER.info("Game evaluation:\n\n{}", representEvaluation(results, selectedMove, false));
                game = game.play(selectedMove.getMove());
                if (avoidPassing && selectedMove.getMove() != PASS) {
                    avoidPassing = false;
                }
            }
            
            // Play out all moves that do not in the players' opinions change the result until no such further moves are
            // found.  All groups that have all their stones left on the board in this playout are alive; all that have
            // been captured are dead.  If there are any groups that have lost only a subset of their stones, we have a
            // conflict that will require more play to resolve.
            LOGGER.info("Game's state before dead stone search:\n\n{}", game);
            Game resolution = game.resume();
            
            // TODO: This method of setting the minimum acceptable bar for playing moves assumes that the AI correctly
            // evaluates the pass's score, then reuses that score.  Depending upon how training goes, I may need to make
            // this playout check the pass score for every move.
            Score lastPass = getPassScoreFrom(results.getScores());
            Map<Player, Score> noWorseThan = new HashMap<>();
            noWorseThan.put(resolution.getCurrentPlayer(), lastPass);
            noWorseThan.put(resolution.getCurrentPlayer().getOpposite(), lastPass.invert());
            
            while (!resolution.isOver()) {
                LOGGER.info("Game dead stone search state:\n\n{}\n", indent(resolution));
                Evaluation evaluation = evaluateGame(ai, resolution);
                Score selected = recommender.recommendResolutionPlay(
                    evaluation,
                    noWorseThan.get(resolution.getCurrentPlayer())
                ).get();
                LOGGER.info("Dead stone search evaluation:\n\n{}", representEvaluation(evaluation, selected, true));
                resolution = resolution.play(selected.getMove());
            }
            
            boolean contradiction = false;
            Set<Group> deadGroups = new HashSet<>();
            for (Group group : game.getGroupsOfStones()) {
                Boolean isDead = null;
                Intersection expected = group.type;
                for (Coordinate coordinate : group.members) {
                    Intersection actual = resolution.get(coordinate);
                    boolean flagAsDead = !expected.equals(actual);
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
                LOGGER.info("The dead stone search resulted in a contradiction; resumed play is now forced.");
                game = game.resume();
                avoidPassing = true;
            } else {
                game = game.score(deadGroups);
                LOGGER.info("Game's final state:\n\n{}", game);
            }
        } while (!game.isOver());
        
        LOGGER.info("Writing Game to SGF...");
        Path directory = Paths.get("./sgfs");
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        
        String filename = DateTimeFormat.forPattern("yyyy-MM-dd-HH-mm-ss-SSS").print(DateTime.now());
        Sgf.writeToFile(game, new File("sgfs/" + filename + ".sgf"));
        LOGGER.info("Done.");
    }
    
    private static Score getPassScoreFrom( List<Score> scores ) {
        return scores.stream().filter(score -> PASS == score.getMove()).findFirst().get();
    }
    
    private static GoAi createAi() throws Exception {
        return time(() -> new GoAi(BOARD_SIZE, HIDDEN_LAYERS, CHANNELS, SEED), "App.createAi()");
    }
    
    private static Game createGame() throws Exception {
        return time(() -> Game.newBuilder(BOARD_SIZE).build(), "App.createGame()");
    }
    
    private static Evaluation evaluateGame( GoAi ai, Game game ) throws Exception {
        return time(() -> ai.evaluate(game, true), "GoAi.evaluate(game)");
    }
    
    private static String representEvaluation( Evaluation calculations, Score selected, boolean indent ) {
        StringBuilder builder = new StringBuilder();
        
        List<Score> scores = calculations.getScores();
        
        Map<Move, Score> moves = new HashMap<>();
        for (Score score : scores) {
            moves.put(score.getMove(), score);
        }
        
        for (int row = 0; row < BOARD_SIZE; ++row) {
            if (indent) {
                builder.append("\t");
            }
            
            for (int column = 0; column < BOARD_SIZE; ++column) {
                if (column > 0) {
                    builder.append(" ");
                }
                
                Coordinate coordinate = Coordinate.get(column + 1, row + 1);
                builder.append(representScore(coordinate, moves, selected));
            }
            builder.append("\n");
        }
        
        if (indent) {
            builder.append("\t");
        }
        
        builder.append("PASS:       ");
        builder.append(representScore(PASS, moves, selected));
        builder.append("\n");
        
        return builder.toString();
    }
    
    private static String representScore( Move move, Map<Move, Score> legalMoves, Score selected ) {
        String representation = "___________";
        
        if (legalMoves.containsKey(move)) {
            String equivalentPrefix = " ";
            String equivalentSuffix = " ";
            String selectedPrefix = " ";
            String selectedSuffix = " ";
            
            Score currentScore = legalMoves.get(move);
            double result = currentScore.getReadableResult();
            if (currentScore.equals(selected)) {
                selectedPrefix = "<";
                selectedSuffix = ">";
            }
            if (result == selected.getReadableResult()) {
                equivalentPrefix = "<";
                equivalentSuffix = ">";
            }
            
            representation = String.format(
                "%s%s%+7.2f%s%s",
                selectedPrefix,
                equivalentPrefix,
                result,
                equivalentSuffix,
                selectedSuffix
            );
        }
        
        return representation;
    }
    
    private static String indent( Game game ) {
        String representation = game.toString();
        String[] lines = representation.split("[\\s&&[^ ]]+");
        for (int i = 0; i < lines.length; ++i) {
            lines[i] = "\t" + lines[i];
        }
        return String.join("\n", lines);
    }
}
