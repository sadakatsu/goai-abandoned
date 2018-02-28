package com.sadakatsu.goai;

import com.sadakatsu.goai.GoAi.Evaluation;

public class RecommenderComparisonState {
    private final Evaluation evaluation;

    public RecommenderComparisonState( Evaluation evaluation ) {
        validateEvaluation(evaluation);
        this.evaluation = evaluation;
    }
    
    private void validateEvaluation( Evaluation evaluation ) {
        if (evaluation == null) {
            throw new IllegalArgumentException("The Evaluation may not be null.");
        }
    }
    
    public Evaluation getEvaluation() {
        return evaluation;
    }
}