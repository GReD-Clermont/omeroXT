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

package fr.igred.omero.roi;

import Imaris.Error;
import Imaris.IApplicationPrx;
import Imaris.ILabelImagePrx;
import Imaris.ISpotsPrx;
import Imaris.ISurfacesPrx;
import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.ImageWrapper;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static fr.igred.omero.repository.Image2Imaris.setSpacing;
import static java.util.Comparator.comparingInt;


/**
 * Utility class for creating Imaris datasets from OMERO images.
 */
public final class ROI2Imaris {


	/**
	 * Private constructor to prevent instantiation.
	 */
	private ROI2Imaris() {
	}


	/**
	 * Clamps a value between 0 and a maximum.
	 *
	 * @param value The value to clamp.
	 * @param max   The maximum value.
	 *
	 * @return The clamped value.
	 */
	private static int clamp(int value, int max) {
		return Math.max(0, Math.min(max, value));
	}


	/**
	 * Sets the pixels of a shape in a label array.
	 *
	 * @param awtShape The AWT shape.
	 * @param value    The label value.
	 * @param x        The x position.
	 * @param y        The y position.
	 * @param w        The width.
	 * @param h        The height.
	 * @param labels   The label array.
	 */
	private static void setShapePixels(java.awt.Shape awtShape, int value, int x, int y, int w, int h, int[] labels) {
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				if (awtShape.contains(i + x, j + y)) {
					int pos = j * w + i;
					labels[pos] = value + 1;
				}
			}
		}
	}


	/**
	 * Converts the PointWrappers from an image to an Imaris Spots object.
	 *
	 * @param client The OMERO client.
	 * @param image  The OMERO image.
	 * @param app    The Imaris application proxy.
	 *
	 * @return The Imaris Spots object.
	 *
	 * @throws AccessException    If there is an access error.
	 * @throws ServiceException   If there is a service error.
	 * @throws ExecutionException If there is an execution error.
	 * @throws Error              If there is an Imaris error.
	 */
	public static ISpotsPrx pointsToSpots(Client client, ImageWrapper image, IApplicationPrx app)
	throws Error, AccessException, ServiceException, ExecutionException {
		List<ROIWrapper> rois = image.getROIs(client);

		Map<Long, List<PointWrapper>> points = rois.stream()
		                                           .collect(Collectors.toMap(ROIWrapper::getId,
		                                                                     r -> r.getShapes()
		                                                                           .stream()
		                                                                           .filter(s -> s instanceof PointWrapper)
		                                                                           .map(s -> (PointWrapper) s)
		                                                                           .collect(Collectors.toList())));

		int numPoints = points.values()
		                      .stream()
		                      .mapToInt(List::size)
		                      .sum();

		float[][] positions = new float[numPoints][3];
		int[]     times     = new int[numPoints];
		float[]   radii     = new float[numPoints];

		int index = 0;
		for (Map.Entry<Long, List<PointWrapper>> entry : points.entrySet()) {
			List<PointWrapper> sortedPoints = entry.getValue()
			                                       .stream()
			                                       .sorted(comparingInt(PointWrapper::getT))
			                                       .collect(Collectors.toList());
			for (PointWrapper point : sortedPoints) {
				positions[index][0] = (float) point.getX();
				positions[index][1] = (float) point.getY();
				positions[index][2] = point.getZ();
				times[index] = point.getT();
				radii[index] = 1.0f;
				index++;
			}
		}

		//TODO: handle tracking

		ISpotsPrx spots = app.GetFactory().CreateSpots();
		spots.Set(positions, times, radii);
		return spots;
	}


	/**
	 * Converts the ROIs from an image to an Imaris Surfaces object.
	 *
	 * @param client The OMERO client.
	 * @param image  The OMERO image.
	 * @param app    The Imaris application proxy.
	 *
	 * @return The Imaris Surfaces object.
	 *
	 * @throws AccessException    If there is an access error.
	 * @throws ServiceException   If there is a service error.
	 * @throws ExecutionException If there is an execution error.
	 * @throws Error              If there is an Imaris error.
	 */
	public static ISurfacesPrx roisToSurfaces(Client client, ImageWrapper image, IApplicationPrx app)
	throws Error, AccessException, ServiceException, ExecutionException {
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
			shapes.removeIf(s -> s instanceof PointWrapper);
			shapes.removeIf(s -> s instanceof TextWrapper);
			for (GenericShapeWrapper<?> shape : shapes) {
				int zpos = shape.getZ();
				int tpos = shape.getT();

				int zmin = Math.max(zpos, 0);
				int zmax = zpos >= 0 ? zpos : sizeZ - 1;

				int tmin = Math.max(tpos, 0);
				int tmax = tpos >= 0 ? tpos : sizeT - 1;

				Shape awtShape = shape.createTransformedAWTShape();

				Rectangle2D bounds = awtShape.getBounds2D();

				int x = clamp((int) bounds.getX(), sizeX - 1);
				int y = clamp((int) bounds.getY(), sizeY - 1);
				int w = clamp((int) bounds.getWidth(), sizeX - x - 1);
				int h = clamp((int) bounds.getHeight(), sizeY - y - 1);

				for (int t = tmin; t <= tmax; t++) {
					for (int z = zmin; z <= zmax; z++) {
						int[] labels = labelImage.GetDataSubVolumeAs1DArrayInts(x, y, z, t, w, h, 1);
						setShapePixels(awtShape, index, x, y, w, h, labels);
						labelImage.SetDataSubVolumeAs1DArrayInts(labels, x, y, z, t, w, h, 1);
					}
				}
			}
		}
		//TODO: handle tracking
		return app.GetImageProcessing().DetectSurfacesFromLabelImage(labelImage);
	}


	/**
	 * Loads the ROIs of the image into an Imaris Surfaces object.
	 *
	 * @param client The OMERO client.
	 * @param image  The OMERO image.
	 * @param app    The Imaris application proxy.
	 *
	 * @throws AccessException    If there is an access error.
	 * @throws ServiceException   If there is a service error.
	 * @throws ExecutionException If there is an execution error.
	 * @throws Error              If there is an Imaris error.
	 */
	public static void loadROIs(Client client, ImageWrapper image, IApplicationPrx app)
	throws AccessException, ServiceException, ExecutionException, Error {
		ISurfacesPrx surfaces = roisToSurfaces(client, image, app);
		app.GetSurpassScene().AddChild(surfaces, -1);

		ISpotsPrx spots = pointsToSpots(client, image, app);
		app.GetSurpassScene().AddChild(spots, -1);
	}

}
