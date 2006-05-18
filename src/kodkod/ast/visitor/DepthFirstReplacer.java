/*
 * DepthFirstReplacer.java
 * Created on Aug 24, 2005
 */
package kodkod.ast.visitor;

import kodkod.ast.BinaryExpression;
import kodkod.ast.BinaryFormula;
import kodkod.ast.BinaryIntExpression;
import kodkod.ast.ComparisonFormula;
import kodkod.ast.Comprehension;
import kodkod.ast.ConstantExpression;
import kodkod.ast.ConstantFormula;
import kodkod.ast.Decl;
import kodkod.ast.Decls;
import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IfExpression;
import kodkod.ast.IntCastExpression;
import kodkod.ast.IntComparisonFormula;
import kodkod.ast.IntConstant;
import kodkod.ast.IntExpression;
import kodkod.ast.Multiplicity;
import kodkod.ast.MultiplicityFormula;
import kodkod.ast.Node;
import kodkod.ast.NotFormula;
import kodkod.ast.QuantifiedFormula;
import kodkod.ast.Relation;
import kodkod.ast.RelationPredicate;
import kodkod.ast.UnaryExpression;
import kodkod.ast.UnaryIntExpression;
import kodkod.ast.Variable;




/** 
 * A depth first replacer.  The default implementation
 * returns the tree to which it is applied.  Reference 
 * equality is used to determine if two nodes are the same.
 * 
 * @specfield cached: set Node // result of visiting these nodes will be cached
 * @specfield cache: cached ->lone Node
 * @author Emina Torlak 
 */
public abstract class DepthFirstReplacer implements ReturnVisitor<Expression, Formula, Decls, IntExpression> {
	
	protected DepthFirstReplacer() { }
	
	/**
	 * If the given node has already been visited and its replacement
	 * cached, the cached value is returned.  Otherwise, null is returned.
	 * @return this.cache[node]
	 */
	protected abstract <N extends Node> N lookup(N node) ;
	
	/**
	 * Caches the given replacement for the specified node, if this is 
	 * a caching visitor.  Otherwise does nothing.  The method returns
	 * the replacement node.  
	 * @effects node in this.cached => this.cache' = this.cache ++ node->replacement,
	 *           this.cache' = this.cache
	 * @return replacement
	 */
	protected abstract <N extends Node> N cache(N node, N replacement);
	
	/** 
	 * Calls lookup(decls) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits each of the children's 
	 * variable and expression.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement Decls object is cached and returned.
	 * @return { d: Decls | d.size = decls.size && 
	 *                      all i: [0..d.size) | d.declarations[i].variable = decls.declarations[i].variable.accept(this) && 
	 *                                           d.declarations[i].expression = decls.declarations[i].expression.accept(this) } 
	 */
	public Decls visit(Decls decls) { 
		Decls ret = lookup(decls);
		if (ret==null) {	
			Decls visitedDecls = null;
			boolean allSame = true;
			for(Decl decl : decls) {
				Decls newDecl = visit(decl);
				if (newDecl != decl) 
					allSame = false;
				visitedDecls = (visitedDecls==null) ? newDecl : visitedDecls.and(newDecl);
			}
			
			ret = allSame ? decls : visitedDecls;
		}
		return cache(decls, ret);
	}
	
	/** 
	 * Calls lookup(decl) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the declaration's 
	 * variable and expression.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement Decl object is cached and returned.
	 * @return { d: Declaration |  d.variable = declaration.variable.accept(this) && 
	 *                             d.expression = declaration.expression.accept(this) 
	 */
	public Decl visit(Decl decl) {
		Decl ret = lookup(decl);
		if (ret==null) {
			final Variable variable = (Variable) decl.variable().accept(this);
			final Expression expression = decl.expression().accept(this);
			ret = (variable==decl.variable() && expression==decl.expression()) ?
				  decl : variable.oneOf(expression); 
		}
		return cache(decl,ret);
	}
	
	/** 
	 * Calls lookup(relation) and returns the cached value, if any.  
	 * If a replacement has not been cached, the relation is cached and
	 * returned.
	 * @return relation 
	 */
	public Expression visit(Relation relation) { 
		final Expression ret = lookup(relation);
		return ret==null ? cache(relation,relation) : ret; 
	}
	
