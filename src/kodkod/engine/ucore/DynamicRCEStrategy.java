/* 
 * Kodkod -- Copyright (c) 2005-2008, Emina Torlak
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package kodkod.engine.ucore;

import java.util.Arrays;
import java.util.Iterator;

import kodkod.engine.fol2sat.TranslationLog;
import kodkod.engine.fol2sat.Translator;
import kodkod.engine.satlab.Clause;
import kodkod.engine.satlab.ReductionStrategy;
import kodkod.engine.satlab.ResolutionTrace;
import kodkod.util.ints.IntBitSet;
import kodkod.util.ints.IntIterator;
import kodkod.util.ints.IntSet;
import kodkod.util.ints.Ints;
import kodkod.util.ints.SparseSequence;
import kodkod.util.ints.TreeSequence;

/**
 * Dynamic Recycling Core Extraction is a strategy for generating unsat cores that are minimal at the logic level.  
 * Specifically, let C be a core that is minimal according to this strategy, 
 * and let F(C) be the top-level logic constraints
 * corresponding to C.  Then, this strategy guarantees that there is no clause
 * c in C such that F(C - c) is a strict subset of F(C). Furthermore, it also
 * guarantees that for all f in F(C), F(C) - f is satisfiable.  This is a stronger
 * guarantee than that of {@linkplain HybridStrategy}.  In general, using this strategy
 * is more expensive, timewise, than using {@linkplain HybridStrategy}.
 * 
 * <p>Like Adaptive RCE, DRCE is parameterized with 3 values that control the amount of recycling.  The
 * first is the <tt>noRecycleRatio</tt>, which completely disables recycling if it is greater than
 * the ratio of the size of the core passed to {@linkplain #next(ResolutionTrace)} and the number of axioms in the 
 * trace.  The default value is .03; if the core makes up only 3 percent of the axioms, no recycling 
 * will happen.  The remaining two parameters are the <tt>recycleLimit</tt> and the <tt>hardnessCutOff<tt>.
 * If the hardness of the proof passed to {@linkplain #next(ResolutionTrace)} is greater than <tt>hardnessCutOff<tt>,
 * then the number of relevant axioms, |A_r|, plus the number of recycled resolvents is no greater than 
 * |A_r|*<tt>recycleLimit</tt>.  Otherwise, all valid
 * resolvents are recycled (i.e. added to the relevant axioms).  
 * Proof hardness is the ratio of the trace size to the number of axioms in the trace.
 * Default value for <tt>hardnessCutOff<tt> is 2.0, and  default value for <tt>recycleLimit</tt> is 1.2. 
 * 
 * <p>Unlike ARCE, DRCE uses proof information to determine the order in which the constraints are tested for 
 * membership in a minimal core.  ARCE, RCE, SCE and NCE all use the same (arbitrary but deterministic) ordering.</p>
 * 
 * <p>This implementation of DRCE will work properly only on CNFs generated by the kodkod {@linkplain Translator}. </p>
 * 
 * @specfield noRecycleRatio: double
 * @specfield hardnessCutOff: double
 * @specfield recycleLimit: double
 * @invariant noRecycleRatio in [0..1]
 * @invariant recycleLimit >= 1
 * @invariant hardnessCutOff >= 1
 * @author Emina Torlak
 * @see HybridStrategy
 */
public final class DynamicRCEStrategy implements ReductionStrategy {
	private final double noRecycleRatio, recycleLimit, hardnessCutOff;
	private static final boolean DBG = true;
	private final SparseSequence<IntSet> hits;
	/**
	 * Constructs an ARCE strategy that will use the given translation
	 * log to relate the cnf clauses back to the logic constraints from 
	 * which they were generated. 
	 * @effects this.hardnessCutOff' = 2 and this.recycleLimit' = 1.2 and this.noRecycleRatio' = .03
	 */
	public DynamicRCEStrategy(final TranslationLog log) {
		this(log, .03, 2.0, 1.2);
	}
	
