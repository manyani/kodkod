package kodkod.engine.fol2sat;

import static kodkod.ast.BinaryFormula.Operator.AND;
import static kodkod.ast.BinaryFormula.Operator.IFF;
import static kodkod.ast.BinaryFormula.Operator.IMPLIES;
import static kodkod.ast.BinaryFormula.Operator.OR;
import static kodkod.ast.QuantifiedFormula.Quantifier.ALL;
import static kodkod.ast.QuantifiedFormula.Quantifier.SOME;
import static kodkod.ast.RelationPredicate.Name.ACYCLIC;
import static kodkod.ast.RelationPredicate.Name.FUNCTION;
import static kodkod.ast.RelationPredicate.Name.TOTAL_ORDERING;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import kodkod.ast.BinaryFormula;
import kodkod.ast.ComparisonFormula;
import kodkod.ast.ConstantExpression;
import kodkod.ast.ExprToIntCast;
import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IntComparisonFormula;
import kodkod.ast.IntToExprCast;
import kodkod.ast.MultiplicityFormula;
import kodkod.ast.Node;
import kodkod.ast.NotFormula;
import kodkod.ast.QuantifiedFormula;
import kodkod.ast.Relation;
import kodkod.ast.RelationPredicate;
import kodkod.ast.visitor.DepthFirstDetector;
import kodkod.ast.visitor.DepthFirstVoidVisitor;
import kodkod.util.collections.IdentityHashSet;

/**
 * A node annotated with information about
 * structural sharing in its ast/dag.  The class
 * also provides utility methods for collecting
 * various information about annotated nodes.
 * 
 * @specfield node: N // annotated node
 * @author Emina Torlak
 */ 
final class AnnotatedNode<N extends Node> {
	private final N node;
	private final Set<Node> sharedNodes;
	
	
	/**
	 * Constructs a new annotator for the given node.
	 * @effects this.node' = node
	 */
	AnnotatedNode(N node) {
		this.node = node;
		final SharingDetector detector = new SharingDetector();
		node.accept(detector);
		this.sharedNodes = Collections.unmodifiableSet(detector.sharedNodes());
	}

	/**
	 * Constructs a new annotator for the given node.
	 * @requires sharedNodes = {n: Node | some n.children && #(n.~children & node.*children) > 1 }
	 * @effects this.node' = node
	 */
	AnnotatedNode(N node, Set<Node> sharedNodes) {
		this.node = node;
		this.sharedNodes = sharedNodes;
	}
	
	/**
	 * Returns this.node.
	 * @return this.node
	 */
	final N node() {
		return node;
	}
	
	/**
	 * Returns the set of all non-leaf descendents
	 * of this.node that have more than one parent.
	 * @return {n: Node | some n.children && #(n.~children & this.node.*children) > 1 }
	 */
	final Set<Node> sharedNodes() { 
		return sharedNodes;
	}
	
	/**
	 * Returns the set of all relations at the leaves of the given annotated node.
	 * @return Relation & annotated.node.*children
	 */
	static Set<Relation> relations(final AnnotatedNode<? extends Node> annotated) {
		final Set<Relation> relations = new IdentityHashSet<Relation>();
		final DepthFirstVoidVisitor visitor = new DepthFirstVoidVisitor() {
			private final Set<Node> shared = annotated.sharedNodes;
			private final Set<Node> visited = new IdentityHashSet<Node>(shared.size());
			protected boolean visited(Node n) {
				return shared.contains(n) && !visited.add(n);
			}
			public void visit(Relation relation) {
				relations.add(relation);
			}
		};
		annotated.node.accept(visitor);
		return relations;
	}
	
