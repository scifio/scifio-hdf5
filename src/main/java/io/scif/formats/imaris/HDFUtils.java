/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2016 UC Berkeley and Board of Regents of
 * the University of Wisconsin-Madison.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package io.scif.formats.imaris;

import java.io.UnsupportedEncodingException;

import javax.swing.JOptionPane;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

/**
 * @author henrypinkard
 */
public class HDFUtils {

	// Return dataspace, datatype, dataset IDs
	public static int[] createDataSet(final int locationID, final String name,
		final long[] size, final int type) throws HDF5LibraryException,
		HDF5Exception
	{

		// 1) Create and initialize a dataspace for the dataset
		// number of dimensions, array with size of each dimension, array with max
		// size of each dimension
		final int dataSpaceID = H5.H5Screate_simple(size.length, size, null);

		// 2) Define a datatype for the dataset by using method that copies existing
		// datatype
		final int dataTypeID = H5.H5Tcopy(type);
		H5.H5Tset_order(dataTypeID, HDF5Constants.H5T_ORDER_LE);

		// 3) Create and initialize the dataset
		final int dataSetID =
			H5.H5Dcreate(locationID, name, dataTypeID, dataSpaceID,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT);

		return new int[] { dataSpaceID, dataTypeID, dataSetID };
	}

	public static int[] createCompressedDataSet(final int locationID,
		final String name, final long[] size, final int type, final long[] chunk)
		throws HDF5LibraryException, HDF5Exception
	{

		// 1) Create and initialize a dataspace for the dataset
		// number of dimensions, array with size of each dimension, array with max
		// size of each dimension
		final int dataSpaceID = H5.H5Screate_simple(size.length, size, null);

		// 2) Define a datatype for the dataset by using method that copies existing
		// datatype
		final int dataTypeID = H5.H5Tcopy(type);
		H5.H5Tset_order(dataTypeID, HDF5Constants.H5T_ORDER_LE);

		// Optionally create property list specifiying compression
		final int propListID = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
		H5.H5Pset_deflate(propListID, 2);
		H5.H5Pset_chunk(propListID, chunk.length, chunk);

		// 3) Create and initialize the dataset
		final int dataSetID =
			H5.H5Dcreate(locationID, name, dataTypeID, dataSpaceID,
				HDF5Constants.H5P_DEFAULT, propListID, HDF5Constants.H5P_DEFAULT);

		return new int[] { dataSpaceID, dataTypeID, dataSetID, propListID };
	}

	public static void writeStringAttribute(final int objectID,
		final String name, final String value) throws HDF5LibraryException,
		HDF5Exception
	{
		// Create dataspace for attribute
		final int dataspaceID =
			H5.H5Screate_simple(1, new long[] { value.length() }, null);
		final int attID =
			H5.H5Acreate(objectID, name, HDF5Constants.H5T_C_S1, dataspaceID,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		try {
			H5.H5Awrite(attID, HDF5Constants.H5T_C_S1, value.getBytes("US-ASCII"));
		}
		catch (final UnsupportedEncodingException ex) {
			JOptionPane.showMessageDialog(null, "Can't encode string");
		}
		// Close dataspace and attribute
		H5.H5Sclose(dataspaceID);
		H5.H5Aclose(attID);
	}

}