	/** 
	 * Calls lookup(variable) and returns the cached value, if any.  
	 * If a replacement has not been cached, the variable is cached and
	 * returned.
	 * @return variable 
	 */
	public Expression visit(Variable variable) { 
		final Expression ret = lookup(variable);
		return ret==null ? cache(variable,variable) : variable; 
	}
	
	/** 
	 * Calls lookup(constExpr) and returns the cached value, if any.  
	 * If a replacement has not been cached, the constExpr is cached and
	 * returned.
	 * @return constExpr 
	 */
	public Expression visit(ConstantExpression constExpr) {
		final Expression ret = lookup(constExpr);
		return ret==null ? cache(constExpr,constExpr) : constExpr;
	}
	
	/** 
	 * Calls lookup(binExpr) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the expression's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement expression is cached and returned.
	 * @return { b: BinaryExpression | b.left = binExpr.left.accept(this) &&
	 *                                 b.right = binExpr.right.accept(this) && b.op = binExpr.op }
	 */
	public Expression visit(BinaryExpression binExpr) {
		Expression ret = lookup(binExpr);
		if (ret==null) {
			final Expression left  = binExpr.left().accept(this);
			final Expression right = binExpr.right().accept(this);
			ret = (left==binExpr.left() && right==binExpr.right()) ?
				  binExpr : left.compose(binExpr.op(), right);
		}
		return cache(binExpr,ret);
	}
	
	/** 
	 * Calls lookup(unaryExpr) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the expression's 
	 * child.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement expression is cached and returned.
	 * @return { u: UnaryExpression | b.left = binExpr.left.accept(this) && u.op = binExpr.op }
	 */
	public Expression visit(UnaryExpression unaryExpr) {
		Expression ret = lookup(unaryExpr);
		if (ret==null) {
			final Expression child = unaryExpr.expression().accept(this);
			ret = (child==unaryExpr.expression()) ? 
				  unaryExpr : child.apply(unaryExpr.op());
		}
		return cache(unaryExpr,ret);
	}
	
	/** 
	 * Calls lookup(comprehension) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the expression's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement expression is cached and returned.
	 * @return { c: Comprehension | c.declarations = comprehension.declarations.accept(this) &&
	 *                              c.formula = comprehension.formula.accept(this) }
	 */
	public Expression visit(Comprehension comprehension) {
		Expression ret = lookup(comprehension);
		if (ret==null) {
			final Decls decls = (Decls)comprehension.declarations().accept(this);
			final Formula formula = comprehension.formula().accept(this);
			ret = (decls==comprehension.declarations() && formula==comprehension.formula()) ? 
				  comprehension : formula.comprehension(decls); 
		}
		return cache(comprehension,ret);
	}
	
	
	/** 
	 * Calls lookup(ifExpr) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the expression's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement expression is cached and returned.
	 * @return { i: IfExpression | i.condition = ifExpr.condition.accept(this) &&
	 *                             i.thenExpr = ifExpr.thenExpr.accept(this) &&
	 *                             i.elseExpr = ifExpr.elseExpr.accept(this) }
	 */
	public Expression visit(IfExpression ifExpr) {
		Expression ret = lookup(ifExpr);
		if (ret==null) {
			final Formula condition = ifExpr.condition().accept(this);
			final Expression thenExpr = ifExpr.thenExpr().accept(this);
			final Expression elseExpr = ifExpr.elseExpr().accept(this);
			ret = (condition==ifExpr.condition() && thenExpr==ifExpr.thenExpr() &&
				   elseExpr==ifExpr.elseExpr()) ? 
			      ifExpr : condition.thenElse(thenExpr, elseExpr);
		}
		return cache(ifExpr,ret);
	}