	/**
	 * Returns true if the given node contains a child whose meaning depends on 
	 * integer bounds (i.e. an ExprToIntCast node with SUM operator or an IntToExprCast node).
	 * @return true if the given node contains a child whose meaning depends on 
	 * integer bounds (i.e. an ExprToIntCast node with SUM operator or an IntToExprCast node).
	 */
	static boolean usesIntBounds(final AnnotatedNode<? extends Node> annotated) {
		final DepthFirstDetector detector = new DepthFirstDetector(annotated.sharedNodes) {
			public Boolean visit(IntToExprCast expr) {
				return Boolean.TRUE;
			}
			public Boolean visit(ExprToIntCast intExpr) {
				if (intExpr.op()==ExprToIntCast.Operator.CARDINALITY)
					super.visit(intExpr);
				return Boolean.TRUE;
			}
			public Boolean visit(ConstantExpression expr) {
				return expr==Expression.INTS ? Boolean.TRUE : Boolean.FALSE;
			}
		};
		return (Boolean)annotated.node.accept(detector);
	}
	
	/**
	 * Returns the set of all skolemizable formulas at or above
	 * the given depth in annotated.node.  A depth of 0 means that only existentials not
	 * nested inside of universals are returned.  A depth of 1 means that the existentials 
	 * nested within a single universal are also returned, etc.
	 * @requires depth >= 0
	 * @return the set of all top-level existentially quantified formulas in annotated.node
	 */
	static Set<QuantifiedFormula> skolemizables(AnnotatedNode<Formula> annotated, final int depth) {
		final NodeDetector detector = new NodeDetector(annotated.sharedNodes) {
			public Boolean visit(QuantifiedFormula quantFormula) {
				return cache(quantFormula, true);
			}
		};
		final Set<QuantifiedFormula> formulas = new IdentityHashSet<QuantifiedFormula>();
		final Collector collector = new Collector(detector.detectAll()) {
			private int currentDepth = depth;
			public void visit(QuantifiedFormula quantFormula) {
				if (!visited(quantFormula)) { 
					final boolean oldTop = topLevel;
					topLevel = false;
					quantFormula.declarations().accept(this);

					final QuantifiedFormula.Quantifier q = quantFormula.quantifier();
					final boolean existential = (q==SOME && !negated || q==ALL && negated);
					if (oldTop && existential) {
						if (status(quantFormula)==flagCombo()) {
							formulas.add(quantFormula);
						}
					} else {
						formulas.remove(quantFormula);
					}
					final int oldDepth = currentDepth;
					currentDepth -= existential ? 0 : quantFormula.declarations().size();
					topLevel = oldTop && (currentDepth>=0);
					quantFormula.formula().accept(this);
					topLevel = oldTop;
					currentDepth = oldDepth;
				}
			}
		};
		annotated.node.accept(collector);
		return formulas;
	}
	
	/**
	 * Returns a map of RelationPredicate names to sets of top-level relation predicates with
	 * the corresponding names in the given annotated formula.
	 * @return a map of RelationPredicate names to sets of top-level relation predicates with
	 * the corresponding names in the annotated formula.  A predicate is considered 'top-level' if 
	 * it is not transitively reachable from a quantified formula inside of annotated.node, and if it is a 
	 * component of the top-level conjunction, if any, of annotated.node.  
	 */
	static Map<RelationPredicate.Name, Set<RelationPredicate>> predicates(AnnotatedNode<Formula> annotated) {
		final NodeDetector detector = new NodeDetector(annotated.sharedNodes) {
			public Boolean visit(RelationPredicate pred) {
				return cache(pred, true);
			}
		};
		final EnumMap<RelationPredicate.Name, Set<RelationPredicate>> preds = 
			new EnumMap<RelationPredicate.Name, Set<RelationPredicate>>(RelationPredicate.Name.class);	
		preds.put(ACYCLIC, new IdentityHashSet<RelationPredicate>(4));
		preds.put(TOTAL_ORDERING, new IdentityHashSet<RelationPredicate>(4));
		preds.put(FUNCTION, new IdentityHashSet<RelationPredicate>(8));
		final Collector collector = new Collector(detector.detectAll()) {
			public void visit(RelationPredicate pred) {
				super.visit(pred);
				if (status(pred)==2) {
					preds.get(pred.name()).add(pred);
				} else {
					preds.get(pred.name()).remove(pred);
				}
			};
		};
		annotated.node.accept(collector);
		return preds;
	}
	
