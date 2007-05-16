/* 
 * Kodkod -- Copyright (c) 2005-2007, Emina Torlak
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
package kodkod.engine.fol2sat;

import static kodkod.ast.BinaryFormula.Operator.AND;
import static kodkod.ast.BinaryFormula.Operator.IFF;
import static kodkod.ast.BinaryFormula.Operator.IMPLIES;
import static kodkod.ast.BinaryFormula.Operator.OR;
import static kodkod.ast.QuantifiedFormula.Quantifier.ALL;
import static kodkod.ast.QuantifiedFormula.Quantifier.SOME;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import kodkod.ast.BinaryFormula;
import kodkod.ast.ComparisonFormula;
import kodkod.ast.Comprehension;
import kodkod.ast.Decl;
import kodkod.ast.Decls;
import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IntComparisonFormula;
import kodkod.ast.IntExpression;
import kodkod.ast.Multiplicity;
import kodkod.ast.MultiplicityFormula;
import kodkod.ast.Node;
import kodkod.ast.NotFormula;
import kodkod.ast.QuantifiedFormula;
import kodkod.ast.Relation;
import kodkod.ast.RelationPredicate;
import kodkod.ast.SumExpression;
import kodkod.ast.Variable;
import kodkod.ast.visitor.AbstractDetector;
import kodkod.ast.visitor.AbstractReplacer;
import kodkod.engine.bool.BooleanMatrix;
import kodkod.engine.config.Options;
import kodkod.engine.config.Reporter;
import kodkod.instance.Bounds;
import kodkod.instance.TupleSet;

/**
 * Skolemizes existential quantifiers, up to a given
 * number of nestings (within universal quantifiers). 
 * @author Emina Torlak
 */
abstract class Skolemizer extends AbstractReplacer {	
	
	/**
	 * Skolemizes the given annotated formula using the given bounds and options.  If 
	 * Options.trackFormulas is set and the formula is skolemizable, the resulting annotated
	 * formula will contain transitive source information for each of its subformulas. 
	 * Specifically, let f be the returned annotated formula, t be a descendent of f.node, and
	 * s a descendent of annotated.node from which t was derived.  Then, 
	 * f.source[t] = annotated.source[s].  If options.trackFormulas is false, no source 
	 * information will be recorded (i.e. f.source[t] = t for all descendents t of f).
	 * @effects upper bound mappings for skolem constants, if any, are added to the bounds
	 * @return the skolemized version of the given formula
	 * @throws NullPointerException - any of the arguments are null
	 * @throws IllegalArgumentException - some Relation & annotated.node.^children - bounds.relations
	 * @throws UnsupportedOperationException - bounds is unmodifiable
	 */
	@SuppressWarnings("unchecked")
	static AnnotatedNode<Formula> skolemize(final AnnotatedNode<Formula> annotated, Bounds bounds, Options options) {
		if (options.logTranslation()) {
			final Map<Node,Node> source = new IdentityHashMap<Node,Node>();
			final Skolemizer r = new Skolemizer(annotated, bounds, options) {
				protected Formula source(Formula f, Node n) {
					//System.out.println("logging " + f + " <-- " + n);
					final Node nsource = annotated.sourceOf(n);
					if (f!=nsource) source.put(f, nsource);
					return f;
				}
			};
			final Formula f = annotated.node().accept(r);
			return f==annotated.node() ? annotated : new AnnotatedNode<Formula>(f, source);
		} else {
			final Skolemizer r = new Skolemizer(annotated, bounds, options) {};
			final Formula f = annotated.node().accept(r);
			return f==annotated.node() ? annotated : new AnnotatedNode<Formula>(f);
		}
	}

	/**
	 * Contains info about an approximate bound for a 
	 * non-skolemizable decl.
	 * @specfield decl: Decl
	 * @specfield upperBound: lone BooleanMatrix
	 * @invariant decl.expression in upperBound
	 * @author Emina Torlak
	 */
	private static final class DeclInfo {
		final Decl decl;
		BooleanMatrix upperBound;
		/**
		 * Constructs a DeclInfo for the given decl.
		 * @effects this.decl' = decl && this.upperBound' = null
		 */
		DeclInfo(Decl decl) {
			this.decl = decl;
			this.upperBound =  null;
		}
	}

