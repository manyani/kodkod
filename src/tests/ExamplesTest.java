package tests;

import examples.Bigconfig;
import examples.CeilingsAndFloors;
import examples.Dijkstra;
import examples.Pigeonhole;
import examples.RingElection;
import examples.Sudoku;
import examples.Toughnut;
import junit.framework.TestCase;
import kodkod.ast.Formula;
import kodkod.engine.Solution;
import kodkod.engine.Solver;
import kodkod.engine.TimeoutException;
import kodkod.engine.satlab.SATFactory;
import kodkod.instance.Bounds;

public class ExamplesTest extends TestCase {

	private final Solver solver;
	
	public ExamplesTest(String arg0) {
		super(arg0);
		this.solver = new Solver();
		this.solver.options().setSolver(SATFactory.ZChaff);
	}

	private Solution solve(Formula formula, Bounds bounds) {
		try {
			return solver.solve(formula, bounds);
		} catch (TimeoutException e) {
			e.printStackTrace();
			throw new AssertionError();
		}
	}
	
	/**
	 * Runs the Bigconfig example for 1 hq, 9 subs, 4 unwindings.
	 */
	public void testBigconfig() {
		final Bigconfig model = new Bigconfig(4);
		final Solution sol = solve(model.show(), model.bounds(1, 9, 10));
//		SATISFIABLE
//		p cnf 2227 8278
//		primary variables: 100
		assertEquals(Solution.Outcome.SATISFIABLE,sol.outcome());
		assertEquals(100, sol.stats().primaryVariables());
		assertEquals(2227, sol.stats().variables());
		assertEquals(8278, sol.stats().clauses());
	}
	
	/**
	 * Runs the CeilingsAndFloors example for 6 Man, 6 Platform.
	 */
	public void testCeilingsAndFloors() {
		final CeilingsAndFloors model = new CeilingsAndFloors();
		final Formula assertion = model.declarations().and(model.belowTooDoublePrime());
		final Solution sol = solve(assertion, model.bounds(6, 6));
//		UNSATISFIABLE
//		p cnf 1686 6436
//		primary variables: 90
		assertEquals(Solution.Outcome.UNSATISFIABLE, sol.outcome());
		assertEquals(90, sol.stats().primaryVariables());
		assertEquals(1686, sol.stats().variables());
		assertEquals(6436, sol.stats().clauses());
	}
	
	/**
	 * Runs the Dijkstra example for 6 States, 6 Processes, and 6 Mutexes.
	 */
	public void testDijkstra() {
		final Dijkstra model = new Dijkstra();
		final Formula noDeadlocks = model.declarations().and(model.dijkstraPreventsDeadlocks().not());
		final Solution sol = solve(noDeadlocks, model.bounds(6,6,6));
//		UNSATISFIABLE
//		p cnf 4341 25526
//		primary variables: 444
		assertEquals(Solution.Outcome.UNSATISFIABLE, sol.outcome());
		assertEquals(444, sol.stats().primaryVariables());
		assertEquals(4341, sol.stats().variables());
		assertEquals(25526, sol.stats().clauses());
	}
	
	/**
	 * Runs the Pigeonhole example for 10 Pigeons, 9 Holes.
	 */
	public void testPigeonhole() {
		final Pigeonhole model = new Pigeonhole();
		final Formula show = model.declarations().and(model.pigeonPerHole());
		final Solution sol = solve(show, model.bounds(10,9));
//		UNSATISFIABLE
//		p cnf 983 3567
//		primary variables: 90
		assertEquals(Solution.Outcome.UNSATISFIABLE, sol.outcome());
		assertEquals(90, sol.stats().primaryVariables());
		assertEquals(983, sol.stats().variables());
		assertEquals(3567, sol.stats().clauses());
	}
	
	/**
	 * Runs the RingElection example for 10 Times, 5 Proceses.
	 */
	public void testRingElection() {
		final RingElection model = new RingElection();
		final Formula checkAtMostOneElected = model.declsAndFacts().and(model.atMostOneElected().not());
		final Solution sol = solve(checkAtMostOneElected, model.bounds(5,10));
//		UNSATISFIABLE
//		p cnf 8654 36743
//		primary variables: 325
		assertEquals(Solution.Outcome.UNSATISFIABLE, sol.outcome());
		assertEquals(325, sol.stats().primaryVariables());
		assertEquals(8654, sol.stats().variables());
		assertEquals(36743, sol.stats().clauses());
	}

	/**
	 * Runs the Sudoku example.
	 */
	public void testSudoku() {
		final Sudoku model = new Sudoku(3);
		final Solution sol = solve(model.rules(), model.puzzle1());
//		SATISFIABLE
//		p cnf 1544 9006
//		primary variables: 702
		assertEquals(Solution.Outcome.SATISFIABLE, sol.outcome());
		assertEquals(702, sol.stats().primaryVariables());
		assertEquals(1544, sol.stats().variables());
		assertEquals(9006, sol.stats().clauses());
	}
	
	/**
	 * Runs the Toughnut example for an 8x8 board.
	 */
	public void testToughnut() {
		final Toughnut model = new Toughnut();
		final Solution sol = solve(model.covering(), model.board(8));
//		TRIVIALLY_UNSATISFIABLE
//		p cnf 0 0
//		primary variables: 0
		assertEquals(Solution.Outcome.TRIVIALLY_UNSATISFIABLE, sol.outcome());
		assertEquals(0, sol.stats().primaryVariables());
		assertEquals(0, sol.stats().variables());
		assertEquals(0, sol.stats().clauses());
	}
	
}
