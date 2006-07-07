/*
 * Variable.java
 * Created on May 24, 2005
 */
package kodkod.ast;

import kodkod.ast.visitor.ReturnVisitor;
import kodkod.ast.visitor.VoidVisitor;

/** 
 * Represents a variable in a quantified formula or a comprehension expression,
 * or a let statement, etc.  Two variables are the same if and only if they
 * refer to the same object.  That is, v1.eauls(v2) <=> v1 == v2.  Each
 * variable has a name, which is basically a comment for the purpose of 
 * printing, viewing, etc.  The name has no meaning otherwise.  The arity of
 * a variable specifies the arity of expressions over which the variable can
 * range.
 * 
 * @specfield name: String
 * @specfield arity: int
 * @invariant no children
 * @author Emina Torlak 
 */
public final class Variable extends LeafExpression {

	/**
	 * Constructs a variable with the specified name and arity 1.
	 * @effects this.name' = name && this.arity' = 1
	 */
	private Variable(String name) {
		super(name, 1);
	}

	/**
	 * Constructs a variable with the specified name and arity.
	 * @effects this.name' = name && this.arity' = arity
	 */
	private Variable(String name, int arity) {
		super(name, arity);
	}

	/**
	 * Returns a new variable with the specified name and arity 1.
	 * @effects this.name' = name && this.arity' = 1
	 */
	public static Variable unary(String name) {
		return new Variable(name);
	}

	/**
	 * Returns a new variable with the specified name and arity.
	 * @effects this.name' = name && this.arity' = arity
	 * @throws IllegalArgumentException - arity < 1
	 */
	public static Variable nary(String name, int arity) {
		return new Variable(name, arity);
	}

	/**
	 * Returns the declaration that constrains this variable to 
	 * be bound to at most one element of the given expression:  'this: lone expr'.
	 * @return {d: Decl | d.variable = this && d.multiplicity = LONE && d.expression = expr }
	 * @throws NullPointerException - expr = null
	 * @throws IllegalArgumentException - this.arity != expr.arity || expr.arity != 1
	 */
	public Decl loneOf(Expression expr) {
		return new Decl(this, Multiplicity.LONE, expr);
	}

	/**
	 * Returns the declaration that constrains this variable to 
	 * be bound to exactly one element of the given expression:  'this: one expr'.
	 * @return {d: Decl | d.variable = this && d.multiplicity = ONE && d.expression = expr }
	 * @throws NullPointerException - expr = null
	 * @throws IllegalArgumentException - this.arity != expr.arity || expr.arity != 1
	 */
	public Decl oneOf(Expression expr) {
		return new Decl(this, Multiplicity.ONE, expr);
	}

	/**
	 * Returns the declaration that constrains this variable to 
	 * be bound to at least one element of the given expression:  'this: some expr'.
	 * @return {d: Decl | d.variable = this && d.multiplicity = SOME && d.expression = expr }
	 * @throws NullPointerException - expr = null
	 * @throws IllegalArgumentException - this.arity != expr.arity || expr.arity != 1
	 */
	public Decl someOf(Expression expr) {
		return new Decl(this, Multiplicity.SOME, expr);
	}

	/**
	 * Returns the declaration that constrains this variable to 
	 * be bound to a subset of the elements in the given expression:  'this: set expr'.
	 * @return {d: Decl | d.variable = this && d.multiplicity = SET && d.expression = expr }
	 * @throws NullPointerException - expr = null
	 * @throws IllegalArgumentException - this.arity != expr.arity 
	 */
	public Decl setOf(Expression expr) {
		return new Decl(this, Multiplicity.SET, expr);
	}

	/**
	 * Returns the declaration that constrains this variable to 
	 * be bound to the specified number of the elements in the given expression:  'this: mult expr'.
	 * @return {d: Decl | d.variable = this && d.multiplicity = mult && d.expression = expr }
	 * @throws NullPointerException - expression = null || mult = null
	 * @throws IllegalArgumentException - mult = NO
	 * @throws IllegalArgumentException - mult in ONE + LONE + SOME && expr.arity != 1
	 * @throws IllegalArgumentException - this.arity != expr.arity
	 */
	public Decl declare(Multiplicity mult, Expression expr) {
		return new Decl(this, mult, expr);
	}

	/**
	 * Returns true of o is a BinaryFormula with the
	 * same tree structure as this.
	 * @return o.op.equals(this.op) && o.left.equals(this.left) && o.right.equals(this.right) 
	 */
	public <E, F, D, I> E accept(ReturnVisitor<E, F, D, I> visitor) {
		return visitor.visit(this);
	}

	/**
	 * Accepts the given visitor.
	 * @see kodkod.ast.Node#accept(kodkod.ast.visitor.VoidVisitor)
	 */
	public void accept(VoidVisitor visitor) {
		visitor.visit(this);
	}

}
