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

//This class encapsulates all the data object IDs for a given timepoint
import javax.swing.JOptionPane;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

public class TimePoint {

	private static final int HISTOGRAM_SIZE = 256;
	private final ResolutionLevel[] resLevels_;
	// res index, channel index array of channel Groups
	private final ChannelGroup[][] channelGroups_;
	private final boolean compressImageData_;

	// Constructor creates all data structures that are populated later
	public TimePoint(final ResolutionLevel[] resLevels, final int[] resLevelIDs,
		final int numChannels, final int frameIndex, final int bitDepth,
		final boolean compressImageData) throws HDF5LibraryException, HDF5Exception
	{
		compressImageData_ = compressImageData;
		resLevels_ = resLevels;
		channelGroups_ = new ChannelGroup[resLevels.length][numChannels];

		for (int resIndex = 0; resIndex < resLevels.length; resIndex++) {
			// Create time point
			final int timePointID =
				H5.H5Gcreate(resLevelIDs[resIndex], "TimePoint " + frameIndex,
					HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);

			final ResolutionLevel resLevel = resLevels[resIndex];
			for (int channelIndex = 0; channelIndex < numChannels; channelIndex++) {
				channelGroups_[resIndex][channelIndex] =
					new ChannelGroup(timePointID, channelIndex, resLevel, bitDepth);
			}
			H5.H5Gclose(timePointID);
		}
	}

	// should be called by the addImage function of the HDFWriter
	public void writePixels(final PipelineImage img) throws Exception {
		// This function recieves several slices processed into multiple
		// resolutions, stored in a
		// 3 dimensional array with indices: resolution level, slice index, pixel
		// index
		// At the lower resolutions, an image is stored at the lowest slice index of
		// the higher resolution
		// images from which it is comprised
		final int channel = img.channel;

		final Object[][] imageData = (Object[][]) img.pixels;

		for (int resIndex = 0; resIndex < resLevels_.length; resIndex++) {
			// write histogram if last slice in channel
			if (img.histograms != null) {
				channelGroups_[resIndex][channel].writeHistogram(img, resIndex);
			}

			final Object[] sliceArray = imageData[resIndex];
			for (int sliceIndex = 0; sliceIndex < sliceArray.length; sliceIndex++) {
				if (sliceArray[sliceIndex] != null) {
					// there is data in this slice at this resolution level
					final int dataSlice =
						(img.slice + sliceIndex) /
							resLevels_[resIndex].getReductionFactorZ();
					channelGroups_[resIndex][channel].writeSlice(resLevels_[resIndex]
						.getImageSizeX(), resLevels_[resIndex].getImageSizeY(), dataSlice,
						sliceArray[sliceIndex]);
				}
			}
		}
	}

	// Close channel Group
	public void closeTimePoint() throws HDF5LibraryException, HDF5Exception {
		for (int res = 0; res < channelGroups_.length; res++) {
			for (int channel = 0; channel < channelGroups_[0].length; channel++) {
				channelGroups_[res][channel].close();
			}
		}
	}

	private class ChannelGroup {

		private final ResolutionLevel resLevel_;
		private int[] histogramIDs_;
		private int[] imageDataIDs_;