	/* replacement environment; maps skolemized variables to their skolem expressions,
	 * and non-skolemized variables to themselves */
	private Environment<Expression> repEnv;
	/* the interpreter used to determine the upper bounds for skolem constants;
	 * the upper bounds for skolem constants will be added to interpreter.bounds */
	private final LeafInterpreter interpreter;
	/* bounds on which the interpreter is based */
	private final Bounds bounds;
	/* reporter */
	private final Reporter reporter;
	/* non-skolemizable quantified declarations in the current scope, in the order of declaration
	 * (most recent decl is last in the list) */
	private final List<DeclInfo> nonSkolems;
	/* a Decl-only view of the nonSkolems list */
	private final List<Decl> nonSkolemsView;
	/* true if the polarity of the currently visited node is negative, otherwise false */
	private boolean negated;
	/* depth to which to skolemize; negative depth indicates that no skolemization can be done at that point */
	private int skolemDepth;

	/**
	 * Constructs a skolem replacer from the given arguments. 
	 */
	private Skolemizer(AnnotatedNode<Formula> annotated, Bounds bounds, Options options) {
		super(annotated.sharedNodes());

		// only cache intermediate computations for expressions with no free variables
		// and formulas with no free variables and no quantified descendents
		final AbstractDetector fvdetect = annotated.freeVariableDetector();
		final AbstractDetector qdetect = annotated.quantifiedFormulaDetector();
		for(Node n: annotated.sharedNodes()) {
			if (!(Boolean)n.accept(fvdetect)) {
				if (!(n instanceof Formula) || !((Boolean)n.accept(qdetect)))
					this.cache.put(n, null);
			}
		}
		this.reporter = options.reporter();
		this.bounds = bounds;
		this.interpreter = LeafInterpreter.overapproximating(bounds, options);
		this.repEnv = Environment.empty();
		this.nonSkolems = new ArrayList<DeclInfo>();
		this.nonSkolemsView = new AbstractList<Decl>() {
			public Decl get(int index) { return nonSkolems.get(index).decl;	}
			public int size() { return nonSkolems.size(); }
		};
		this.negated = false;
		this.skolemDepth = options.skolemDepth();
	}

	/**
	 * Caches the given replacement for the specified node, if 
	 * the node is a syntactically shared expression, int expression or declaration with
	 * no free variables.  Otherwise does nothing.  The method returns
	 * the replacement node.  
	 * @return replacement
	 */
	@Override
	protected final <N extends Node> N cache(N node, N replacement) {
		if (cache.containsKey(node)) {
			cache.put(node, replacement);
		}
		return replacement;
	}
	
	/**
	 * Records that the given node is the source of the 
	 * specified formula, if this is a tracking skolemizer.  Otherwise does nothing.
	 * This method is always called when the result of visiting a node n will result
	 * in the creation of a formula f such that f != n.
	 * @return f
	 * @effects Records that the given node is the source of the 
	 * specified formula, if this is a tracking skolemizer.  Otherwise does nothing.
	 */
	protected Formula source(Formula f, Node n) { 
		return f; 
	}
	
	/*-------declarations---------*/
	/** 
	 * Visits the given decl's expression.  Note that we must not visit variables 
	 * in case they are re-used.  For example, consider the formula
	 * some x: X | all x: Y | F(x).  Since x bound by the existential quantifier
	 * is going to be skolemized, if we visited the variable in the enclosed
	 * declaration, we would get the skolem constant as a return value and
	 * a ClassCastException would be thrown.
	 * 
	 * @return { d: Declaration |  d.variable = decl.variable && d.multiplicity = decl.multiplicity &&
	 *                             d.expression = decl.expression.accept(this) } 
	 */
	@Override
	public final Decl visit(Decl decl) {
		Decl ret = lookup(decl);
		if (ret!=null) return ret;
		final int oldDepth = skolemDepth;
		skolemDepth = -1; // can't skolemize inside a decl
		final Expression expression = decl.expression().accept(this);
		skolemDepth = oldDepth;
		ret = (expression==decl.expression()) ? decl : decl.variable().declare(decl.multiplicity(), expression); 	
		return cache(decl,ret);
	}

