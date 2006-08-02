/**
 * 
 */
package examples.tptp;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.Relation;
import kodkod.engine.Solution;
import kodkod.engine.Solver;
import kodkod.engine.TimeoutException;
import kodkod.engine.satlab.SATFactory;
import kodkod.instance.Bounds;
import kodkod.instance.Tuple;
import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;

/**
 * A KK encoding of ALG197+1.p from http://www.cs.miami.edu/~tptp/
 * @author Emina Torlak
 */
public final class ALG197 extends Quasigroups7 {

	/**
	 * Constructs a new instance of ALG197.
	 */
	public ALG197() {}
	
	
	/**
	 * Parametrization of axioms 12 and 13.
	 * @requires e's are unary, op is ternary
	 */
	Formula ax12and13(Relation[] e, Relation op) {
		Expression eIden = Expression.NONE;
		for(int i = 0; i < 7; i++) {
			eIden = eIden.union(e[i].product(e[1]).product(e[1]));
		}
		final Expression intersect = eIden.intersection(op);
		return intersect.some().and(eIden.in(intersect).not());
	}
	
	
	
	/**
	 * Parametrization of axioms 14 and 15.
	 * @requires e's are unary, op is ternary
	 */
	Formula ax14and15(Relation[] e, Relation op) {
		final Expression expr0 = e[6].join(op); // op(e6,...)
		final Expression expr1 = e[6].join(expr0); // op(e6,e6)
		final Expression expr2 = expr1.join(expr1.join(op)); // op(op(e6,e6),op(e6,e6))
		final Expression expr3 = expr2.join(expr0); // op(e6,op(op(e6,e6),op(e6,e6)))
		// e0 = op(e6,op(e6,e6))
		final Formula f0 = e[0].in(expr1.join(expr0));
		// e1 = op(op(e6,e6),op(e6,e6))
		final Formula f1 = e[1].in(expr2);
		// e2 = op(op(op(e6,e6),op(e6,e6)),op(e6,e6))
		final Formula f2 = e[2].in(expr1.join(expr2.join(op)));
		// e3 = op(e6,op(op(e6,e6),op(e6,e6)))
		final Formula f3 = e[3].in(expr3);
		// e4 = op(e6,op(e6,op(op(e6,e6),op(e6,e6))))
		final Formula f4 = e[4].in(expr3.join(expr0));
		return f0.and(f1).and(f2).and(f3).and(f4);
	}
	
	/**
	 * Parametrization of axioms 16-22.
	 * @requires e is unary, h is binary
	 */
	Formula ax16_22(Relation e, Relation h) {
		final Expression expr0 = e.join(op2); // op2(e,...)
		final Expression expr1 = e.join(expr0); // op2(e,e)
		final Expression expr2 = expr1.join(expr1.join(op2)); // op2(op2(e,e),op2(e,e))
		final Expression expr3 = expr2.join(expr0); // op2(e,op2(op2(e,e),op2(e,e)))
		//  h(e10) = op2(e,op2(e,e))
		final Formula f0 = e1[0].join(h).eq(expr1.join(expr0));
		//  h(e11) = op2(op2(e,e),op2(e,e))
		final Formula f1 = e1[1].join(h).eq(expr2);
		//  h(e12) = op2(op2(op2(e,e),op2(e,e)),op2(e,e))
		final Formula f2 = e1[2].join(h).eq(expr1.join(expr2.join(op2)));
		//  h(e13) = op2(e,op2(op2(e,e),op2(e,e)))
		final Formula f3 = e1[3].join(h).eq(expr3);
		//  h(e14) = op2(e,op2(e,op2(op2(e,e),op2(e,e))))
		final Formula f4 = e1[4].join(h).eq(expr3.join(expr0));
		//  h(e15) = op2(e,e)
		final Formula f5 = e1[5].join(h).eq(expr1);
		return f0.and(f1).and(f2).and(f3).and(f4).and(f5);
	}
	
	/**
	 * Returns the bounds the problem (axioms 1, 4, 9-11, last formula of 14-15, and first formula of 16-22).
	 * @return the bounds for the problem
	 */
	public final Bounds bounds() {
		final Bounds b = super.bounds();
		final TupleFactory f = b.universe().factory();
		
		final TupleSet op1h = b.upperBound(op1).clone();
		final TupleSet op2h = b.upperBound(op2).clone();
		
		final TupleSet op1l = f.setOf(f.tuple("e16", "e16", "e15")); // axiom 14, line 6
		final TupleSet op2l = f.setOf(f.tuple("e26", "e26", "e25")); // axiom 15, line 6
		
		op1h.removeAll(f.area(f.tuple("e16", "e16", "e10"), f.tuple("e16", "e16", "e16")));
		op1h.addAll(op1l);
		
		op2h.removeAll(f.area(f.tuple("e26", "e26", "e20"), f.tuple("e26", "e26", "e26")));
		op2h.addAll(op2l);
		
		b.bound(op1, op1l, op1h);
		b.bound(op2, op2l, op2h);
		
		final TupleSet high = f.area(f.tuple("e10", "e20"), f.tuple("e15", "e26"));
		
		// first line of axioms 16-22
		for(int i = 0; i < 7; i++) {
			Tuple t = f.tuple("e16", "e2"+i);
			high.add(t);
			b.bound(h[i], f.setOf(t), high);
			high.remove(t);
		}
		
		return b;
	}
	
	private static void usage() {
		System.out.println("java examples.tptp.ALG197");
		System.exit(1);
	}
	
	/**
	 * Usage: java examples.tptp.ALG197
	 */
	public static void main(String[] args) {
	
		try {
	
			final ALG197 model = new ALG197();
			final Solver solver = new Solver();
			solver.options().setSolver(SATFactory.MiniSat);
			final Formula f = model.axioms().and(model.co1().not());
			final Bounds b = model.bounds();
//			System.out.println(f);
			final Solution sol = solver.solve(f, b);
			if (sol.instance()==null) {
				System.out.println(sol);
			} else {
				System.out.println(sol.stats());
				model.display(sol.instance());
			}
		} catch (TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NumberFormatException nfe) {
			usage();
		}
	}
}