	/**
	 * Detects shared non-leaf descendents of a given node.
	 * 
	 * @specfield node: Node // node to which the analyzer is applied
	 */
	private static final class SharingDetector extends DepthFirstVoidVisitor {
		/* maps each internal node with more than one parent to TRUE and all
		 * other internal nodes to FALSE */
		final IdentityHashMap<Node,Boolean> sharingStatus;
		/* @invariant numShareNodes = #sharingStatus.TRUE */
		int numSharedNodes;
		
		SharingDetector() {
			sharingStatus = new IdentityHashMap<Node,Boolean>();
		}
		
		/**
		 * Returns the shared internal nodes of this.node.  This method should
		 * be called only after this visitor has been applied to this.node.
		 * @return {n: Node | #(n.~children & node.*children) > 1 }
		 */
		IdentityHashSet<Node> sharedNodes() {
			final IdentityHashSet<Node> shared = new IdentityHashSet<Node>(numSharedNodes);
			for(Map.Entry<Node,Boolean> entry : sharingStatus.entrySet()) {
				if (entry.getValue()==Boolean.TRUE)
					shared.add(entry.getKey());
			}
			return shared;
		}
		
		/**
		 * Records the visit to the given node in the status map.
		 * If the node has not been visited before, it is mapped
		 * to Boolean.FALSE and false is returned.  Otherwise, 
		 * it is mapped to Boolean.TRUE and true is returned.
		 * The first time a Node is mapped to true, numSharedNodes
		 * is incremented by one.
		 * @effects no this.shared[node] => this.shared' = this.shared + node->FALSE,
		 *          this.shared[node] = FALSE => this.shared' = this.shared + node->TRUE,
		 *          this.shared' = this.shared
		 * @return this.shared'[node]
		 */
		protected final boolean visited(Node node) {
			Boolean status = sharingStatus.get(node);
			if (!Boolean.TRUE.equals(status)) {
				if (status==null) {
					status = Boolean.FALSE;
				} else { // status == Boolean.FALSE
					status = Boolean.TRUE;
					numSharedNodes++;
				}
				sharingStatus.put(node,status);
			}
			return status;
		}
	}
	/**
	 * Provides an additional method to the DepthFirstDetector class
	 * that returns the result of applying a given detector d to 
	 * d.cached.
	 * @author Emina Torlak
	 */
	private abstract static  class NodeDetector extends DepthFirstDetector {
		/**
		 * Constructs a new NodeDetector that will cache the results of 
		 * visiting the given nodes.
		 * @effects this.cached' = nodes
		 */
		NodeDetector(Set<Node> nodes) {
			super(nodes);
		}
		/**
		 * Returns the result of applying this detector
		 * to the set of nodes with which it was constructed.
		 * @effects all n: this.cached | n.accept(this)
		 * @return this.cached'
		 */
		final Map<Node,Boolean> detectAll() {
			for(Node n: cached)
				n.accept(this);
//			System.out.println(cache);
			return cache;
		}
	}
	