	/** 
	 * This method should be accessed only from the context of a non-skolemizable
	 * node, because it  extends the replacement environment
	 * with  identity mappings for the variables declared in the given decls.  To ensure
	 * that the environment is always extended, the method should be called using the
	 * visit((Decls) node.declarations()) syntax, since the accept syntax may dynamically
	 * dispatch the call to the {@link #visit(Decl)} method, producing UnboundLeafExceptions.
	 * @effects this.repEnv in this.repEnv'.^parent &&
	 * #(this.repEnv'.*parent - this.repEnv.*parent) = decls.size() &&
	 * all v: decls.variable | this.repEnv'.lookup(v) = v
	 * @requires this.skolemDepth < 0
	 * @return { d: Decls | d.size = decls.size && 
	 *                      all i: [0..d.size) | d.declarations[i] = decls.declarations[i].accept(this) } 
	 */
	public final  Decls visit(Decls decls) { 
		Decls ret = lookup(decls);
		if (ret==null) {
			Decls visitedDecls = null;
			boolean allSame = true;
			for(Decl decl : decls) {
				Decls newDecl = visit(decl);
				if (newDecl != decl) 
					allSame = false;
				visitedDecls = (visitedDecls==null) ? newDecl : visitedDecls.and(newDecl);
				repEnv = repEnv.extend(decl.variable(), decl.variable());
			}
			ret = allSame ? decls : visitedDecls;
			return cache(decls, ret);
		} else { // just extend the replacement environment
			for(Decl decl: decls) {
				repEnv = repEnv.extend(decl.variable(), decl.variable());
			}
			return ret;
		}
	}

	/*-------expressions and intexpressions---------*/
	/* INVARIANT:  whenever an expression or intexpression is visited, skolemDepth < 0 */
	/** 
	 * Returns the binding for the given variable in the current replacement environment.
	 * @return the binding for the given variable in the current replacement environment.
	 * @throws UnboundLeafException - variable not bound in teh replacement environment.
	 */
	@Override
	public final Expression visit(Variable variable) { 
		final Expression ret = repEnv.lookup(variable);
		if (ret==null)
			throw new UnboundLeafException("Unbound variable", variable);
		return ret;
	}	
	
	/**
	 * @see kodkod.ast.visitor.AbstractReplacer#visit(kodkod.ast.Comprehension)
	 */
	@Override
	public final Expression visit(Comprehension expr) {
		Expression ret = lookup(expr);
		if (ret!=null) return ret;
		final Environment<Expression> oldRepEnv = repEnv; // skolemDepth < 0 at this point
		final Decls decls = visit((Decls)expr.declarations());
		final Formula formula = expr.formula().accept(this);
		ret = (decls==expr.declarations() && formula==expr.formula()) ? expr : formula.comprehension(decls);
		repEnv = oldRepEnv;		
		return cache(expr,ret);
	}
	/**
	 * @see kodkod.ast.visitor.AbstractReplacer#visit(kodkod.ast.SumExpression)
	 */
	@Override
	public final IntExpression visit(SumExpression intExpr) {
		IntExpression ret = lookup(intExpr);
		if (ret!=null) return ret;	
		final Environment<Expression> oldRepEnv = repEnv; // skolemDepth < 0 at this point
		final Decls decls  = visit((Decls)intExpr.declarations());
		final IntExpression expr = intExpr.intExpr().accept(this);
		ret =  (decls==intExpr.declarations() && expr==intExpr.intExpr()) ? intExpr : expr.sum(decls);
		repEnv = oldRepEnv;
		return cache(intExpr,ret);
	}

