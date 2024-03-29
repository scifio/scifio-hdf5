/*
 * #%L
 * SCIFIO library for reading and converting scientific file formats.
 * %%
 * Copyright (C) 2011 - 2022 SCIFIO developers.
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

import java.awt.Color;
import java.text.DecimalFormat;

import javax.swing.JOptionPane;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

public class HDFWriter {

	private static final Color[] DEFAULT_CHANNEL_COLORS = new Color[] {
		new Color(75, 0, 130), Color.blue, Color.green, Color.yellow, Color.red,
		Color.pink, Color.orange, Color.magenta };
	private static final String VERSION = "7.6";
	private final int bitDepth_;
	private String acqDate_ = "2012-11-08 16:14:17.000";
	private final int numChannels_, numFrames_;
	private final int imageWidth_, imageHeight_, numSlices_;
	private final double pixelSize_, pixelSizeZ_;
	private long fileID_;
	private long timeInfoID_;
	private final DecimalFormat numberFormat_ = new DecimalFormat("#.###");
	private final ResolutionLevel[] resLevels_;
	private long[] resLevelIDs_;
	private final String path_;
	private TimePoint currentTimePoint_;
	private int timePointImageCount_ = 0;
	private final boolean compressImageData_;
	private final int slicesPerWrite_;
	private Color[] channelColors_;
	private boolean initialized_ = false;

	public HDFWriter(final String path, final int numChannels,
		final int numFrames, final int numSlices, final int bitDepth,
		final double pixelSize, final double pixelSizeZ,
		final Color[] channelColors, final int width, final int height,
		final ResolutionLevel[] resLevels)
	{
		compressImageData_ = true;
		path_ = path;
		numChannels_ = numChannels;
		numFrames_ = numFrames;
		numSlices_ = numSlices;
		pixelSize_ = pixelSize;
		pixelSizeZ_ = pixelSizeZ;
		if (channelColors == null) {
			channelColors_ = DEFAULT_CHANNEL_COLORS;
		}
		else {
			channelColors_ = channelColors;
		}
		bitDepth_ = bitDepth;

		imageWidth_ = width;
		imageHeight_ = height;
		resLevels_ = resLevels;
		slicesPerWrite_ = resLevels_[resLevels_.length - 1].getReductionFactorZ();
	}

	public void close() {
		try {
			// if canceled
			if (currentTimePoint_ != null) {
				currentTimePoint_.closeTimePoint();
			}

			H5.H5Gclose(timeInfoID_);
			for (final long id : resLevelIDs_) {
				H5.H5Gclose(id);
			}
			H5.H5Fclose(fileID_);
		}
		catch (final Exception e) {
			JOptionPane.showMessageDialog(null, "Couldn't close Imaris file");
			e.printStackTrace();
		}
	}

	// this function is not writing one image, but rather the minimum number of
	// slices needed to
	// write one image at the lowest resolution level
	public void writeImage(final PipelineImage img) throws Exception {
		if (!initialized_) {
			acqDate_ = img.dateAndtime;
			createFile();
			initialized_ = true;
		}
		// if new timepoint
		if (timePointImageCount_ == 0) {
			currentTimePoint_ =
				new TimePoint(resLevels_, resLevelIDs_, numChannels_, img.frame,
					bitDepth_, compressImageData_);
			HDFUtils.writeStringAttribute(timeInfoID_, "TimePoint" + (1 + img.frame),
				img.dateAndtime);
		}

		currentTimePoint_.writePixels(img);

		if (numSlices_ % slicesPerWrite_ != 0 &&
			img.slice + slicesPerWrite_ - 1 >= numSlices_)
		{
			// dont want to overcount extra slices that don't exist in original data
			timePointImageCount_ += numSlices_ % slicesPerWrite_;
		}
		else {
			timePointImageCount_ += slicesPerWrite_;
		}

		// close channels if full
		if (timePointImageCount_ == numChannels_ * numSlices_) {
			if (img.histograms == null) {
				JOptionPane.showMessageDialog(null, "histogram not created correctly");
				img.histograms = new long[resLevels_.length][256];
			}
			currentTimePoint_.closeTimePoint();
			currentTimePoint_ = null;
			timePointImageCount_ = 0;
		}
	}

	private void createFile() {
		try {
			fileID_ =
				H5.H5Fcreate(path_, (int) HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			addRootAttributes();
			makeDataSetInfo();
			makeDataSet();
		}
		catch (final Exception e) {
			JOptionPane.showMessageDialog(null, "Couldnt create imaris file");
			e.printStackTrace();
		}
	}

	private void addRootAttributes() throws HDF5LibraryException, HDF5Exception {
		HDFUtils.writeStringAttribute(fileID_, "DataSetDirectoryName", "DataSet");
		HDFUtils.writeStringAttribute(fileID_, "DataSetInfoDirectoryName",
			"DataSetInfo");
		HDFUtils.writeStringAttribute(fileID_, "ImarisDataSet", "ImarisDataSet");
		HDFUtils.writeStringAttribute(fileID_, "ImarisVersion", "5.5.0");
//      HDFUtils.writeStringAttribute(fileID_, "ThumbnailDirectoryName", "Thumbnail");
		// Create number of datasets attribute
		final long dataspaceID = H5.H5Screate_simple(1, new long[] { 1 }, null);
		final long attID =
			H5.H5Acreate(fileID_, "NumberOfDataSets",
				HDF5Constants.H5T_NATIVE_UINT32, dataspaceID,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		H5.H5Awrite(attID, HDF5Constants.H5T_NATIVE_UINT32,
			new byte[] { 1, 0, 0, 0 });
		// Close dataspace and attribute
		H5.H5Sclose(dataspaceID);
		H5.H5Aclose(attID);
	}

	private void makeDataSetInfo() throws NullPointerException,
		HDF5LibraryException, HDF5Exception
	{
		final long dataSetGroupID =
			H5.H5Gcreate(fileID_, "/DataSetInfo", HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		// Channels
		for (int c = 0; c < numChannels_; c++) {
			final long channelID =
				H5.H5Gcreate(dataSetGroupID, "Channel " + c, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			final float[] rgb =
				channelColors_[c % numChannels_].getRGBColorComponents(null);
			HDFUtils
				.writeStringAttribute(channelID, "Color", numberFormat_.format(rgb[0]) +
					" " + numberFormat_.format(rgb[1]) + " " +
					numberFormat_.format(rgb[2]));
			HDFUtils.writeStringAttribute(channelID, "ColorMode", "BaseColor");
			HDFUtils.writeStringAttribute(channelID, "ColorOpacity", "1.000");
			HDFUtils.writeStringAttribute(channelID, "ColorRange", "0 " +
				((int) Math.pow(2, bitDepth_) - 1));
			HDFUtils.writeStringAttribute(channelID, "Description",
				"(description not specified)");
			HDFUtils.writeStringAttribute(channelID, "GammaCorrection", "1.000");
			HDFUtils.writeStringAttribute(channelID, "Name", "(name not specified)");
			H5.H5Gclose(channelID);
		}

		// Image
		final long imageID =
			H5.H5Gcreate(dataSetGroupID, "Image", HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		HDFUtils.writeStringAttribute(imageID, "Description",
			"(description not specified)");
		HDFUtils.writeStringAttribute(imageID, "ExtMax0", numberFormat_
			.format(imageWidth_ * pixelSize_));
		HDFUtils.writeStringAttribute(imageID, "ExtMax1", numberFormat_
			.format(imageHeight_ * pixelSize_));
		HDFUtils.writeStringAttribute(imageID, "ExtMax2", numberFormat_
			.format(numSlices_ * pixelSizeZ_));
		HDFUtils.writeStringAttribute(imageID, "ExtMin0", "0");
		HDFUtils.writeStringAttribute(imageID, "ExtMin1", "0");
		HDFUtils.writeStringAttribute(imageID, "ExtMin2", "0");
		HDFUtils.writeStringAttribute(imageID, "Name", "(name not specified)");
		if (acqDate_ != null) {
			HDFUtils.writeStringAttribute(imageID, "RecordingDate", acqDate_);
		}
		HDFUtils.writeStringAttribute(imageID, "Unit", "um");
		HDFUtils.writeStringAttribute(imageID, "X", imageWidth_ + "");
		HDFUtils.writeStringAttribute(imageID, "Y", imageHeight_ + "");
		HDFUtils.writeStringAttribute(imageID, "Z", numSlices_ + "");
		H5.H5Gclose(imageID);

		// Imaris
		final long imarisID =
			H5.H5Gcreate(dataSetGroupID, "Imaris", HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		HDFUtils.writeStringAttribute(imarisID, "Version", VERSION);
		H5.H5Gclose(imarisID);

		// ImarisDataSet
		final long imarisDSID =
			H5.H5Gcreate(dataSetGroupID, "ImarisDataSet", HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		HDFUtils.writeStringAttribute(imarisDSID, "Creator", "Imaricumpiler");
		HDFUtils.writeStringAttribute(imarisDSID, "NumberOfImages", "1");
		HDFUtils.writeStringAttribute(imarisDSID, "Version", VERSION);
		H5.H5Gclose(imarisDSID);

		// Log
		final long logID =
			H5.H5Gcreate(dataSetGroupID, "Log", HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		HDFUtils.writeStringAttribute(logID, "Entries", "0");
		H5.H5Gclose(logID);

		// TimeInfo
		timeInfoID_ =
			H5.H5Gcreate(dataSetGroupID, "TimeInfo", HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		HDFUtils.writeStringAttribute(timeInfoID_, "DatasetTimePoints", numFrames_ +
			"");
		HDFUtils.writeStringAttribute(timeInfoID_, "FileTimePoints", numFrames_ +
			"");
		// close this at the end after all time points added

		H5.H5Gclose(dataSetGroupID);
	}

	private void makeDataSet() throws NullPointerException, HDF5LibraryException,
		HDF5Exception
	{
		resLevelIDs_ = new long[resLevels_.length];

		final long dataSetGroupID =
			H5.H5Gcreate(fileID_, "/DataSet", HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

		// Make resolution levels
		for (int level = 0; level < resLevels_.length; level++) {
			resLevelIDs_[level] =
				H5.H5Gcreate(dataSetGroupID, "ResolutionLevel " + level,
					HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
		}
		H5.H5Gclose(dataSetGroupID);
	}

}