	/**
	 * A skeleton implementation of a visitor that collects
	 * top-level formulas of a given type; i.e. formulas that
	 * are components in the top-level conjunction, if any, on ALL
	 * possible paths starting at the root formula.
	 * The default implementation does not collect any formulas;
	 * subclasses should override the visit(F) method and provide 
	 * the collection code.  For example, to collect all top level
	 * MultiplicityFormulas, override visit(MultiplicityFormula f) as follows:
	 * <pre>
	 * public void visit(MultiplicityFormula f) {
	 *   super.visit(f);
	 *   if (status()==2) // f has only been visited with negated false and topLevel true
	 *     this.multiplicityFormulas.add(f);
	 *   else
	 *     this.multiplicityFormulas.remove(f); // we've seen f with an invalid combination of flags
	 * }
	 * </pre>
	 * 
	 * @specfield root: Formula // the formula to which this visitor will be applied
	 * @specfield F: Class<? extends Formula> // the type of formula being collected
	 * @specfield negation, topLevel : boolean // flags used for bookkeeping
	 * @author Emina Torlak
	 */
	private static abstract class Collector extends DepthFirstVoidVisitor {
		/* @invariant stores numbers [0..15] which represent the powerset 
		 * of the set {FF, FT, TF, TT}. The numbers 1, 2, 4, 
		 * and 8 represent the elements FF, FT, TF, and TT, respectively.  
		 * The members of the power set are, therefore, represented 
		 * as the bitwise OR of the elements they contain.
		 */
		private final Byte[] flagCombos;
		
		/* @invariant maps all nodes shared nodes to a byte, depending on 
		 * which combination of flags was active when the node was visited.
		 * For example, if, during each visit to a node n, the flags negated
		 * and topLevel were set to either FT or TT, visitedNodes
		 * would map n to flagCombos[2 | 8] = flagCombos[10].  Initially,
		 * shared nodes with a descendent of type T are mapped to flagCombos[0], 
		 * and those without a descendent of type T are mapped to flagCombos[15] 
		 * (since we never have to visit them).
		 */
		private final Map<Node, Byte> visitedNodes;
		
		boolean negated, topLevel;
		
		/**
		 * Constructs a new formula collector that will use the provided
		 * map to decide if some nodes should be visited more than once.
		 * The argument map must be modifiable.
		 * @requires sharedInfo.keySet() = {n: Node | #(n.~children & root.*children) > 1 }
		 * @requires all n : sharedInfo.keySet() | 
		 *   some (n.*children).getClass() & formulaType => sharedInfo.get(n) = Boolean.TRUE,
		 *   sharedInfo.get(n) = Boolean.FALSE
		 * @effects this.topLevel' = true && this.negated' = false
		 */
		@SuppressWarnings("unchecked") Collector(Map<Node, Boolean> sharedInfo) {
			this.negated = false;
			this.topLevel = true;
			this.flagCombos = new Byte[16];
			for(int i = 0; i < 16; i++) {
				flagCombos[i] = Byte.valueOf((byte)i);
			}
			for(Map.Entry e : sharedInfo.entrySet()) {
				e.setValue(e.getValue()==Boolean.TRUE ? flagCombos[0] : flagCombos[15]);
			}
			this.visitedNodes = (Map<Node, Byte>) ((Map<Node, ?>) sharedInfo);
		}
		
		/**
		 * Translates the current value of the negated/topLevel flags
		 * into an integral representation that glues the two bits
		 * together, negated bit first.
		 */
		final int flagCombo() {
			return 1 << ((negated ? 2 : 0) | (topLevel ? 1 : 0));
		}
		
		/**
		 * Returns an integer that encodes all flag combinations with
		 * which the given node has been visited so far.  The numbers 1, 2, 4, 
		 * and 8 represent the flag settings FF, FT, TF, and TT, respectively, 
		 * where the most significant bit corresponds to the negated flag and the least significant bit
		 * to the topLevel flag.  For example, if, during each visit to n, the flags negated
		 * and topLevel were set to either FT or TT, status of n would be 2 | 8. 
		 * @return an integer that encodes all flag combinations with
		 * which the given node has been visited so far
		 */
		final int status(Node n) {
			final Byte status = visitedNodes.get(n);
			return status == null ? flagCombo() : flagCombo() | status;
		}
		
