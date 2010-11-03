/*
 * Copyright (c) 2010.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.playground.gatk.walkers.genotyper;

import org.apache.log4j.Logger;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.contexts.StratifiedAlignmentContext;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.utils.pileup.ReadBackedPileup;
import org.broadinstitute.sting.utils.pileup.PileupElement;
import org.broad.tribble.util.variantcontext.Genotype;

import java.io.PrintStream;
import java.util.*;


/**
 * The model representing how we calculate a genotype given the priors and a pile
 * of bases and quality scores
 */
public abstract class AlleleFrequencyCalculationModel implements Cloneable {

    public enum Model {
        EXACT,
        GRID_SEARCH
    }

    protected int N;

    protected Logger logger;
    protected PrintStream verboseWriter;

    protected enum GenotypeType { AA, AB, BB }

    protected static final double VALUE_NOT_CALCULATED = -1.0 * Double.MAX_VALUE;

    protected class CalculatedAlleleFrequency {

        public double log10PNonRef;
        public int altAlleles;

        public CalculatedAlleleFrequency(double log10PNonRef, int altAlleles) {
            this.log10PNonRef = log10PNonRef;
            this.altAlleles = altAlleles;
        }
    }

    protected AlleleFrequencyCalculationModel(int N, Logger logger, PrintStream verboseWriter) {
        this.N = N;
        this.logger = logger;
        this.verboseWriter = verboseWriter;
    }

    /**
     * Must be overridden by concrete subclasses
     * @param tracker                         rod data
     * @param ref                             reference context
     * @param GLs                             genotype likelihoods
     * @param log10AlleleFrequencyPriors      priors
     * @param log10AlleleFrequencyPosteriors  array (pre-allocated) to store results
     */
    protected abstract void getLog10PNonRef(RefMetaDataTracker tracker,
                                            ReferenceContext ref,
                                            Map<String, BiallelicGenotypeLikelihoods> GLs,
                                            double[] log10AlleleFrequencyPriors,
                                            double[] log10AlleleFrequencyPosteriors);

    /**
     * Can be overridden by concrete subclasses
     * @param contexts             alignment contexts
     * @param GLs                  genotype likelihoods
     * @param log10AlleleFrequencyPosteriors    allele frequency results
     * @param AFofMaxLikelihood    allele frequency of max likelihood
     *
     * @return calls
     */
    protected abstract Map<String, Genotype> assignGenotypes(Map<String, StratifiedAlignmentContext> contexts,
                                                             Map<String, BiallelicGenotypeLikelihoods> GLs,
                                                             double[] log10AlleleFrequencyPosteriors,
                                                             int AFofMaxLikelihood);

    protected int getFilteredDepth(ReadBackedPileup pileup) {
        int count = 0;
        for ( PileupElement p : pileup ) {
            if ( DiploidSNPGenotypeLikelihoods.usableBase(p, true) )
                count++;
        }

        return count;
    }
}