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
import Imaris.ILabelImagePrx;
import Imaris.ISurfacesPrx;
import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.roi.GenericShapeWrapper;
import fr.igred.omero.roi.ROIWrapper;
import fr.igred.omero.roi.ShapeList;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static fr.igred.omero.repository.Image2Imaris.setSpacing;


/**
 * Utility class for creating Imaris datasets from OMERO images.
 */
public final class ROI2Imaris {

	/** Logger for logging messages and errors. */
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());


	/**
	 * Private constructor to prevent instantiation.
	 */
	private ROI2Imaris() {
	}


	/**
	 * Loads the ROIs of the image into an Imaris Surfaces object.
	 *
	 * @param client The OMERO client.
	 * @param image  The OMERO image.
	 * @param app    The Imaris application proxy.
	 *
	 * @return The Imaris Surfaces corresponding to the ROIs.
	 *
	 * @throws AccessException    If there is an access error.
	 * @throws ServiceException   If there is a service error.
	 * @throws ExecutionException If there is an execution error.
	 * @throws Error              If there is an Imaris error.
	 */
	public static ISurfacesPrx loadROIs(Client client, ImageWrapper image, IApplicationPrx app)
	throws AccessException, ServiceException, ExecutionException, Error {
		int sizeX = image.getPixels().getSizeX();
		int sizeY = image.getPixels().getSizeY();
		int sizeZ = image.getPixels().getSizeZ();
		int sizeT = image.getPixels().getSizeT();

		List<ROIWrapper> rois = image.getROIs(client);

		ILabelImagePrx labelImage = app.GetFactory().CreateLabelImage();
		labelImage.Create(sizeX, sizeY, sizeZ, sizeT);
		setSpacing(client, image, labelImage);

		for (int index = 0; index < rois.size(); index++) {
			ROIWrapper roi    = rois.get(index);
			ShapeList  shapes = roi.getShapes();
			for (GenericShapeWrapper<?> shape : shapes) {
				int zpos = shape.getZ();
				int tpos = shape.getT();

				int zmin = Math.max(zpos, 0);
				int zmax = zpos >= 0 ? zpos : sizeZ - 1;

				int tmin = Math.max(tpos, 0);
				int tmax = tpos >= 0 ? tpos : sizeT - 1;

				Shape awtShape = shape.createTransformedAWTShape();

				Rectangle2D bounds = awtShape.getBounds2D();

				int bx = (int) bounds.getX();
				int by = (int) bounds.getY();
				int bw = (int) bounds.getWidth();
				int bh = (int) bounds.getHeight();

				int x = bx < 0 ? 0 : bx >= sizeX ? sizeX - 1 : bx;
				int y = by < 0 ? 0 : by >= sizeY ? sizeY - 1 : by;
				int w = bw < 0 ? 0 : x + bw >= sizeX ? sizeX - x - 1 : bw;
				int h = bh < 0 ? 0 : y + bh >= sizeY ? sizeY - y - 1 : bh;

				for(int t=tmin; t<=tmax; t++) {
					for(int z=zmin; z<=zmax; z++) {
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
			}
		}

		ISurfacesPrx surfaces = app.GetImageProcessing().DetectSurfacesFromLabelImage(labelImage);
		app.GetSurpassScene().AddChild(surfaces, -1);
		return surfaces;
	}

}