	/** 
	 * Calls lookup(castExpr) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the expression's 
	 * child.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement expression is cached and returned.
	 * @return { i: IntCastExpression | i.intexpr = castExpr.intexpr.accept(this)}
	 */
    public Expression visit(IntCastExpression castExpr) {
    		Expression ret = lookup(castExpr);
    		if (ret==null) {
    			final IntExpression intexpr = castExpr.intexpr().accept(this);
    			ret = intexpr==castExpr.intexpr() ? castExpr : 
    				  intexpr.toExpression();
    		}
    		return cache(castExpr, ret);
    }
    
    /** 
	 * Calls lookup(intconst) and returns the cached value, if any.  
	 * If a replacement has not been cached, the constant is cached and returned.
	 * @return intconst
	 */
    public IntExpression visit(IntConstant intconst) {
    		IntExpression ret = lookup(intconst);
    		return ret==null ? cache(intconst, intconst) : intconst;
    }
    
    /** 
	 * Calls lookup(intExpr) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the expression's 
	 * child.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement expression is cached and returned.
	 * @return { i: UnaryIntExpression | i.expression = intExpr.expression.accept(this)}
	 */
    public IntExpression visit(UnaryIntExpression intExpr) {
    		IntExpression ret = lookup(intExpr);
    		if (ret==null) {
    			final Expression expr = intExpr.expression().accept(this);
    			ret = expr==intExpr.expression() ? intExpr : expr.apply(intExpr.op());
    		}
    		return cache(intExpr, ret);
    }
    /** 
	 * Calls lookup(intExpr) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the expression's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement expression is cached and returned.
	 * @return { b: BinaryIntExpression | b.left = intExpr.left.accept(this) &&
	 *                                    b.right = intExpr.right.accept(this) && b.op = intExpr.op }
	 */
    public IntExpression visit(BinaryIntExpression intExpr) {
    		IntExpression ret = lookup(intExpr);
		if (ret==null) {
			final IntExpression left  = intExpr.left().accept(this);
			final IntExpression right = intExpr.right().accept(this);
			ret = (left==intExpr.left() && right==intExpr.right()) ?
					intExpr : left.compose(intExpr.op(), right);
		}
		return cache(intExpr,ret);
    }
    /** 
     * 
	 * Calls lookup(intComp) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the formula's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement formula is cached and returned.
	 * @return { c: IntComparisonFormula | c.left = intComp.left.accept(this) &&
	 *                                     c.right = intComp.right.accept(this) &&
	 *                                     c.op = intComp.op }
	 */
    public Formula visit(IntComparisonFormula intComp) {
    		Formula ret = lookup(intComp);
		if (ret==null) {
			final IntExpression left  = intComp.left().accept(this);
			final IntExpression right = intComp.right().accept(this);
			ret =  (left==intComp.left() && right==intComp.right()) ? 
					intComp : left.compare(intComp.op(), right);
		}
		return cache(intComp,ret);
    }
	
	/**
	 * Returns the constant.
	 * @return constant
	 */
	public Formula visit(ConstantFormula constant) {
		return constant;
	}

	/** 
	 * Calls lookup(quantFormula) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the formula's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement formula is cached and returned.
	 * @return { q: QuantifiedFormula | q.declarations = quantFormula.declarations.accept(this) &&
	 *                                  q.formula = quantFormula.formula.accept(this) }
	 */
	public Formula visit(QuantifiedFormula quantFormula) {
		Formula ret = lookup(quantFormula);
		if (ret==null) {
			final Decls decls = (Decls)quantFormula.declarations().accept(this);
			final Formula formula = quantFormula.formula().accept(this);
			ret = (decls==quantFormula.declarations() && formula==quantFormula.formula()) ? 
				  quantFormula : formula.quantify(quantFormula.quantifier(), decls);
		}
		return cache(quantFormula,ret);
	}
	
	/** 
	 * Calls lookup(binFormula) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the formula's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement formula is cached and returned.
	 * @return { b: BinaryFormula | b.left = binExpr.left.accept(this) &&
	 *                              b.right = binExpr.right.accept(this) && b.op = binExpr.op }
	 */
	public Formula visit(BinaryFormula binFormula) {
		Formula ret = lookup(binFormula);
		if (ret==null) {
			final Formula left  = binFormula.left().accept(this);
			final Formula right = binFormula.right().accept(this);
			ret = (left==binFormula.left() && right==binFormula.right()) ? 
				  binFormula : left.compose(binFormula.op(), right);     
		}
		return cache(binFormula,ret);
	}
	
