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

import java.util.LinkedList;
import java.util.TreeMap;

public class HDFPreprocessor {

	private final int batchSize_;
	private final ResolutionLevel[] resLevels_;
	private final TreeMap<Integer, long[][]> histograms_;
	private final int bitDepth_;
	private final int width_, height_;

	public HDFPreprocessor(final int width, final int height, final int bitDepth,
		final ResolutionLevel[] resLevels)
	{
		bitDepth_ = bitDepth;
		resLevels_ = resLevels;
		batchSize_ = resLevels_[resLevels_.length - 1].getReductionFactorZ();
		width_ = width;
		height_ = height;
		histograms_ = new TreeMap<Integer, long[][]>();
	}

	// slice index of first in batch
	public PipelineImage process(final LinkedList<PipelineImage> slices) {

		if (slices.getFirst().slice == 0) {
			histograms_.put(slices.getFirst().channel,
				new long[resLevels_.length][256]);
		}

		// Images is a list of slices with a size corresponding to the minumum
		// number of slices
		// needed to write one slice of the lowest resolution level
		final int numSlicesInChunk = slices.size();

		// Calculate downsampled resolutions
		// These are arrays of pixels used in downsampling, organized by resolution
		// level index and slice index
		final Object[][] downsampledPixSum =
			new Object[resLevels_.length][numSlicesInChunk];
		final Object[][] pixelsToWrite =
			new Object[resLevels_.length][numSlicesInChunk];
		// copy over pixels for highest resolution
		for (int i = 0; i < numSlicesInChunk; i++) {
			pixelsToWrite[0][i] = slices.get(i).pixels;
			if (pixelsToWrite[0][i] == null) {
				break;
				// only occurs if incomplete set of slices gets sent to fill out a frame
				// (dummy images)
			}
			if (bitDepth_ > 8) {
				for (final short s : (short[]) slices.get(i).pixels) {
					histograms_.get(slices.getFirst().channel)[0][(int) (255 * ((s & 0xffff) / Math
						.pow(2, bitDepth_)))]++;
				}
			}
			else {
				for (final byte b : (byte[]) slices.get(i).pixels) {
					histograms_.get(slices.getFirst().channel)[0][b & 0xff]++;
				}
			}
		}
		// calculate and add pixels for lower resolutions
		for (int resLevel = 1; resLevel < resLevels_.length; resLevel++) {
			for (int i = 0; i < numSlicesInChunk; i++) {
				if (i % resLevels_[resLevel].getReductionFactorZ() == 0) {
					// only create arrays when the slice index is a multiple of the
					// resolution level's z downsample factor
					// these arrays are used to sum up all appropriate pixels values and
					// then average them into
					// the new value at a lower resolution
					downsampledPixSum[resLevel][i] =
						new long[resLevels_[resLevel].getImageSizeX() *
							resLevels_[resLevel].getImageSizeY()];
					if (bitDepth_ > 8) {
						pixelsToWrite[resLevel][i] =
							new short[resLevels_[resLevel].getImageSizeX() *
								resLevels_[resLevel].getImageSizeY()];
					}
					else {
						pixelsToWrite[resLevel][i] =
							new byte[resLevels_[resLevel].getImageSizeX() *
								resLevels_[resLevel].getImageSizeY()];
					}
				}
			}
		}

		// This block sums up all pixel values from higher resolutions needed to
		// create average values at lower
		// resolutions and then averages them
		final int res0Width = width_;
		final int numPixelsPerSlice = res0Width * height_;
		for (int sliceIndex = 0; sliceIndex < numSlicesInChunk; sliceIndex++) {
			for (int i = 0; i < numPixelsPerSlice; i++) {
				final int x = i % res0Width;
				final int y = i / res0Width;
				for (int resLevel = 1; resLevel < resLevels_.length; resLevel++) {
					final int resLevelSizeX = resLevels_[resLevel].getImageSizeX();
					final int resLevelSizeY = resLevels_[resLevel].getImageSizeY();
					final int zDSFactor = resLevels_[resLevel].getReductionFactorZ();
					final int xDSFactor = resLevels_[resLevel].getReductionFactorX();
					final int yDSFactor = resLevels_[resLevel].getReductionFactorY();
					// dsX and dsY are the x and y coordinates of the pixel in the
					// downsampled image
					final int dsX = x / xDSFactor;
					final int dsY = y / yDSFactor;
					if (dsX >= resLevelSizeX || dsY >= resLevelSizeY) {
						// these pixels are cropped off at this resolution level, so skip
						// them
						continue;
					}

					// downsampled slice index is
					final int downsampledSliceIndex =
						sliceIndex - (sliceIndex % zDSFactor);
					int val;
					if (slices.get(sliceIndex).pixels == null) {
						val = 0;
						// this should only occur in the situation in which the a blank
						// slice has to be passed
						// to fill out the end of stack that has been downsampled in z. Use
						// the first slice in the
						// slice group (which must exist) to calculate the summed value.
						// This way, the bottom slice
						// in lower resolutions is not half as bright as others
						if (bitDepth_ > 8) {
							val = (((short[]) slices.get(0).pixels)[i] & 0xffff);
						}
						else {
							val = (((byte[]) slices.get(0).pixels)[i] & 0xff);
						}
					}
					else if (bitDepth_ > 8) {
						if (slices.get(sliceIndex) == null) {
							System.out.println("null slice index");
						}
						else if (slices.get(sliceIndex) == null) {
							System.out.println("null pix");
						}
						val = (((short[]) slices.get(sliceIndex).pixels)[i] & 0xffff);
						histograms_.get(slices.getFirst().channel)[resLevel][(int) (255 * (val / Math
							.pow(2, bitDepth_)))]++;
					}
					else {
						val = (((byte[]) slices.get(sliceIndex).pixels)[i] & 0xff);
						// add pixel value to histogram
						histograms_.get(slices.getFirst().channel)[resLevel][val]++;
					}
					((long[]) downsampledPixSum[resLevel][downsampledSliceIndex])[dsY *
						resLevelSizeX + dsX] += val;

					if (x % xDSFactor == xDSFactor - 1 &&
						y % yDSFactor == yDSFactor - 1 &&
						sliceIndex % zDSFactor == zDSFactor - 1)
					{
						// all pixels for reslevel have been filled
						final int pixelIndex =
							(x / xDSFactor) + (y / yDSFactor) * resLevelSizeX;
						// calculate average pixel value
						if (bitDepth_ > 8) {
							((short[]) pixelsToWrite[resLevel][downsampledSliceIndex])[pixelIndex] =
								(short) (((long[]) downsampledPixSum[resLevel][downsampledSliceIndex])[pixelIndex] / (xDSFactor *
									yDSFactor * zDSFactor));
						}
						else {
							((byte[]) pixelsToWrite[resLevel][downsampledSliceIndex])[pixelIndex] =
								(byte) (((long[]) downsampledPixSum[resLevel][downsampledSliceIndex])[pixelIndex] / (xDSFactor *
									yDSFactor * zDSFactor));
						}
					}
				}
			}
		}

		final PipelineImage img =
			new PipelineImage(pixelsToWrite, slices.getFirst().channel, slices
				.getFirst().slice, slices.getFirst().frame,
				slices.getFirst().dateAndtime);

		// If this is the last slice in the frame, histograms are finished, so send
		// them for writing
		if (slices.getFirst().slice + batchSize_ >= resLevels_[0].getImageSizeZ()) {
			img.histograms = histograms_.get(slices.getFirst().channel);
			histograms_.put(slices.getFirst().channel, null);
		}
		return img;
	}
}