		public ChannelGroup(final int timePointID, final int channelIndex,
			final ResolutionLevel resLevel, final int bitDepth)
			throws HDF5LibraryException, HDF5Exception
		{
			resLevel_ = resLevel;
			final int id =
				H5.H5Gcreate(timePointID, "Channel " + channelIndex,
					HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			// Add channel attributes, image data, histogram
			HDFUtils.writeStringAttribute(id, "HistogramMax", ((int) Math.pow(2,
				bitDepth) - 1) +
				".000");
			HDFUtils.writeStringAttribute(id, "HistogramMin", "0.000");
			HDFUtils.writeStringAttribute(id, "ImageBlockSizeX", "" +
				resLevel_.getXBlockSize());
			HDFUtils.writeStringAttribute(id, "ImageBlockSizeY", "" +
				resLevel_.getYBlockSize());
			HDFUtils.writeStringAttribute(id, "ImageBlockSizeZ", "" +
				resLevel_.getZBlockSize());
			HDFUtils.writeStringAttribute(id, "ImageSizeX", "" +
				resLevel_.getImageSizeX());
			HDFUtils.writeStringAttribute(id, "ImageSizeY", "" +
				resLevel_.getImageSizeY());
			HDFUtils.writeStringAttribute(id, "ImageSizeZ", "" +
				resLevel_.getImageSizeZ());

//         Create histograms
			histogramIDs_ =
				HDFUtils.createDataSet(id, "Histogram", new long[] { HISTOGRAM_SIZE },
					HDF5Constants.H5T_NATIVE_UINT64);

			// Create image datasets
			if (compressImageData_) {
				imageDataIDs_ =
					HDFUtils.createCompressedDataSet(id, "Data", new long[] {
						resLevel.getContainerSizeZ(), resLevel.getContainerSizeY(),
						resLevel.getContainerSizeX() }, resLevel.getImageByteDepth() == 1
						? HDF5Constants.H5T_NATIVE_UCHAR : HDF5Constants.H5T_NATIVE_UINT16,
						new long[] { resLevel.getZBlockSize(), resLevel.getYBlockSize(),
							resLevel.getXBlockSize() });
			}
			else {
				imageDataIDs_ =
					HDFUtils.createDataSet(id, "Data", new long[] {
						resLevel.getContainerSizeZ(), resLevel.getContainerSizeY(),
						resLevel.getContainerSizeX() }, resLevel.getImageByteDepth() == 1
						? HDF5Constants.H5T_NATIVE_UCHAR : HDF5Constants.H5T_NATIVE_UINT16);
			}

			H5.H5Gclose(id);
		}

		private void writeHistogram(final PipelineImage img, final int resIndex)
			throws HDF5LibraryException, HDF5Exception
		{
//         Write and close histogram
			final int memDataSpaceID =
				H5.H5Screate_simple(1, new long[] { HISTOGRAM_SIZE }, null);
			try {
				final long[] histogram = img.histograms[resIndex];
				H5.H5Dwrite_long(histogramIDs_[2], histogramIDs_[1], memDataSpaceID,
					histogramIDs_[0], HDF5Constants.H5P_DEFAULT, histogram);
			}
			catch (final Exception e) {
				JOptionPane.showMessageDialog(null,
					"Couldn't write histogram: channel " + img.channel + " slice: " +
						img.slice + " frame: " + img.frame + " resIndex: " + resIndex);
			}
			H5.H5Sclose(memDataSpaceID);
			closeHistograms();
			histogramIDs_ = null;
		}

		private void closeHistograms() throws HDF5LibraryException {
			H5.H5Sclose(histogramIDs_[0]);
			H5.H5Tclose(histogramIDs_[1]);
			H5.H5Dclose(histogramIDs_[2]);
		}

		private void close() throws HDF5LibraryException, HDF5Exception {
			if (histogramIDs_ != null) {
				// if writing cancelled
				closeHistograms();
			}

			// Close image data
			H5.H5Sclose(imageDataIDs_[0]);
			H5.H5Tclose(imageDataIDs_[1]);
			H5.H5Dclose(imageDataIDs_[2]);
			if (compressImageData_) {
				H5.H5Pclose(imageDataIDs_[3]);
			}
			imageDataIDs_ = null;

		}

		private void writeSlice(final int width, final int height,
			final int dataSlice, final Object pixels) throws Exception
		{
			final long[] start = new long[] { dataSlice, 0, 0 };
			// count is total number of points in each dimension
			final long[] count = new long[] { 1, height, width };
			int ret =
				H5.H5Sselect_hyperslab(imageDataIDs_[0], HDF5Constants.H5S_SELECT_SET,
					start, null, count, null);

			// Create dataspace in memory to copy from
			final int memDataSpaceID =
				H5.H5Screate_simple(1, new long[] { width * height }, null);
			ret = H5.H5Sselect_all(memDataSpaceID);

			ret =
				H5.H5Dwrite(imageDataIDs_[2], pixels instanceof byte[]
					? HDF5Constants.H5T_NATIVE_UCHAR : HDF5Constants.H5T_NATIVE_UINT16,
					memDataSpaceID, imageDataIDs_[0], HDF5Constants.H5P_DEFAULT, pixels);

			H5.H5Sclose(memDataSpaceID);
		}
	}
}
