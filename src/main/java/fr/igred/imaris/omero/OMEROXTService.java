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

package fr.igred.imaris.omero;

import Imaris.Error;
import Imaris.IApplicationPrx;
import ImarisServer.IServerPrx;
import com.bitplane.xt.BPImarisLib;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.Image2Imaris;
import fr.igred.omero.repository.ImageWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static Imaris.IApplicationPrxHelper.checkedCast;
import static fr.igred.omero.repository.Image2Imaris.createImarisDataset;


/** Service to handle OMERO and Imaris XT interactions **/
public class OMEROXTService extends OMEROClient {


	/** Imaris library **/
	private final BPImarisLib imarisLib = new BPImarisLib();

	/** Imaris instance IDs corresponding **/
	private final List<Integer> imarisIDs = new ArrayList<>(0);


	/**
	 * Returns the Imaris library instance.
	 *
	 * @return See above.
	 */
	public BPImarisLib getImarisLib() {
		return imarisLib;
	}


	/**
	 * Returns the list of Imaris instance IDs.
	 *
	 * @return See above.
	 */
	public List<Integer> getImarisIDs() {
		return Collections.unmodifiableList(imarisIDs);
	}


	/** Cleans up resources **/
	@Override
	public void cleanUp() {
		super.cleanUp();
		imarisLib.Disconnect();
	}


	/**
	 * Refreshes the list of Imaris instance IDs.
	 */
	public void refreshImaris() {
		ImarisServer.IServerPrx vServer = imarisLib.GetServer();

		int nImaris = vServer == null ? 0 : vServer.GetNumberOfObjects();
		imarisIDs.clear();
		for (int i = 0; i < nImaris; i++) {
			imarisIDs.add(vServer.GetObjectID(i));
		}
	}


	/**
	 * Loads the selected image into the specified Imaris instance.
	 *
	 * @param imageIndex  The index of the image to load.
	 * @param imarisIndex The Imaris instance ID index.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws Error              Imaris error.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void loadImage(int imageIndex, int imarisIndex)
	throws AccessException, Error, ExecutionException {
		int imarisID = imarisIDs.get(imarisIndex);

		IServerPrx imarisServer = imarisLib.GetServer();

		IApplicationPrx vApplication = checkedCast(imarisServer.GetObject(imarisID));

		createImarisDataset(client, getUserImage(imageIndex), vApplication);
	}


	/**
	 * Loads the ROIs of the selected image into the specified Imaris instance.
	 *
	 * @param imageIndex  The index of the image to load.
	 * @param imarisIndex The Imaris instance ID index.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws ServiceException   Cannot connect to OMERO.
	 * @throws Error              Imaris error.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void loadROIs(int imageIndex, int imarisIndex)
	throws AccessException, ServiceException, Error, ExecutionException {
		int imarisID = imarisIDs.get(imarisIndex);

		IServerPrx vServer = imarisLib.GetServer();

		IApplicationPrx vApplication = checkedCast(vServer.GetObject(imarisID));

		Image2Imaris.loadROIs(client, getUserImage(imageIndex), vApplication);
	}

}