	/** 
	 * Calls lookup(binFormula) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the formula's 
	 * child.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement formula is cached and returned.
	 * @return { n: NotFormula | n.child = not.child.accept(this) }
	 */
	public Formula visit(NotFormula not) {
		Formula ret = lookup(not);
		if (ret==null) {
			final Formula child = not.formula().accept(this);
			ret = (child==not.formula()) ? not : child.not();
		}
		return cache(not,ret);
	}
	
	/** 
	 * Calls lookup(compFormula) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the formula's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement formula is cached and returned.
	 * @return { c: ComparisonFormula | c.left = compFormula.left.accept(this) &&
	 *                                  c.right = compFormula.right.accept(this) &&
	 *                                  c.op = compFormula.op }
	 */
	public Formula visit(ComparisonFormula compFormula) {
		Formula ret = lookup(compFormula);
		if (ret==null) {
			final Expression left  = compFormula.left().accept(this);
			final Expression right = compFormula.right().accept(this);
			ret =  (left==compFormula.left() && right==compFormula.right()) ? 
				   compFormula : left.compose(compFormula.op(), right);
		}
		return cache(compFormula,ret);
	}
	
	/** 
	 * Calls lookup(multFormula) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the formula's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement formula is cached and returned.
	 * @return { m: MultiplicityFormula | m.multiplicity = multFormula.multiplicity &&
	 *                                    m.expression = multFormula.expression.accept(this) }
	 */
	public Formula visit(MultiplicityFormula multFormula) {
		Formula ret = lookup(multFormula);
		if (ret==null) {
			final Expression expression = multFormula.expression().accept(this);
			ret = (expression==multFormula.expression()) ? 
				  multFormula : expression.apply(multFormula.multiplicity());
		}
		return cache(multFormula,ret);
	}
	
	/** 
	 * Calls lookup(pred) and returns the cached value, if any.  
	 * If a replacement has not been cached, visits the formula's 
	 * children.  If nothing changes, the argument is cached and
	 * returned, otherwise a replacement formula is cached and returned.
	 * @return { p: RelationPredicate | p.name = pred.name && p.relation = pred.relation.accept(this) &&
	 *                                  p.name = FUNCTION => p.targetMult = pred.targetMult && 
	 *                                                       p.domain = pred.domain.accept(this) &&
	 *                                                       p.range = pred.range.accept(this),
	 *                                  p.name = TOTAL_ORDERING => p.ordered = pred.ordered.accept(this) &&
	 *                                                             p.first = pred.first.accept(this) &&
	 *                                                             p.last = pred.last.accept(this) }
	 */
	public Formula visit(RelationPredicate pred) {
		Formula ret = lookup(pred);
		if (ret==null) {
			final Relation r = (Relation)pred.relation().accept(this);
			switch(pred.name()) {
			case ACYCLIC :  
				ret = (r==pred.relation()) ? pred : r.acyclic(); 
				break;
			case FUNCTION :
				final RelationPredicate.Function fp = (RelationPredicate.Function) pred;
				final Expression domain = fp.domain().accept(this);
				final Expression range = fp.range().accept(this);
				ret = (r==fp.relation() && domain==fp.domain() && range==fp.range()) ?
						fp : 
						(fp.targetMult()==Multiplicity.ONE ? r.function(domain, range) : r.functional(domain,range));
				break;
			case TOTAL_ORDERING : 
				final RelationPredicate.TotalOrdering tp = (RelationPredicate.TotalOrdering) pred;
				final Relation ordered = (Relation) tp.ordered().accept(this);
				final Relation first = (Relation)tp.first().accept(this);
				final Relation last = (Relation)tp.last().accept(this);
				ret = (r==tp.relation() && ordered==tp.ordered() && first==tp.first() && last==tp.last()) ? 
						tp : r.totalOrder(ordered, first, last);
				break;
			default :
				throw new IllegalArgumentException("unknown relation predicate: " + pred.name());
			}
			
		}
		return cache(pred,ret);
	}
	
}
