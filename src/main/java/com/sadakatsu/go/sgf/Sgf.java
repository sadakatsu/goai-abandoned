package com.sadakatsu.go.sgf;

import static com.sadakatsu.go.domain.Pass.PASS;
import static com.sadakatsu.go.domain.intersection.Stone.*;
import static com.sadakatsu.go.domain.outcome.CompleteButNotScored.COMPLETE_BUT_NOT_SCORED;
import static com.sadakatsu.go.domain.outcome.Invalidated.INVALIDATED;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.sadakatsu.go.domain.Coordinate;
import com.sadakatsu.go.domain.Game;
import com.sadakatsu.go.domain.Group;
import com.sadakatsu.go.domain.Move;
import com.sadakatsu.go.domain.intersection.Player;
import com.sadakatsu.go.domain.outcome.Draw;
import com.sadakatsu.go.domain.outcome.Invalidated;
import com.sadakatsu.go.domain.outcome.Outcome;
import com.sadakatsu.go.domain.outcome.Win;

// TODO: This class is overly simplistic.  The SGF4 format allows for many interesting and useful properties.  This
// class supports a bare minimum (and possibly less than that, based upon how I support free handicap placement).
public class Sgf {
    public static void writeToFile( Game game, File destination ) throws IOException {
        String representation = getAreaRepresentation(game);
        
        try {
            Game cursor = game;
            while (true) {
                if (!cursor.isOver()) {
                    representation = representPreviousMove(cursor) + representation;
                }
                cursor = cursor.getPreviousState();
            }
        } catch (IllegalStateException e) {
            // I would say "game over", except we've reached the beginning of the game.  This is fine.
        }
        
        representation = representPreamble(game) + representation;
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(destination))) {
            writer.append("(");
            writer.append(representation);
            writer.append(")");
        } finally {}
    }
    
    private static String getAreaRepresentation( Game game ) {
        StringBuilder representation = new StringBuilder();
        
        Outcome outcome = game.getOutcome();
        if (game.isOver() && outcome != COMPLETE_BUT_NOT_SCORED && outcome != INVALIDATED) {
            Set<Coordinate> blackPoints = new HashSet<>();
            Set<Coordinate> whitePoints = new HashSet<>();
            
            for (Group group : game.getAllGroups()) {
                if (group.type == BLACK) {
                    blackPoints.addAll(group.members);
                } else if (group.type == WHITE) {
                    whitePoints.addAll(group.members);
                } else if (group.bordersBlack && !group.bordersWhite) {
                    blackPoints.addAll(group.members);
                } else if (group.bordersWhite && !group.bordersBlack) {
                    whitePoints.addAll(group.members);
                }
            }
            
            String blackPointString = buildCoordinatePropertyList("TB", blackPoints);
            String whitePointString = buildCoordinatePropertyList("TW", whitePoints);
            
            representation.append(";");
            representation.append(blackPointString);
            representation.append(whitePointString);
            representation.append("C[");
            representation.append(outcome);
            representation.append("]");
        }
        
        return representation.toString();
    }
    
    private static String buildCoordinatePropertyList( String propertyName, Collection<Coordinate> coordinates ) {
        StringBuilder builder = new StringBuilder();
        
        if (coordinates.size() > 0) {
            builder.append(propertyName);
            for (Coordinate coordinate : coordinates) {
                builder.append("[");
                builder.append(getSgfRepresentation(coordinate));
                builder.append("]");
            }
        }
        
        return builder.toString();
    }
    
    private static String getSgfRepresentation( Coordinate coordinate ) {
        return String.format(
            "%c%c",
            convertComponentToSgfCharacter(coordinate.getColumn()),
            convertComponentToSgfCharacter(coordinate.getRow())
        );
    }
    
    private static char convertComponentToSgfCharacter( int component ) {
        return (char) ('a' + component - 1);
    }
    
    private static String representPreviousMove( Game game ) {
        Game previous = game.getPreviousState();
        Player previousPlayer = previous.getCurrentPlayer();
        Move previousMove = game.getPreviousMove();
        return String.format(
            ";%s[%s]",
            previousPlayer == BLACK ? "B" : "W",
            previousMove == PASS ? "tt" : getSgfRepresentation((Coordinate) previousMove)
        );
    }
    
    private static String representPreamble( Game game ) {
        StringBuilder builder = new StringBuilder();
        
        builder.append(";FF[4]GM[1]SZ[");
        builder.append(game.getDimension());
        builder.append("]HA[");
        builder.append(game.getHandicap());
        builder.append("]");
        if (game.getHandicap() > 0) {
            builder.append(buildCoordinatePropertyList("AB", game.getHandicapStonePlacements()));
        }
        builder.append("KM[");
        builder.append(game.getCompensation());
        builder.append("]");
        if (game.getHandicap() > 0) {
            builder.append("PL[W]");
        }
        
        builder.append("RE[");
        
        Outcome outcome = game.getOutcome();
        if (Win.class.equals(outcome.getClass())) {
            Player winner = outcome.getWinner();
            if (winner == BLACK) {
                builder.append("B+");
            } else {
                builder.append("W+");
            }
            builder.append(outcome.getMargin());
        } else if (Draw.class.equals(outcome.getClass())) {
            builder.append("0");
        } else if (outcome == INVALIDATED) {
            builder.append("Void");
        } else {
            builder.append("?");
        }
                        
        builder.append("]");
        
        return builder.toString();
    }
}
