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
import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import ome.model.units.Time;
import omero.model._PlaneInfoOperationsNC;
import omero.model.enums.UnitsTime;

import java.lang.invoke.MethodHandles;
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

        String pixType = pix.getPixelType();
        Imaris.tType type = eTypeFloat;
        if ("uint8".equals(pixType)) type = eTypeUInt8;
        else if ("uint16".equals(pixType)) type = eTypeUInt16;

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
            if (created) pix.destroyRawDataFacility();
            app.SetDataSet(dataset);
        } catch (Error | AccessException | ExecutionException e) {
            LOGGER.warning(e.getMessage());
        }
    }


    private static void setDataSlice(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t) throws Error {
        Imaris.tType type = imarisDataset.GetType();
        if (type == eTypeUInt8) setDataSliceBytes(pixels, imarisDataset, c, z, t);
        else if (type == eTypeUInt16) setDataSliceShorts(pixels, imarisDataset, c, z, t);
        else if (type == eTypeFloat) setDataSliceFloats(pixels, imarisDataset, c, z, t);
    }


    private static void setDataSliceBytes(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
    throws Error {
        int sizeY = pixels.length;
        int sizeX = sizeY > 0 ? pixels[0].length : 0;
        byte[][] bytes = new byte[sizeY][sizeX];
        for (int y = 0; y < sizeY; y++)
            for (int x = 0; x < sizeX; x++)
                bytes[y][x] = (byte) pixels[y][x];
        imarisDataset.SetDataSliceBytes(bytes, z, c, t);
    }


    private static void setDataSliceShorts(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
    throws Error {
        int sizeY = pixels.length;
        int sizeX = sizeY > 0 ? pixels[0].length : 0;
        short[][] shorts = new short[sizeY][sizeX];
        for (int y = 0; y < sizeY; y++)
            for (int x = 0; x < sizeX; x++)
                shorts[y][x] = (short) pixels[y][x];
        imarisDataset.SetDataSliceShorts(shorts, z, c, t);
    }


    private static void setDataSliceFloats(double[][] pixels, IDataSetPrx imarisDataset, int c, int z, int t)
    throws Error {
        int sizeY = pixels.length;
        int sizeX = sizeY > 0 ? pixels[0].length : 0;
        float[][] floats = new float[sizeY][sizeX];
        for (int y = 0; y < sizeY; y++)
            for (int x = 0; x < sizeX; x++)
                floats[y][x] = (float) pixels[y][x];
        imarisDataset.SetDataSliceFloats(floats, z, c, t);
    }


    private static void setChannels(Client client, ImageWrapper image, IDataSetPrx imarisDataset) {
        int sizeC = image.getPixels().getSizeC();
        final int shift = 8;
        for (int c = 0; c < sizeC; c++) {
            try {
                int r = image.getChannelColor(client, c).getRed();
                int g = image.getChannelColor(client, c).getGreen();
                int b = image.getChannelColor(client, c).getBlue();
                int a = image.getChannelColor(client, c).getAlpha();
                int rgba = r + (g << shift) + (b << 2 * shift) + (a << 3 * shift);
                imarisDataset.SetChannelColorRGBA(c, rgba);
                imarisDataset.SetChannelName(c, image.getChannelName(client, c));
            } catch (AccessException | ServiceException | ExecutionException | Error e) {
                LOGGER.warning(e.getMessage());
            }
        }
    }


    private static void setSpacing(Client client, ImageWrapper image, IDataSetPrx imarisDataset) {
        int sizeX = image.getPixels().getSizeX();
        int sizeY = image.getPixels().getSizeY();
        int sizeZ = image.getPixels().getSizeZ();
        String unit = image.getPixels().getPixelSizeX().getSymbol();
        float spacingX = (float) image.getPixels().getPixelSizeX().getValue();
        float spacingY = (float) image.getPixels().getPixelSizeY().getValue();
        float spacingZ = (float) image.getPixels().getPixelSizeZ().getValue();
        float minX = 0;
        float minY = 0;
        float minZ = 0;
        double delta = 0;
        try {
            List<omero.model.IObject> objects = client.findByQuery("select info from PlaneInfo as info " +
                                                                   "join fetch info.deltaT as dt " +
                                                                   "join fetch info.exposureTime as et " +
                                                                   "where info.pixels.id=" + image.getPixels().getId());
            _PlaneInfoOperationsNC planeInfo = (_PlaneInfoOperationsNC) objects.get(0);
            minX = (float) planeInfo.getPositionX().getValue();
            minY = (float) planeInfo.getPositionY().getValue();
            minZ = (float) planeInfo.getPositionZ().getValue();
            delta = planeInfo.getDeltaT().getValue();
            UnitsTime deltaUnit = planeInfo.getDeltaT().getUnit();
            Time t = new Time(delta, String.valueOf(deltaUnit));
            delta = Time.convertTime(t, "s").getValue();
        } catch (ServiceException | OMEROServerError e) {
            LOGGER.warning(e.getMessage());
        }
        float maxX = minX + spacingX * sizeX;
        float maxY = minY + spacingY * sizeY;
        float maxZ = minZ + spacingZ * sizeZ;

        try {
            imarisDataset.SetExtendMinX(minX);
            imarisDataset.SetExtendMaxX(maxX);
            imarisDataset.SetExtendMinY(minY);
            imarisDataset.SetExtendMaxY(maxY);
            imarisDataset.SetExtendMinZ(minZ);
            imarisDataset.SetExtendMaxZ(maxZ);
            imarisDataset.SetUnit(unit);
            String date = image.getAcquisitionDate().toString();
            imarisDataset.SetParameter("Image", "RecordingDate", date);
            imarisDataset.SetTimePoint(0, date);
            // TODO: Fix time
            imarisDataset.SetTimePointsDelta(delta);
        } catch (Error e) {
            LOGGER.warning(e.getMessage());
        }
    }

}
