/*
 *  Copyright (C) 2020-2025 GReD
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
import Imaris.IDataSetPrx;
import Imaris.ILabelImagePrx;
import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.roi.GenericShapeWrapper;
import fr.igred.omero.roi.ROIWrapper;
import fr.igred.omero.roi.ShapeList;
import ome.model.units.Time;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.lang.invoke.MethodHandles;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static Imaris.tType.eTypeFloat;
import static Imaris.tType.eTypeUInt16;
import static Imaris.tType.eTypeUInt8;

public final class Image2Imaris {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());


	private Image2Imaris() {
	}


	public static void createImarisDataset(Client client, ImageWrapper image, IApplicationPrx app) {
		PixelsWrapper pix = image.getPixels();

		int sizeX = pix.getSizeX();
		int sizeY = pix.getSizeY();
		int sizeC = pix.getSizeC();
		int sizeZ = pix.getSizeZ();
		int sizeT = pix.getSizeT();

		String       pixType = pix.getPixelType();
		Imaris.tType type    = eTypeFloat;
		if ("uint8".equals(pixType)) {
			type = eTypeUInt8;
		} else if ("uint16".equals(pixType)) {
			type = eTypeUInt16;
		}

		try {
			IDataSetPrx dataset = app.GetFactory().CreateDataSet();
			dataset.Create(type, sizeX, sizeY, sizeZ, sizeC, sizeT);
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
		} catch (Error | AccessException | ExecutionException e) {
			LOGGER.warning(e.getMessage());
		}
	}


	private static void setDataSlice(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
	throws Error {
		Imaris.tType type = imarisDataset.GetType();
		if (type==eTypeUInt8) {
			setDataSliceBytes(pixels, imarisDataset, c, z, t);
		} else if (type==eTypeUInt16) {
			setDataSliceShorts(pixels, imarisDataset, c, z, t);
		} else if (type==eTypeFloat) {
			setDataSliceFloats(pixels, imarisDataset, c, z, t);
		}
	}


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


	private static void setChannels(Client client, ImageWrapper image, IDataSetPrx imarisDataset) {
		int       sizeC = image.getPixels().getSizeC();
		final int shift = 8;
		for (int c = 0; c < sizeC; c++) {
			try {
				int r    = image.getChannelColor(client, c).getRed();
				int g    = image.getChannelColor(client, c).getGreen();
				int b    = image.getChannelColor(client, c).getBlue();
				int a    = image.getChannelColor(client, c).getAlpha();
				int rgba = r + (g << shift) + (b << 2 * shift) + (a << 3 * shift);
				imarisDataset.SetChannelColorRGBA(c, rgba);
				imarisDataset.SetChannelName(c, image.getChannelName(client, c));
			} catch (AccessException | ServiceException | ExecutionException | Error e) {
				LOGGER.warning(e.getMessage());
			}
		}
	}


	private static void setSpacing(Client client, ImageWrapper image, IDataSetPrx imarisDataset) {
		PixelsWrapper pixels = image.getPixels();

		int sizeX = pixels.getSizeX();
		int sizeY = pixels.getSizeY();
		int sizeZ = pixels.getSizeZ();

		omero.model.Length pixSizeX = pixels.getPixelSizeX();
		omero.model.Length pixSizeY = pixels.getPixelSizeY();
		omero.model.Length pixSizeZ = pixels.getPixelSizeZ();

		Double spacingX = getLengthValue(pixSizeX);
		Double spacingY = getLengthValue(pixSizeY);
		Double spacingZ = getLengthValue(pixSizeZ);

		String unit = pixSizeX != null ? pixSizeX.getSymbol() : null;

		omero.model.Length posX = pixels.getPositionX();
		omero.model.Length posY = pixels.getPositionY();
		omero.model.Length posZ = pixels.getPositionZ();

		Double minX = getLengthValue(posX);
		Double minY = getLengthValue(posY);
		Double minZ = getLengthValue(posZ);

		Double maxX = minX != null && spacingX != null ? minX + spacingX * sizeX : null;
		Double maxY = minY != null && spacingY != null ? minY + spacingY * sizeY : null;
		Double maxZ = minZ != null && spacingZ != null ? minZ + spacingZ * sizeZ : null;

		try {
			pixels.loadPlanesInfo(client);
		} catch (AccessException | ExecutionException | ServiceException e) {
			LOGGER.warning(e.getMessage());
		}

		omero.model.Time interval = pixels.getMeanTimeInterval();
		Double delta;
		if (interval != null) {
			String tUnit = String.valueOf(interval.getUnit());
			Time t = new Time(pixels.getMeanTimeInterval().getValue(), tUnit);

			delta = Time.convertTime(t, "s").getValue();
		} else {
			delta = null;
		}

		try {
			if (minX != null) {
				imarisDataset.SetExtendMinX(minX.floatValue());
			}
			if (maxX != null) {
				imarisDataset.SetExtendMaxX(maxX.floatValue());
			}
			if (minY != null) {
				imarisDataset.SetExtendMinY(minY.floatValue());
			}
			if (maxY != null) {
				imarisDataset.SetExtendMaxY(maxY.floatValue());
			}
			if (minZ != null) {
				imarisDataset.SetExtendMinZ(minZ.floatValue());
			}
			if (maxZ != null) {
				imarisDataset.SetExtendMaxZ(maxZ.floatValue());
			}
			if (unit != null) {
				imarisDataset.SetUnit(unit);
			}
			Timestamp acqDate = image.getAcquisitionDate();
			if (acqDate != null) {
				String date = image.getAcquisitionDate().toString();
				imarisDataset.SetParameter("Image", "RecordingDate", date);
				imarisDataset.SetTimePoint(0, date);
			}
			// TODO: Fix time
			if (delta != null) {
				imarisDataset.SetTimePointsDelta(delta);
			}
		} catch (Error e) {
			LOGGER.warning(e.getMessage());
		}
	}


	private static Double getLengthValue(omero.model.Length length) {
		return length != null ? length.getValue() : null;
	}


	/**
	 * Loads the ROIs of the image into an Imaris label image.
	 *
	 * @param client The OMERO client.
	 * @param image  The OMERO image.
	 * @param app    The Imaris application proxy.
	 * @return The Imaris label image containing the ROIs.
	 * @throws AccessException    If there is an access error.
	 * @throws ServiceException   If there is a service error.
	 * @throws ExecutionException If there is an execution error.
	 * @throws Error              If there is an Imaris error.
	 */
	public static ILabelImagePrx loadROIs(Client client, ImageWrapper image, IApplicationPrx app)
	throws AccessException, ServiceException, ExecutionException, Error {
		int sizeX = image.getPixels().getSizeX();
		int sizeY = image.getPixels().getSizeY();
		int sizeZ = image.getPixels().getSizeZ();
		int sizeT = image.getPixels().getSizeT();

		List<ROIWrapper> rois = image.getROIs(client);

		ILabelImagePrx labelImage = app.GetFactory().CreateLabelImage();
		labelImage.Create(sizeX, sizeY, sizeZ, sizeT);

		for (int index = 0; index < rois.size(); index++) {
			ROIWrapper roi    = rois.get(index);
			ShapeList  shapes = roi.getShapes();
			for (GenericShapeWrapper<?> shape : shapes) {
				int z = shape.getZ();
				int t = shape.getT();

				Shape awtShape = shape.createTransformedAWTShape();

				Rectangle2D bounds = awtShape.getBounds2D();

				int x = (int) bounds.getX();
				int y = (int) bounds.getY();
				int w = (int) bounds.getWidth();
				int h = (int) bounds.getHeight();

				int[] labels = labelImage.GetDataSubVolumeAs1DArrayInts(x, y, z, t, w, h, 1);

				for (int j = 0; j < h; j++) {
					for (int i = 0; i < w; i++) {
						if (awtShape.contains(i + x, j + y)) {
							int pos = j * w + i;
							labels[pos] = index + 1;
						}
					}
				}
				labelImage.SetDataSubVolumeAs1DArrayInts(labels, x, y, z, t, w, h, 1);
			}
		}
		return labelImage;
	}

}
