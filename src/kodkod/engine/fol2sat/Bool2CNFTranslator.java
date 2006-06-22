package kodkod.engine.fol2sat;

import static kodkod.engine.bool.Operator.AND;
import kodkod.engine.bool.BooleanFormula;
import kodkod.engine.bool.BooleanVariable;
import kodkod.engine.bool.BooleanVisitor;
import kodkod.engine.bool.ITEGate;
import kodkod.engine.bool.MultiGate;
import kodkod.engine.bool.NotGate;
import kodkod.engine.satlab.SATFactory;
import kodkod.engine.satlab.SATSolver;
import kodkod.util.ints.IntSet;
import kodkod.util.ints.Ints;

/**
 * Transforms a boolean circuit into a formula in conjunctive
 * normal form.
 * 
 * @author Emina Torlak
 */
final class Bool2CNFTranslator {

	private Bool2CNFTranslator() {}

	/**
	 * Creates a new instance of SATSolver using the provided factory
	 * and uses it to translate the given circuit into conjunctive normal form
	 * using the <i>definitional translation algorithm</i>.
	 * The third parameter is required to contain the number of primary variables
	 * allocated during translation from FOL to booelan.
	 * @return a SATSolver instance returned by the given factory and initialized
	 * to contain the CNF translation of the given circuit.
	 */
	static SATSolver definitional(BooleanFormula circuit, SATFactory factory, int numPrimaryVariables) {
		final SATSolver solver = factory.instance();
		solver.addClause(circuit.accept(new DefinitionalTranslator(solver, numPrimaryVariables, circuit),null));
		return solver;
	}
	
	/**
	 * Helper visitor that performs <i> definitional translation to cnf </i>.
	 * @specfield root: BooleanFormula // the translated circuit
	 */
	private static final class DefinitionalTranslator implements BooleanVisitor<int[], Object> {
		private final SATSolver solver;
		private final IntSet visited;
		private final PolarityDetector pdetector;
		private final int[] unaryClause = new int[1];
		private final int[] binaryClause = new int[2];
		private final int[] ternaryClause = new int[3];
		
		/**
		 * Constructs a translator for the given circuit.
		 * @effects this.root' = circuit
		 */
		DefinitionalTranslator(SATSolver solver, int numPrimaryVars, BooleanFormula circuit) {
			final int maxLiteral = StrictMath.abs(circuit.label());
			this.solver = solver;
			this.solver.addVariables(maxLiteral);
			this.pdetector = (new PolarityDetector(numPrimaryVars, maxLiteral)).apply(circuit);
			this.visited = Ints.bestSet(pdetector.offset, StrictMath.max(pdetector.offset, maxLiteral));
		}
		
		/**
		 * Adds translation clauses to the solver and returns an array containing the
		 * gate's literal. The CNF clauses are generated according to the standard SAT to CNF translation:
		 * o = AND(i1, i2, ... ik) ---> (i1 | !o) & (i2 | !o) & ... & (ik | !o) & (!i1 | !i2 | ... | !ik | o),
		 * o = OR(i1, i2, ... ik)  ---> (!i1 | o) & (!i2 | o) & ... & (!ik | o) & (i1 | i2 | ... | ik | !o).
		 * @return o: int[] | o.length = 1 && o.[0] = multigate.literal
		 * @effects if the multigate has not yet been visited, its children are visited
		 * and the clauses are added to the solver connecting the multigate's literal to
		 * its input literal, as described above.
		 */
		public int[] visit(MultiGate multigate, Object arg) {  
			final int oLit = multigate.label();
			if (visited.add(oLit)) { 
				final int sgn; final boolean p, n;
				if (multigate.op()==AND) {
					sgn = 1; p = pdetector.positive(oLit); n = pdetector.negative(oLit);
				} else { // multigate.op()==OR
					sgn = -1; n = pdetector.positive(oLit); p = pdetector.negative(oLit);
				}
				final int[] lastClause = n ? new int[multigate.size()+1] : null;
				final int output = oLit * -sgn;
				int i = 0;
				for(BooleanFormula input : multigate) {
					int iLit = input.accept(this, arg)[0];
					if (p) {
						binaryClause[0] = iLit * sgn;
						binaryClause[1] = output;
						solver.addClause(binaryClause);
					}
					if (n) { 
						lastClause[i++] = iLit * -sgn;
					}
				}
				if (n) {
					lastClause[i] = oLit * sgn;
					solver.addClause(lastClause);
				}
			}
			unaryClause[0] = oLit;
			return unaryClause;        
		}
		
