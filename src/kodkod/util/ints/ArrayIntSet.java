/**
 * 
 */
package kodkod.util.ints;

import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * An immutable set of integers, stored in a sorted array.
 * 
 * @specfield ints: set int
 * @author Emina Torlak
 */
public final class ArrayIntSet extends AbstractIntSet {
	private final int[] ints;
	private final int hashcode;
	/**
	 * Constructs a set view for the given array.  The array must contain no duplicates, 
	 * its elements must be sorted
	 * in the ascending order, and its contents
	 * must not be changed while it is in use by this set
	 * @requires all i, j: [0..ints.length) | i < j => array[i] <= Sarray[j]
	 * @effects this.ints' = ints
	 */
	public ArrayIntSet(int[] ints) {
		this.ints = ints;
		this.hashcode = Ints.superFastHash(ints);
	}
	
	/**
	 * Constructs an ArrayIntSet that is <tt>equal</tt> to the
	 * given set.
	 * @effects this.ints' = s.ints
	 */
	public ArrayIntSet(IntSet s) {
		this(s.toArray());
	}
	
	/**
	 * @see kodkod.util.ints.AbstractIntSet#iterator(int, int)
	 */
	@Override
	public IntIterator iterator(final int from, final int to) {
		return from <= to ? new AscendingIntArrayIterator(from,to) : new DescendingIntArrayIterator(from,to);
	}

	/**
	 * {@inheritDoc}
	 * @see kodkod.util.ints.IntSet#size()
	 */
	public int size() {
		return ints.length;
	}

	/**
	 * @see kodkod.util.ints.IntSet#ceil(int)
	 */
	public int ceil(int i) {
		final int index = Arrays.binarySearch(ints, i);
		if (index==-ints.length-1) throw new NoSuchElementException();
		else return index >= 0 ? ints[index] : ints[-index-1];
	}

	/**
	 * @see kodkod.util.ints.IntSet#floor(int)
	 */
	public int floor(int i) {
		final int index = Arrays.binarySearch(ints, i);
		if (index==-1) throw new NoSuchElementException();
		else return index >= 0 ? ints[index] : ints[-index-2];
	}

	/**
	 * @see kodkod.util.ints.AbstractIntSet#contains(int)
	 */
	public boolean contains(int i) {
		return Arrays.binarySearch(ints, i) >= 0;
	}

	/**
	 * @see kodkod.util.ints.AbstractIntSet#max()
	 */
	public int max() {
		if (ints.length==0) throw new NoSuchElementException();
		return ints[ints.length-1];
	}

	/**
	 * @see kodkod.util.ints.AbstractIntSet#min()
	 */
	public int min() {
		if (ints.length==0) throw new NoSuchElementException();
		return ints[0];
	}
	
	/**
	 * {@inheritDoc}
	 * @see kodkod.util.ints.AbstractIntSet#toArray()
	 */
	public int[] toArray() {
		final int[] ret = new int[ints.length];
		System.arraycopy(ints, 0, ret, 0, ints.length);
		return ret;
	}
	
	/**
	 * {@inheritDoc}
	 * @see kodkod.util.ints.IntSet#copyInto(int[])
	 */
	public void copyInto(int[] array) {
		if (array.length < size()) 
			throw new IndexOutOfBoundsException();
		System.arraycopy(ints, 0, array, 0, ints.length);
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.util.AbstractSet#hashCode()
	 */
	public int hashCode() { return hashcode; }
	
	/**
	 * {@inheritDoc}
	 * @see kodkod.util.ints.AbstractIntSet#equals(java.lang.Object)
	 */
	public boolean equals(Object o) {
		return (o instanceof ArrayIntSet) ? 
				java.util.Arrays.equals(ints, ((ArrayIntSet)o).ints) : 
				super.equals(o);
	}
	
	private abstract class IntArrayIterator implements IntIterator {
		int next, end;
		public final Integer next() { return nextInt(); }
		public final void remove() { throw new UnsupportedOperationException(); }
	}
	
	private final class AscendingIntArrayIterator extends IntArrayIterator {
		/**
		 * Constructs a new AscendingIntArrayIterator.
		 * @requires from <= to
		 */
		AscendingIntArrayIterator(int from, int to) {
			final int fromIndex = Arrays.binarySearch(ints, from);
			final int toIndex = Arrays.binarySearch(ints, to);
			next = fromIndex >= 0 ? fromIndex : -fromIndex-1;
			end = toIndex >=0 ? toIndex : -toIndex-2;
		}
		public boolean hasNext() { return next <= end; }
		public int nextInt() {
			if (!hasNext()) throw new NoSuchElementException();
			return ints[next++];
		}
	}
	
	private final class DescendingIntArrayIterator extends IntArrayIterator {
		/**
		 * Constructs a new AscendingIntArrayIterator.
		 * @requires from >= to
		 */
		DescendingIntArrayIterator(int from, int to) {
			final int fromIndex = Arrays.binarySearch(ints, from);
			final int toIndex = Arrays.binarySearch(ints, to);
			next = fromIndex >= 0 ? fromIndex : -fromIndex-2;
			end = toIndex >=0 ? toIndex : -toIndex-1;
		}
		public boolean hasNext() { return next >= end; }
		public int nextInt() {
			if (!hasNext()) throw new NoSuchElementException();
			return ints[next--];
		}
	}

}