	/**
	 * Constructs an ARCE strategy that will use the given translation
	 * log to relate the cnf clauses back to the logic constraints from 
	 * which they were generated. 
	 * @effects this.hardnessCutOff' = hardnessCutOff and this.recycleLimit' = recycleLimit and 
	 * this.noRecycleRatio' = noRecycleRatio
	 */
	public DynamicRCEStrategy(final TranslationLog log, double noRecycleRatio, double hardnessCutOff, double recycleLimit) {
		if (noRecycleRatio<0 || noRecycleRatio>1) 
			throw new IllegalArgumentException("noRecycleRatio must be in [0..1]: " + noRecycleRatio);
		if (hardnessCutOff < 1)
			throw new IllegalArgumentException("hardnessCutOff must be >=1: " + hardnessCutOff);
		if (recycleLimit < 1)
			throw new IllegalArgumentException("recycleLimit must be >=1: " + recycleLimit);
		this.noRecycleRatio = noRecycleRatio;
		this.hardnessCutOff = hardnessCutOff;
		this.recycleLimit = recycleLimit;
		this.hits = new TreeSequence<IntSet>();
		for(IntIterator itr = StrategyUtils.rootVars(log).iterator(); itr.hasNext(); ) { 
			hits.put(itr.next(), null);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see kodkod.engine.satlab.ReductionStrategy#next(kodkod.engine.satlab.ResolutionTrace)
	 */
	public IntSet next(ResolutionTrace trace) {
		if (hits.isEmpty()) return Ints.EMPTY_SET; // tried everything
		final IntSet relevantVars = StrategyUtils.coreTailUnits(trace);
		
		final long[] byRelevance = sortByRelevance(trace, relevantVars);
		if (DBG) printRelevant(byRelevance);
 		for(int i = byRelevance.length-1; i>=0; i--) {
			final int var = (int)byRelevance[i];
			if (hits.remove(var)!=null) {
				// remove maxVar from the set of relevant variables
				relevantVars.remove(var);
				if (relevantVars.isEmpty()) break; // there was only one root formula left
				// get all axioms and resolvents corresponding to the clauses that
				// form the translations of formulas identified by relevant vars
				final IntSet relevantClauses = clausesFor(trace, relevantVars); 
				assert !relevantClauses.isEmpty() && !relevantClauses.contains(trace.size()-1);
				
				if (DBG) System.out.println("relevant clauses: " + relevantClauses.size() + ", removed " + var);
				
				return relevantClauses;
			}
		}
		
		hits.clear();		
		return Ints.EMPTY_SET;
	}
	
	private final void printRelevant(long[] byRelevance) { 
		System.out.print("\nsorted by relevance: ");
		for(long r : byRelevance) { 
			System.out.print((int)(r>>>32) + ":" + (int)r + " ");
		}
		System.out.println();
	}
	
	/**
	 * Returns an array R of longs such that for each i, j in [0..R.length) i < j implies
	 * that the formula identified by (int)R[i] in this.hits contributes fewer clauses to 
	 * the core of the given trace than the formula identified by (int)R[j].
	 * @return an array as described above
	 */
	private long[] sortByRelevance(ResolutionTrace trace, IntSet relevantVars) { 
		hits.indices().retainAll(relevantVars);
		
		if (hits.get(hits.indices().min())==null) { // first call, initialize the hits 
			for(IntIterator varItr = relevantVars.iterator(); varItr.hasNext(); ) { 
				final int var = varItr.next();
				final IntSet varReachable = new IntBitSet(var+1);
				varReachable.add(var);
				hits.put(var, varReachable);
			}
			for(Iterator<Clause> clauseItr = trace.reverseIterator(trace.axioms()); clauseItr.hasNext();) {
				final Clause clause = clauseItr.next();
				final int maxVar = clause.maxVariable();
				for(IntSet reachableVars : hits.values()) {
					if (reachableVars.contains(maxVar)) {
						for(IntIterator lits = clause.literals(); lits.hasNext(); ) {
							reachableVars.add(StrictMath.abs(lits.next()));
						}
					} 
				}
			}
		}
		
		final long[] counts = new long[hits.size()];
		
		for(Iterator<Clause> clauseItr = trace.iterator(trace.core()); clauseItr.hasNext(); ) { 
			final Clause clause = clauseItr.next();
			final int maxVar = clause.maxVariable();
			int i = 0;
			for(IntSet reachableVars : hits.values()) { 
				if (reachableVars.contains(maxVar)) { 
					counts[i]++;
				}
				i++;
			}
		}

		
		int  i = 0;	
		for(IntIterator varItr = hits.indices().iterator(); varItr.hasNext();) { 
			final int var = varItr.next();
			counts[i] = (counts[i]<<32) | var;
			i++;
		}
		
		Arrays.sort(counts);
	
		return counts;
	}

	/**
	 * Returns the indices of all axioms and resolvents
	 * in the given trace that form the translations of the formulas
	 * identified by the given variables.  This method assumes that
	 * the axioms in the given trace were generated by the Kodkod
	 * {@linkplain Translator}.
	 * @return 
	 * let C = { c: trace.prover.clauses | c.maxVariable() in relevantVars },
	 *     T = { c1, c2: C | c2.maxVariable() in abs(c1.literals) },
	 *     A = C.*T | 
	 *     trace.backwardReachable(A) - trace.backwardReachable(trace.axioms() - A)
	 */
	private IntSet clausesFor(ResolutionTrace trace, IntSet relevantVars) { 
		final double hardness = (double) trace.size() / (double) trace.axioms().size();
		final double coreRatio = ((double) trace.core().size() / (double) trace.axioms().size());
		
		if (DBG) System.out.println("trace size: " + trace.size() + ", axioms: " + trace.axioms().size() + ", core: " + trace.core().size() + ", resolvents: " + trace.resolvents().size());
		if (DBG) System.out.println("hardness: " + hardness + ", coreRatio: " + coreRatio);
		
		final IntSet relevantAxioms = StrategyUtils.clausesFor(trace, relevantVars);
		if (DBG) System.out.println("relevant axioms:  " + relevantAxioms.size());
		
		if (coreRatio < noRecycleRatio) {
			return relevantAxioms;
		} else if (hardness < hardnessCutOff) { 
			return trace.learnable(relevantAxioms);
		} else {
			IntSet current = relevantAxioms, last;
			final int maxRelevant = (int) Math.rint(relevantAxioms.size()*recycleLimit);
			do {
				last = current;
				current = trace.directlyLearnable(current);
			} while (last.size() < current.size() && current.size() < maxRelevant);
			
			if (DBG) System.out.println("last: " + last.size() +", current: " + current.size() + ", maxRelevant: " + maxRelevant);
			
			return current.size() < maxRelevant ? current : last;
		}
				
	}

}