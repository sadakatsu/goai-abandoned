package com.sadakatsu.goai;

import static com.sadakatsu.go.domain.Pass.PASS;
import static com.sadakatsu.goai.ScoreRecommender.Relationship.*;

import com.sadakatsu.goai.GoAi.Evaluation;

public class BestRawScoreRecommender extends ScoreRecommender {
    @Override
    protected RecommenderComparisonState buildComparisonState( Evaluation evaluation ) {
        return new RecommenderComparisonState(evaluation);
    }

    @Override
    protected Relationship compareScores( Score first, Score second, RecommenderComparisonState state ) {
        Relationship relationship;
        
        int comparison = Double.compare(first.getRawResult(), second.getRawResult());
        if (comparison == 0) {
            comparison = Double.compare(first.getRawPlayerScore(), second.getRawPlayerScore());
            if (comparison == 0) {
                comparison = (first.getMove() == PASS ? 1 : 0) + (second.getMove() == PASS ? -1 : 0);
            }
        }
        
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
        return false;
    }
}