		/**
		 * Returns true if the given node has already been visited with 
		 * the present combination of the negated and topLevel flags.  If not, the present
		 * combination is recorded and false is returned.  
		 * @return true if the given node has already been visited with 
		 * the present combination of the negated and topLevel flags.  If not, the present
		 * combination is recorded and false is returned.  
		 */
		protected final boolean visited(Node node) {
			final Byte status = visitedNodes.get(node);
			if (status == null) return false; 
			final int current = flagCombo();
			if ((status & current) == 0) { // not seen yet
				// if the current value of the topLevel flag is false,
				// we don't have to visit this node ever again, so we
				// simply mark it as having been visited with all possible
				// flag combinations
				if (!topLevel)
					visitedNodes.put(node, flagCombos[15]);
				else
					visitedNodes.put(node, flagCombos[status|current]);
				return false;
			} else { // combination seen
				return true;
			}
		}
			
		/**
		 * If the given formula has not been visited, both its children
		 * are visited with the flags temporarily set as follows:
		 * this.topLevel' = !this.negated && binFormula.op=AND ||  
		 *  this.negated && (binformula.op==OR || binFormula.op==IMPLIES)
		 * the left child of an IMPLIES formula is visited with this.negated flipped,
		 * and the children of an IFF formula are visited twice, with this.negated 
		 * taking on both true and false.
		 */
		public void visit(BinaryFormula binFormula) {
			if (!visited(binFormula)) {
				final boolean oldTop = topLevel;
				final BinaryFormula.Operator op = binFormula.op();
				if (op==IFF || negated && op==AND || !negated && (op==OR || op==IMPLIES)) {
					topLevel = false;
				}
				if (op==IMPLIES) { // a => b = !a || b one negation on the left side
					negated = !negated;
					binFormula.left().accept(this);
					negated = !negated;
					binFormula.right().accept(this);
				} else if (op==IFF) { // a<=>b = (!a || b) && (!b || a) both sides negated and not negated
					negated = !negated;
					super.visit(binFormula);
					negated = !negated;
					super.visit(binFormula);
				} else { // op==AND || op==OR
					super.visit(binFormula);
				}
				topLevel = oldTop;
			}
		}
		
		/**
		 * If the given formula has not been visited, its child
		 * is visited with this.negated flag flipped.
		 */
		public void visit(NotFormula not) {
			if (!visited(not)) {
				negated = !negated;
				not.formula().accept(this);
				negated = !negated;
			}
		}

		/**
		 * If the given formula has not been visited, both of its children
		 * are visited with the topLevel flag set to false.
		 */
		public void visit(QuantifiedFormula quantFormula) {
			if (!visited(quantFormula)) { 
				final boolean oldTop = topLevel;
				topLevel = false;
				quantFormula.declarations().accept(this);
				quantFormula.formula().accept(this);
				topLevel = oldTop;
			}
		}
		
		/**
		 * If the given formula has not been visited, both of 
		 * its children are visited with the topLevel flag set to false.
		 */
		public void visit(ComparisonFormula compFormula) {
			if (!visited(compFormula)) {
				final boolean oldTop = topLevel;
				topLevel = false;
				compFormula.left().accept(this);
				compFormula.right().accept(this);
				topLevel = oldTop;
			}
		}
		
		/**
		 * If the given formula has not been visited, its 
		 * child is visited with the topLevel flag set to false.
		 */
		public void visit(MultiplicityFormula multFormula) {
			if (!visited(multFormula)) {
				final boolean oldTop = topLevel;
				topLevel = false;
				multFormula.expression().accept(this);
				topLevel = oldTop;
			}
		}
		
		/**
		 * If the given formula has not been visited, its 
		 * children are visited with the topLevel flag set to false.
		 */
		public void visit(RelationPredicate pred) {
			if (!visited(pred)) {
				final boolean oldTop = topLevel;
				topLevel = false;
				super.visit(pred);
				topLevel = oldTop;
			}
		}
		
		/**
		 * If the given formula has not been visited, its children
		 * are visited with the topLevel flag set to false
		 */
		public void visit(IntComparisonFormula intComp) {
			if (!visited(intComp)) {
				final boolean oldTop = topLevel;
				topLevel = false;
				intComp.left().accept(this);
				intComp.right().accept(this);
				topLevel = oldTop;
			}
		}
	}
}
