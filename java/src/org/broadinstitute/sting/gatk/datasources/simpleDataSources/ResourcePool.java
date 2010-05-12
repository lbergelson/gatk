package org.broadinstitute.sting.gatk.datasources.simpleDataSources;

import org.broadinstitute.sting.utils.GenomeLoc;
import org.broadinstitute.sting.utils.StingException;
import org.broadinstitute.sting.utils.GenomeLocParser;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
/**
 * User: hanna
 * Date: May 21, 2009
 * Time: 10:55:26 AM
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or
 * functionality.
 */

/**
 * A pool of open resources, all of which can create a closeable iterator.
 */
abstract class ResourcePool <T,I extends Iterator> {
    /**
     * All iterators of this reference-ordered data.
     */
    private List<T> allResources = new ArrayList<T>();

    /**
     * All iterators that are not currently in service.
     */
    private List<T> availableResources = new ArrayList<T>();

    /**
     * Which iterators are assigned to which pools.
     */
    private Map<I,T> resourceAssignments = new HashMap<I,T>();

    /**
     * Get an iterator whose position is before the specified location.  Create a new one if none exists.
     * @param segment Target position for the iterator.
     * @return An iterator that can traverse the selected region.  Should be able to iterate concurrently with other
     *         iterators from tihs pool.
     */
    public I iterator( DataStreamSegment segment ) {
        // Grab the first iterator in the list whose position is before the requested position.
        T selectedResource = null;
        synchronized (this) {
            selectedResource = selectBestExistingResource(segment, availableResources);

            // No iterator found?  Create another.  It is expected that
            // each iterator created will have its own file handle.
            if (selectedResource == null) {
                selectedResource = createNewResource();
                addNewResource(selectedResource);
            }

            // Remove the iterator from the list of available iterators.
            availableResources.remove(selectedResource);


            I iterator = createIteratorFromResource(segment, selectedResource);

            // Make a note of this assignment for proper releasing later.
            resourceAssignments.put(iterator, selectedResource);

            return iterator;
        }
    }

    /**
     * Release the lock on the given iterator, returning it to the pool.
     * @param iterator Iterator to return to the pool.
     */
    public void release( I iterator ) {
        synchronized(this) {
            // Find and remove the resource from the list of allocated resources.
            T resource = resourceAssignments.get( iterator );
            Object obj = resourceAssignments.remove(iterator);

            // make sure we actually removed the assignment
            if (obj == null)
                    throw new StingException("Failed to remove resource assignment; target key had no associated value in the resource assignment map");
            // Return the resource to the pool.
            if( !allResources.contains(resource) )
                throw new StingException("Iterator does not belong to the given pool.");
            availableResources.add(resource);
        }
    }

    /**
     * Add a resource to the list of available resources.  Useful if derived classes
     * want to seed the pool with a set of at a given time (like at initialization).
     * @param resource The new resource to add.
     */
    protected void addNewResource( T resource ) {
        synchronized(this) {
            allResources.add(resource);
            availableResources.add(resource);
        }
    }

    /**
     * If no appropriate resources are found in the pool, the system can create a new resource.
     * Delegate the creation of the resource to the subclass.
     * @return The new resource created.
     */
    protected abstract T createNewResource();

    /**
     * Find the most appropriate resource to acquire the specified data. 
     * @param segment The data over which the resource is required.
     * @param availableResources A list of candidate resources to evaluate.
     * @return The best choice of the availableResources, or null if no resource meets the criteria.
     */
    protected abstract T selectBestExistingResource( DataStreamSegment segment, List<T> availableResources );

    /**
     * Create an iterator over the specified resource.
     * @param position The bounds of iteration.  The first element of the iterator through the last element should all
     *                 be in the range described by position.
     * @param resource The resource from which to derive the iterator.
     * @return A new iterator over the given data.
     */
    protected abstract I createIteratorFromResource( DataStreamSegment position, T resource );

    /**
     * Retire this resource from service.
     * @param resource The resource to retire.
     */
    protected abstract void closeResource(T resource);

    /**
     * Operating stats...get the number of total iterators.  Package-protected
     * for unit testing.
     * @return An integer number of total iterators.
     */
    int numIterators() {
        return allResources.size();
    }

    /**
     * Operating stats...get the number of available iterators.  Package-protected
     * for unit testing.
     * @return An integer number of available iterators.
     */
    int numAvailableIterators() {
        return availableResources.size();
    }

}

/**
 * Marker interface that represents an arbitrary consecutive segment within a data stream.
 */
interface DataStreamSegment {
}

/**
 * Models the entire stream of data.
 */
class EntireStream implements DataStreamSegment {
}

/**
 * Models a mapped position within a stream of GATK input data.
 */
class MappedStreamSegment implements DataStreamSegment {
    public final GenomeLoc locus;

    /**
     * Retrieves the first location covered by a mapped stream segment.
     * @return Location of the first base in this segment.
     */
    public GenomeLoc getFirstLocation() {
        return GenomeLocParser.createGenomeLoc(locus.getContigIndex(),locus.getStart());
    }

    public MappedStreamSegment(GenomeLoc locus) {
        this.locus = locus;
    }
}

/**
 * Models a position within the unmapped reads in a stream of GATK input data.
 */
class UnmappedStreamSegment implements DataStreamSegment {
    /**
     * Where does this region start, given 0 = the position of the first unmapped read.
     */
    public final long position;

    /**
     * How many reads wide is this region?  This size is generally treated as an upper bound.
     */
    public final long size;

    /**
     * Create a new target location in an unmapped read stream.
     * @param position The 0-based index into the unmapped reads.  Position 0 represents the first unmapped read.
     * @param size the size of the segment.
     */
    public UnmappedStreamSegment( long position, long size ) {
        this.position = position;
        this.size = size;
    }
}
