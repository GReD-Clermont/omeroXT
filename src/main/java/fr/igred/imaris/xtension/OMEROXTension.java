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

package fr.igred.imaris.xtension;

import fr.igred.imaris.gui.OMEROXTGui;


/** Main class for the OMERO XTension. */
public final class OMEROXTension {


	/** Private constructor to prevent instantiation. */
	private OMEROXTension() {
	}


	/**
	 * Main entry point.
	 *
	 * @param args Command line arguments.
	 */
	public static void main(String[] args) {
		if (args != null && args.length > 0) {
			int      imarisID = Integer.parseInt(args[0]);
			Runnable omeroxt  = new OMEROXTGui(imarisID);
			omeroxt.run();
		} else {
			Runnable omeroxt = new OMEROXTGui();
			omeroxt.run();
		}
	}

}