	/*-------formulas---------*/
	/**
	 * Returns the least sound upper bound on the value of expr
	 * @return the least sound upper bound on the value of expr
	 */
	private final BooleanMatrix upperBound(Expression expr, Environment<BooleanMatrix> env) {
		return FOL2BoolTranslator.approximate(new AnnotatedNode<Expression>(expr), interpreter, env);
	}
	/**
	 * Creates a skolem relation for decl.variable, bounds it in 
	 * this.bounds, and returns the expression 
	 * that should replace decl.variable in the final formula.
	 * @requires decl has been visited by this 
	 * @effects bounds the skolem relation for decl in this.interpreter.boundingObject
	 * @return the expression that should replace decl.variable in 
	 * the final formula
	 */
	private Expression skolemExpr(Decl decl) {
		final int depth = nonSkolems.size();
		final int arity = depth + decl.variable().arity();

		final Relation skolem = Relation.nary("$"+decl.variable().name(), arity);
		reporter.skolemizing(decl, skolem, nonSkolemsView);

		Expression skolemExpr = skolem;
		Environment<BooleanMatrix> skolemEnv = Environment.empty();

		for(DeclInfo info : nonSkolems) {
			if (info.upperBound==null) {
				info.upperBound = upperBound(info.decl.expression(), skolemEnv);
			}
			skolemEnv = skolemEnv.extend(info.decl.variable(), info.upperBound);
			skolemExpr = info.decl.variable().join(skolemExpr);
		}

		BooleanMatrix matrixBound = upperBound(decl.expression(), skolemEnv);
		for(int i = depth-1; i >= 0; i--) {
			matrixBound = nonSkolems.get(i).upperBound.cross(matrixBound);
		}

		final TupleSet skolemBound = bounds.universe().factory().setOf(arity, matrixBound.denseIndices());
		bounds.bound(skolem, skolemBound);

		return skolemExpr;
	}	
	
	
	/**
	 * Adds the skolem constraint for the given declaration and skolem expr to skolemConstraints.
	 * @return decl.multiplicity = SET => skolemConstraints && skolemExpr in decl.expression,
	 *         skolemConstraints && skolemExpr in decl.expression && decl.multiplicity skolemExpr
	 */
	private Formula addConstraints(Formula skolemConstraints, Decl decl, Expression skolemExpr) {
		final Formula f0 = source(skolemExpr.in(decl.expression()), decl);
		final Multiplicity mult = decl.multiplicity();
		if (mult==Multiplicity.SET) {
			return skolemConstraints.and(f0);
		} else {
			final Formula f1 = source(skolemExpr.apply(mult), decl);
			return skolemConstraints.and(source(f0.and(f1), decl));
		}
	}
	
	/**
	 * Skolemizes the given formula, if possible, otherwise returns the result
	 * of replacing its free variables according to the current replacement environment.
	 * @see kodkod.ast.visitor.AbstractReplacer#visit(kodkod.ast.QuantifiedFormula)
	 */
	public final Formula visit(QuantifiedFormula qf) {
		Formula ret = lookup(qf);
		if (ret!=null) return ret;
		final Environment<Expression> oldRepEnv = repEnv;	
		final QuantifiedFormula.Quantifier quant = qf.quantifier();
		final Decls decls = qf.declarations();
		if (skolemDepth>=0 && (negated && quant==ALL || !negated && quant==SOME)) { // skolemizable formula
			Formula declConstraints = Formula.TRUE;
			for(Decl decl : decls) {
				Decl newDecl = visit(decl);
				Expression skolemExpr = skolemExpr(newDecl);
				repEnv = repEnv.extend(decl.variable(), skolemExpr);
				declConstraints = source(addConstraints(declConstraints, newDecl, skolemExpr), decls);
			}
			ret = declConstraints.compose(negated ? IMPLIES : AND, qf.formula().accept(this));
		} else { // non-skolemizable formula
			final Decls newDecls = visit((Decls)qf.declarations());
			if (skolemDepth>=nonSkolems.size()+newDecls.size()) { // could skolemize below
				for(Decl d: newDecls) { nonSkolems.add(new DeclInfo(d)); }
				final Formula formula = qf.formula().accept(this);
				ret = ((newDecls==decls && formula==qf.formula()) ? qf : formula.quantify(quant, newDecls));
				for(int i = newDecls.size(); i > 0; i--) { nonSkolems.remove(nonSkolems.size()-1); }
			} else { // can't skolemize below
				final int oldDepth = skolemDepth;
				skolemDepth = -1; 
				final Formula formula = qf.formula().accept(this);
				ret = ((newDecls==decls && formula==qf.formula()) ? qf : formula.quantify(quant, newDecls));
				skolemDepth = oldDepth;
			}				
		}			
		repEnv = oldRepEnv;
		return source(cache(qf,ret), qf);
	}

