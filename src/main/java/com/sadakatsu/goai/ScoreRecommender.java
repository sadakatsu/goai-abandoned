package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.Pass.PASS;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sadakatsu.goai.GoAi.Evaluation;

public abstract class ScoreRecommender {
    static enum Relationship {
        FIRST_IS_BETTER_THAN_SECOND,
        BOTH_ARE_EQUIVALENT,
        SECOND_IS_BETTER_THAN_FIRST;
    }
    
    private static final Score AVOID_PASSING = Score.createFromReadableScores(
        PASS,
        -Double.MAX_VALUE / 2 + Double.MIN_NORMAL,
        Double.MAX_VALUE / 2 - Double.MIN_NORMAL
    );
    
    public Set<Score> recommendEquivalentGamePlays( Evaluation evaluation ) {
        validateEvaluation(evaluation);
        RecommenderComparisonState state = buildComparisonState(evaluation);
        return recommendScores(state);
    }
    
    private void validateEvaluation( Evaluation evaluation ) {
        if (evaluation == null) {
            throw new IllegalArgumentException("The Evaluation may not be null.");
        }
    }
    
    protected abstract RecommenderComparisonState buildComparisonState( Evaluation evaluation );
    
    private Set<Score> recommendScores( RecommenderComparisonState state ) {
        Set<Score> best = new HashSet<>();
        
        Evaluation evaluation = state.getEvaluation();
        Collection<Score> scores = evaluation.getScores();
        for (Score candidate : scores) {
            boolean keep = true;
            
            Iterator<Score> iterator = best.iterator();
            while (keep && iterator.hasNext()) {
                Score opponent = iterator.next();
                Relationship relationship = compareScores(candidate, opponent, state);
                if (relationship == Relationship.SECOND_IS_BETTER_THAN_FIRST) {
                    keep = false;
                } else if (relationship == Relationship.FIRST_IS_BETTER_THAN_SECOND) {
                    iterator.remove();
                }
            }
            
            if (keep) {
                best.add(candidate);
            }
        }
        
        return best;
    }
    
    public Optional<Score> recommendGamePlay( Evaluation evaluation ) {
        Optional<Score> best = Optional.empty();
        
        validateEvaluation(evaluation);
        RecommenderComparisonState state = buildComparisonState(evaluation);
        Set<Score> scores = recommendScores(state);
        for (Score score : scores) {
            if (!best.isPresent() || doesFirstLoseTie(best.get(), score, state)) {
                best = Optional.of(score);
            }
        }
        
        return best;
    }
    
    public Set<Score> recommendEquivalentResolutionPlays( Evaluation evaluation, Score noWorseThan ) {
        validateEvaluation(evaluation);
        validateScore(noWorseThan);
        
        RecommenderComparisonState state = buildComparisonState(evaluation);
        return getResolutionPlaySet(state, noWorseThan);
    }
    
    private Set<Score> getResolutionPlaySet( RecommenderComparisonState state, Score noWorseThan ) {
        Set<Score> scores = recommendScores(state);
        Set<Score> resolutions = buildResolutionPlaySet(scores, noWorseThan, state);
        if (resolutions.size() == 0) {
            resolutions = getPassSetFrom(scores);
        }
        return resolutions;
    }
    
    private void validateScore( Score score ) {
        if (score == null) {
            throw new IllegalArgumentException("ScoreRecommender cannot receive null Scores for any arguments.");
        }
    }
    
    private Set<Score> buildResolutionPlaySet( Set<Score> scores, Score noWorseThan, RecommenderComparisonState state ) {
        Stream<Score> stream = scores.stream();
        Stream<Score> filtered = stream.filter((score) -> isGoodResolutionPlay(score, noWorseThan, state));
        Set<Score> collected = filtered.collect(Collectors.toSet());
        return new HashSet<>(collected);
    }
    
    private boolean isGoodResolutionPlay( Score score, Score noWorseThan, RecommenderComparisonState state ) {
        return !(isPass(score) || doesFirstLoseTie(score, noWorseThan, state)); 
    }
    
    private boolean isPass( Score score ) {
        return PASS == score.getMove();
    }
    
    private Set<Score> getPassSetFrom( Set<Score> scores ) {
        Stream<Score> stream = scores.stream();
        Stream<Score> filtered = stream.filter(this::isPass);
        Set<Score> collected = filtered.collect(Collectors.toSet());
        return new HashSet<>(collected);
    }
    
    public Optional<Score> recommendResolutionPlay( Evaluation evaluation, Score noWorseThan ) {
        validateEvaluation(evaluation);
        validateScore(noWorseThan);
        
        RecommenderComparisonState state = buildComparisonState(evaluation);
        Set<Score> resolutions = getResolutionPlaySet(state, noWorseThan);
        Stream<Score> stream = resolutions.stream();
        return stream.reduce((first, second) -> doesFirstLoseTie(first, second, state) ? second : first);
    }
    
    public Optional<Score> recommendGamePlayAvoidingPass( Evaluation evaluation ) {
        return recommendResolutionPlay(evaluation, AVOID_PASSING);
    }
    
    protected abstract Relationship compareScores(
        Score first,
        Score second,
        RecommenderComparisonState state
    );
    
    protected abstract boolean doesFirstLoseTie(
        Score first,
        Score second,
        RecommenderComparisonState state
    );
}
