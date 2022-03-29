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

/**
 * @author Henry
 */
public class ResolutionLevelMaker {

	private static final int BYTES_PER_MB = 1024 * 1024;

	public static ResolutionLevel[] calcLevels(final int imageSizeX,
		final int imageSizeY, final int imageSizeZ, final int numTimePoints,
		final int byteDepth)
	{
		final LinkedList<ResolutionLevel> resLevels =
			new LinkedList<ResolutionLevel>();
		addResLevels(resLevels, imageSizeX, imageSizeY, imageSizeZ, imageSizeX,
			imageSizeY, imageSizeZ, numTimePoints, byteDepth);
		final ResolutionLevel[] array = new ResolutionLevel[resLevels.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = resLevels.get(i);
		}
		return array;
	}

	// Make res level 0 and add more if needed
	private static void addResLevels(final LinkedList<ResolutionLevel> resLevels,
		final int baseSizeX, final int baseSizeY, final int baseSizeZ,
		final int imageSizeX, final int imageSizeY, final int imageSizeZ,
		final int numTimePoints, final int byteDepth)
	{

		resLevels.add(new ResolutionLevel(resLevels.size(), baseSizeX, baseSizeY,
			baseSizeZ, imageSizeX, imageSizeY, imageSizeZ, numTimePoints, byteDepth));

		if (resLevels.getLast().getImageNumBytes() > 4 * BYTES_PER_MB) {
			int newX = imageSizeX, newY = imageSizeY, newZ = imageSizeZ;
			final boolean reduceZ =
				(10 * imageSizeZ) * (10 * imageSizeZ) > imageSizeX * imageSizeY;
			final boolean reduceY =
				(10 * imageSizeY) * (10 * imageSizeY) > imageSizeX * imageSizeZ;
			final boolean reduceX =
				(10 * imageSizeX) * (10 * imageSizeX) > imageSizeY * imageSizeZ;
			if (reduceZ) {
				newZ = (int) Math.ceil(imageSizeZ / 2.0);
			}
			if (reduceX) {
				newX = (int) Math.ceil(imageSizeX / 2.0);
			}
			if (reduceY) {
				newY = (int) Math.ceil(imageSizeY / 2.0);
			}
			addResLevels(resLevels, baseSizeX, baseSizeY, baseSizeZ, newX, newY,
				newZ, numTimePoints, byteDepth);
		}
	}

}
