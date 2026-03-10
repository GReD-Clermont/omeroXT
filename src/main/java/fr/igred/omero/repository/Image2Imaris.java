/*
 *  Copyright (C) 2020-2026 GReD
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package fr.igred.omero.repository;

import Imaris.Error;
import Imaris.IApplicationPrx;
import Imaris.IBaseImagePrx;
import Imaris.IDataSetPrx;
import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import ome.model.units.Time;
import omero.model._TimeOperationsNC;

import java.awt.Color;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static Imaris.tType.eTypeFloat;
import static Imaris.tType.eTypeUInt16;
import static Imaris.tType.eTypeUInt8;


/**
 * Utility class for creating Imaris datasets from OMERO images.
 */
public final class Image2Imaris {

	/** Logger for logging messages and errors. */
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());


	/**
	 * Private constructor to prevent instantiation.
	 */
	private Image2Imaris() {
	}


	/**
	 * Creates an Imaris dataset from an OMERO image and sets it in the Imaris application.
	 *
	 * @param client The OMERO client.
	 * @param image  The OMERO image.
	 * @param app    The Imaris application proxy.
	 */
	public static IDataSetPrx createImarisDataset(Client client, ImageWrapper image, IApplicationPrx app)
	throws Error, ExecutionException, AccessException {
		PixelsWrapper pix = image.getPixels();

		int sizeX = pix.getSizeX();
		int sizeY = pix.getSizeY();
		int sizeC = pix.getSizeC();
		int sizeZ = pix.getSizeZ();
		int sizeT = pix.getSizeT();

		String pixType = pix.getPixelType();

		Imaris.tType type;
		if ("uint8".equals(pixType)) {
			type = eTypeUInt8;
		} else if ("uint16".equals(pixType)) {
			type = eTypeUInt16;
		} else {
			type = eTypeFloat;
		}

		IDataSetPrx dataset = app.GetFactory().CreateDataSet();
		dataset.Create(type, sizeX, sizeY, sizeZ, sizeC, sizeT);
		dataset.SetParameter("Image", "Name", image.getName());

		setChannels(client, image, dataset);
		setSpacing(client, image, dataset);

		boolean created = pix.createRawDataFacility(client);
		for (int t = 0; t < sizeT; t++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int c = 0; c < sizeC; c++) {
					PixelsWrapper.Coordinates pos = new PixelsWrapper.Coordinates(0, 0, c, z, t);

					double[][] pixels = pix.getTile(client, pos, sizeX, sizeY);
					setDataSlice(pixels, dataset, c, z, t);
				}
			}
		}
		if (created) {
			pix.destroyRawDataFacility();
		}

		app.SetDataSet(dataset);
		app.GetSurpassCamera().Fit();
		return dataset;
	}


	/**
	 * Sets a data slice in the Imaris dataset based on the pixel type.
	 *
	 * @param pixels        2D array of pixel values.
	 * @param imarisDataset The Imaris dataset.
	 * @param c             Channel index.
	 * @param z             Z-slice index.
	 * @param t             Time point index.
	 *
	 * @throws Error If there is an Imaris error.
	 */
	private static void setDataSlice(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
	throws Error {
		Imaris.tType type = imarisDataset.GetType();
		if (type == eTypeUInt8) {
			setDataSliceBytes(pixels, imarisDataset, c, z, t);
		} else if (type == eTypeUInt16) {
			setDataSliceShorts(pixels, imarisDataset, c, z, t);
		} else if (type == eTypeFloat) {
			setDataSliceFloats(pixels, imarisDataset, c, z, t);
		}
	}


	/**
	 * Sets a data slice in the Imaris dataset using byte values.
	 *
	 * @param pixels        2D array of pixel values.
	 * @param imarisDataset The Imaris dataset.
	 * @param c             Channel index.
	 * @param z             Z-slice index.
	 * @param t             Time point index.
	 *
	 * @throws Error If there is an Imaris error.
	 */
	private static void setDataSliceBytes(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
	throws Error {
		int      sizeY = pixels.length;
		int      sizeX = sizeY > 0 ? pixels[0].length : 0;
		byte[][] bytes = new byte[sizeX][sizeY];
		for (int y = 0; y < sizeY; y++) {
			for (int x = 0; x < sizeX; x++) {
				bytes[x][y] = (byte) pixels[y][x];
			}
		}
		imarisDataset.SetDataSliceBytes(bytes, z, c, t);
	}


	/**
	 * Sets a data slice in the Imaris dataset using short values.
	 *
	 * @param pixels        2D array of pixel values.
	 * @param imarisDataset The Imaris dataset.
	 * @param c             Channel index.
	 * @param z             Z-slice index.
	 * @param t             Time point index.
	 *
	 * @throws Error If there is an Imaris error.
	 */
	private static void setDataSliceShorts(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
	throws Error {
		int       sizeY  = pixels.length;
		int       sizeX  = sizeY > 0 ? pixels[0].length : 0;
		short[][] shorts = new short[sizeX][sizeY];
		for (int y = 0; y < sizeY; y++) {
			for (int x = 0; x < sizeX; x++) {
				shorts[x][y] = (short) pixels[y][x];
			}
		}
		imarisDataset.SetDataSliceShorts(shorts, z, c, t);
	}


	/**
	 * Sets a data slice in the Imaris dataset with float pixel values.
	 *
	 * @param pixels        The 2D array of pixel values.
	 * @param imarisDataset The Imaris dataset.
	 * @param c             The channel index.
	 * @param z             The Z index.
	 * @param t             The time index.
	 *
	 * @throws Error If there is an Imaris error.
	 */
	private static void setDataSliceFloats(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
	throws Error {
		int       sizeY  = pixels.length;
		int       sizeX  = sizeY > 0 ? pixels[0].length : 0;
		float[][] floats = new float[sizeX][sizeY];
		for (int y = 0; y < sizeY; y++) {
			for (int x = 0; x < sizeX; x++) {
				floats[x][y] = (float) pixels[y][x];
			}
		}
		imarisDataset.SetDataSliceFloats(floats, z, c, t);
	}


	/**
	 * Sets the channel colors and names of the Imaris dataset based on the OMERO image metadata.
	 *
	 * @param client        The OMERO client.
	 * @param image         The OMERO image.
	 * @param imarisDataset The Imaris dataset.
	 */
	private static void setChannels(Client client, ImageWrapper image, IDataSetPrx imarisDataset) {
		int sizeC = image.getPixels().getSizeC();
		int shift = 8;
		for (int c = 0; c < sizeC; c++) {
			try {
				Color color = image.getChannelColor(client, c);
				if (color != null) {
					int r    = color.getRed();
					int g    = color.getGreen();
					int b    = color.getBlue();
					int a    = color.getAlpha();
					int rgba = r + (g << shift) + (b << 2 * shift) + (a << 3 * shift);
					imarisDataset.SetChannelColorRGBA(c, rgba);
				}
				String channelName = image.getChannelName(client, c);
				if (channelName != null) {
					imarisDataset.SetChannelName(c, channelName);
				}
			} catch (AccessException | ServiceException | ExecutionException | Error e) {
				LOGGER.warning(e.getMessage());
			}
		}
	}


	/**
	 * Sets the spatial and temporal spacing of the Imaris image based on the OMERO image metadata.
	 *
	 * @param client      The OMERO client.
	 * @param image       The OMERO image.
	 * @param imarisImage The Imaris image.
	 */
	public static void setSpacing(Client client, ImageWrapper image, IBaseImagePrx imarisImage) {
		PixelsWrapper pixels = image.getPixels();

		int sizeX = pixels.getSizeX();
		int sizeY = pixels.getSizeY();
		int sizeZ = pixels.getSizeZ();

		omero.model.Length pixSizeX = pixels.getPixelSizeX();
		omero.model.Length pixSizeY = pixels.getPixelSizeY();
		omero.model.Length pixSizeZ = pixels.getPixelSizeZ();

		Float spacingX = getLengthValue(pixSizeX);
		Float spacingY = getLengthValue(pixSizeY);
		Float spacingZ = getLengthValue(pixSizeZ);

		String unit = pixSizeX != null ? pixSizeX.getSymbol() : null;

		omero.model.Length posX = pixels.getPositionX();
		omero.model.Length posY = pixels.getPositionY();
		omero.model.Length posZ = pixels.getPositionZ();

		Float minX = getLengthValue(posX);
		Float minY = getLengthValue(posY);
		Float minZ = getLengthValue(posZ);

		Float maxX = minX != null && spacingX != null ? minX + spacingX * sizeX : null;
		Float maxY = minY != null && spacingY != null ? minY + spacingY * sizeY : null;
		Float maxZ = minZ != null && spacingZ != null ? minZ + spacingZ * sizeZ : null;

		try {
			pixels.loadPlanesInfo(client);
		} catch (AccessException | ExecutionException | ServiceException e) {
			LOGGER.warning(e.getMessage());
		}

		Float delta = getTimeValueInSeconds(pixels.getMeanTimeInterval());

		Timestamp acqDate = image.getAcquisitionDate();
		String    date    = acqDate != null ? acqDate.toString() : null;

		setIfNotNull(imarisImage, IBaseImagePrx::SetExtendMinX, minX);
		setIfNotNull(imarisImage, IBaseImagePrx::SetExtendMaxX, maxX);
		setIfNotNull(imarisImage, IBaseImagePrx::SetExtendMinY, minY);
		setIfNotNull(imarisImage, IBaseImagePrx::SetExtendMaxY, maxY);
		setIfNotNull(imarisImage, IBaseImagePrx::SetExtendMinZ, minZ);
		setIfNotNull(imarisImage, IBaseImagePrx::SetExtendMaxZ, maxZ);
		setIfNotNull(imarisImage, IBaseImagePrx::SetUnit, unit);
		setIfNotNull(imarisImage, Image2Imaris::setAcquisitionDate, date);
		// TODO: Fix time
		setIfNotNull(imarisImage, IBaseImagePrx::SetTimePointsDelta, delta);
	}


	/**
	 * Sets the acquisition date on the Imaris image.
	 *
	 * @param imarisImage The Imaris image.
	 * @param date        The acquisition date as a String.
	 *
	 * @throws Error If there is an Imaris error.
	 */
	private static void setAcquisitionDate(IBaseImagePrx imarisImage, String date)
	throws Error {
		imarisImage.SetParameter("Image", "RecordingDate", date);
		imarisImage.SetTimePoint(0, date);
	}


	/**
	 * Converts an OMERO length value to a Float.
	 *
	 * @param length The OMERO length value.
	 *
	 * @return The length value as a Float, or null if the input is null.
	 */
	private static Float getLengthValue(omero.model.Length length) {
		return length != null ? (float) length.getValue() : null;
	}


	/**
	 * Converts an OMERO time value to seconds.
	 *
	 * @param time The OMERO time value.
	 *
	 * @return The time value in seconds, or null if the input is null.
	 */
	private static Float getTimeValueInSeconds(_TimeOperationsNC time) {
		if (time != null) {
			String tUnit = String.valueOf(time.getUnit());
			Time   t     = new Time(time.getValue(), tUnit);

			return (float) Time.convertTime(t, "s").getValue();
		} else {
			return null;
		}
	}


	/**
	 * Sets a value on the Imaris image if the value is not null.
	 *
	 * @param imarisImage The Imaris image.
	 * @param setter      The setter method reference.
	 * @param value       The value to set.
	 * @param <T>         The type of the value.
	 */
	private static <T> void setIfNotNull(IBaseImagePrx imarisImage,
	                                     Setter<? super IBaseImagePrx, ? super T> setter,
	                                     T value) {
		if (value != null) {
			try {
				setter.accept(imarisImage, value);
			} catch (Error e) {
				LOGGER.warning(e.getMessage() + ": " + e.mDescription);
			}
		}
	}


	/**
	 * Functional interface for setting a value on an object.
	 *
	 * @param <T> The type of the object.
	 * @param <U> The type of the value to set.
	 */
	@FunctionalInterface
	private interface Setter<T, U> {

		/**
		 * Sets a value on the given object.
		 *
		 * @param t The object to set the value on.
		 * @param u The value to set.
		 *
		 * @throws Error If there is an Imaris error.
		 */
		void accept(T t, U u) throws Error;

	}

}