		/**
		 * Adds translation clauses to the solver and returns an array containing the
		 * gate's literal. The CNF clauses are generated according to the standard SAT to CNF translation:
		 * o = ITE(i, t, e) ---> (!i | !t | o) & (!i | t | !o) & (i | !e | o) & (i | e | !o)
		 * @return o: int[] | o.length = 1 && o.[0] = itegate.literal
		 * @effects if the itegate has not yet been visited, its children are visited
		 * and the clauses are added to the solver connecting the multigate's literal to
		 * its input literal, as described above.
		 */
		public int[] visit(ITEGate itegate, Object arg) {
			final int oLit = itegate.label();
			if (visited.add(oLit)) {
				final int i = itegate.input(0).accept(this, arg)[0];
				final int t = itegate.input(1).accept(this, arg)[0];
				final int e = itegate.input(2).accept(this, arg)[0];
				final boolean p = pdetector.positive(oLit), n = pdetector.negative(oLit);
				if (p) {
					ternaryClause[0] = -i; ternaryClause[1] = t; ternaryClause[2] = -oLit;
					solver.addClause(ternaryClause);
					ternaryClause[0] = i; ternaryClause[1] = e; ternaryClause[2] = -oLit;
					solver.addClause(ternaryClause);
				}
				if (n) {
					ternaryClause[0] = -i; ternaryClause[1] = -t; ternaryClause[2] = oLit;
					solver.addClause(ternaryClause);	
					ternaryClause[0] = i; ternaryClause[1] = -e; ternaryClause[2] = oLit;
					solver.addClause(ternaryClause);
				}	
			}
			unaryClause[0] = oLit;
			return unaryClause;
		}
		
		/** 
		 * Returns the negation of the result of visiting negation.input, wrapped in
		 * an array.
		 * @return o: int[] | o.length = 1 && o[0] = - translate(negation.inputs)[0]
		 *  */
		public int[] visit(NotGate negation, Object arg) {
			final int[] o = negation.input(0).accept(this, arg);
			assert o.length == 1;
			o[0] = -o[0];
			return o;
		}
		
		/**
		 * Returns the variable's literal wrapped in a an array.
		 * @return o: int[] | o.length = 1 && o[0] = variable.literal
		 */
		public int[] visit(BooleanVariable variable, Object arg) {
			unaryClause[0] = variable.label();
			return unaryClause;
		}
	}
	
	
	/**
	 * Helper visitor that detects pdetector of subformulas.
	 * @specfield root: BooleanFormula // the root of the DAG for whose components we are storing pdetector information
	 */
	private static final class PolarityDetector implements BooleanVisitor<Object, Integer> {
		final int offset;
		/**
		 * @invariant all i : [0..polarity.length) | 
		 *   pdetector[i] = 0 <=> formula with label offset + i has not been visited,
		 *   pdetector[i] = 1 <=> formula with label offset + i has been visited with positive pdetector only,
		 *   pdetector[i] = 2 <=> formula with label offset + i has been visited with negative pdetector only,
		 *   pdetector[i] = 3 <=> formula with label offset + i has been visited with both polarities
		 */
		private final int[] polarity;
		private final Integer[] ints = { Integer.valueOf(3), Integer.valueOf(1), Integer.valueOf(2) };
		
		/**
		 * Creates a new pdetector detector and applies it to the given circuit.
		 * @requires maxLiteral = |root.label()|
		 */
		PolarityDetector(int numPrimaryVars, int maxLiteral) {
			this.offset = numPrimaryVars+1;
			this.polarity = new int[maxLiteral-numPrimaryVars];
		}
		
		/**
		 * Applies this detector to the given formula, and returns this.
		 * @requires this.root = root
		 * @effects this.visit(root)
		 * @return this
		 */
		PolarityDetector apply(BooleanFormula root) {
			root.accept(this, ints[1]);
			return this;
		}
		
		/**
		 * Returns true if the formula with the given label occurs positively in this.root.  
		 * @requires this visitor has been applied to this.root
		 * @requires label in (MultiGate + ITEGate).label
		 * @return true if the formula with the given label occurs positively in this.root.  
		 */
		boolean positive(int label) {
			return (polarity[label-offset] & 1) > 0;
		}
		
		/**
		 * Returns true if the formula with the given label occurs negatively in this.root.  
		 * @requires this visitor has been applied to this.root
		 * @requires label in (MultiGate + ITEGate).label
		 * @return true if the formula with the given label occurs negatively in this.root.  
		 */
		boolean negative(int label) {
			return (polarity[label-offset] & 2) > 0;
		}
		
		/**
		 * Returns true if the given formula has been visited with the specified
		 * pdetector (1 = positive, 2 = negative, 3 = both).  Otherwise records the visit and returns false.
		 * @requires formula in (MultiGate + ITEGate)
		 * @requires pdetector in this.ints
		 * @return true if the given formula has been visited with the specified
		 * pdetector.  Otherwise records the visit and returns false.
		 */
		private boolean visited(BooleanFormula formula, Integer polarity) {
			final int index = formula.label() - offset;
			final int value = this.polarity[index];
			return (this.polarity[index] = value | polarity) == value;
		}
		
		public Object visit(MultiGate multigate, Integer arg) {
			if (!visited(multigate, arg)) {
				for(BooleanFormula input : multigate) {
					input.accept(this, arg);
				}
			}
			return null;
		}

		public Object visit(ITEGate ite, Integer arg) {
			if (!visited(ite, arg)) {
				// the condition occurs both positively and negative in an ITE gate
				ite.input(0).accept(this, ints[0]);
				ite.input(1).accept(this, arg);
				ite.input(2).accept(this, arg);
			}
			return null;
		}

		public Object visit(NotGate negation, Integer arg) {
			return negation.input(0).accept(this, ints[3-arg]);
		}

		public Object visit(BooleanVariable variable, Integer arg) {
			return null; // nothing to do
		}
		
	}
	
}