	/** 
	 * Calls not.formula.accept(this) after flipping the negation flag and returns the result. 
	 * @see kodkod.ast.visitor.AbstractReplacer#visit(kodkod.ast.NotFormula)
	 **/
	public final Formula visit(NotFormula not) {
		Formula ret = lookup(not);
		if (ret!=null) return ret;
		negated = !negated; // flip the negation flag
		final Formula retChild = not.formula().accept(this);
		negated = !negated;
		return retChild==not.formula() ? cache(not,not) : source(cache(not, retChild.not()), not);			
	}

	/**
	 * If not cached, visits the formula's children with appropriate settings
	 * for the negated flag and the skolemDepth parameter.
	 * @see kodkod.ast.visitor.AbstractReplacer#visit(kodkod.ast.BinaryFormula)
	 */
	public final Formula visit(BinaryFormula bf) {
		Formula ret = lookup(bf);
		if (ret!=null) return ret;			
		final BinaryFormula.Operator op = bf.op();
		final int oldDepth = skolemDepth;
		if (op==IFF || (negated && op==AND) || (!negated && (op==OR || op==IMPLIES))) { // cannot skolemize in these cases
			skolemDepth = -1;
		}
		final Formula left, right;
		if (negated && op==IMPLIES) { // !(a => b) = !(!a || b) = a && !b
			negated = !negated;
			left = bf.left().accept(this);
			negated = !negated;
			right = bf.right().accept(this);
		} else {
			left = bf.left().accept(this);
			right = bf.right().accept(this);
		}
		skolemDepth = oldDepth;
		ret = (left==bf.left()&&right==bf.right()) ? bf : left.compose(op, right);
		return source(cache(bf,ret),bf);
	}


	/** 
	 * Calls super.visit(icf) after disabling skolemization and returns the result. 
	 * @return super.visit(icf) 
	 **/
	public final Formula visit(IntComparisonFormula icf) {
		final int oldDepth = skolemDepth;
		skolemDepth = -1; // cannot skolemize inside an int comparison formula
		final Formula ret = super.visit(icf);
		skolemDepth = oldDepth;
		return source(ret,icf);
	}

	/** 
	 * Calls super.visit(cf) after disabling skolemization and returns the result. 
	 * @return super.visit(cf) 
	 **/
	public final Formula visit(ComparisonFormula cf) {
		final int oldDepth = skolemDepth;
		skolemDepth = -1; // cannot skolemize inside a comparison formula
		final Formula ret = super.visit(cf);
		skolemDepth = oldDepth;
		return source(ret,cf);
	}

	/** 
	 * Calls super.visit(mf) after disabling skolemization and returns the result. 
	 * @return super.visit(mf) 
	 **/
	public final Formula visit(MultiplicityFormula mf) {
		final int oldDepth = skolemDepth;
		skolemDepth = -1; // cannot skolemize inside a multiplicity formula
		final Formula ret = super.visit(mf);
		skolemDepth = oldDepth;
		return source(ret,mf);
	}

	/** 
	 * Calls super.visit(pred) after disabling skolemization and returns the result. 
	 * @return super.visit(pred) 
	 **/
	public final Formula visit(RelationPredicate pred) {
		final int oldDepth = skolemDepth;
		skolemDepth = -1; // cannot skolemize inside a relation predicate
		final Formula ret = super.visit(pred);
		skolemDepth = oldDepth;
		return source(ret,pred);
	}
}
