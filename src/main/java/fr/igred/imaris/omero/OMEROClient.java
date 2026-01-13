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

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;


/**
 * OMERO client to connect and browse through the data.
 */
public class OMEROClient {

	/** OMERO client **/
	protected final Client client = new Client();

	/** Current user **/
	private ExperimenterWrapper user = client.getUser();

	/** Groups available to the user **/
	private List<GroupWrapper> groups = new ArrayList<>(0);

	/** Projects available in the selected group **/
	private List<ProjectWrapper> groupProjects = new ArrayList<>(0);

	/** User projects **/
	private List<ProjectWrapper> userProjects = new ArrayList<>(0);

	/** User datasets **/
	private List<DatasetWrapper> userDatasets = new ArrayList<>(0);

	/** User images **/
	private List<ImageWrapper> userImages = new ArrayList<>(0);

	/** Users available in the selected group **/
	private List<ExperimenterWrapper> users = new ArrayList<>(0);


	/**
	 * Returns the current user.
	 *
	 * @return See above.
	 */
	public ExperimenterWrapper getUser() {
		return user;
	}


	/**
	 * Returns the current group ID.
	 *
	 * @return See above.
	 */
	public long getCurrentGroupId() {
		return client.getCurrentGroupId();
	}


	/**
	 * Returns the OMERO client.
	 *
	 * @return See above.
	 */
	public List<GroupWrapper> getGroups() {
		return Collections.unmodifiableList(groups);
	}


	/**
	 * Returns the projects available in the selected group.
	 *
	 * @return See above.
	 */
	public List<ProjectWrapper> getGroupProjects() {
		return Collections.unmodifiableList(groupProjects);
	}


	/**
	 * Returns the projects owned by the selected user in the selected group.
	 *
	 * @return See above.
	 */
	public List<ProjectWrapper> getUserProjects() {
		return Collections.unmodifiableList(userProjects);
	}


	/**
	 * Returns the project at the specified index in the user project list.
	 *
	 * @param index The index of the project to return.
	 *
	 * @return See above.
	 */
	public ProjectWrapper getUserProject(int index) {
		return userProjects.get(index);
	}


	/**
	 * Returns the datasets owned by the selected user in the selected project.
	 *
	 * @return See above.
	 */
	public List<DatasetWrapper> getUserDatasets() {
		return Collections.unmodifiableList(userDatasets);
	}


	/**
	 * Returns the dataset at the specified index in the user dataset list.
	 *
	 * @param index The index of the dataset to return.
	 *
	 * @return See above.
	 */
	public DatasetWrapper getUserDataset(int index) {
		return userDatasets.get(index);
	}


	/**
	 * Returns the images owned by the selected user in the selected dataset.
	 *
	 * @return See above.
	 */
	public List<ImageWrapper> getUserImages() {
		return Collections.unmodifiableList(userImages);
	}


	/**
	 * Returns the image at the specified index in the user images list.
	 *
	 * @param index The index of the dataset to return.
	 *
	 * @return See above.
	 */
	public ImageWrapper getUserImage(int index) {
		return userImages.get(index);
	}


	/**
	 * Returns the users available in the selected group.
	 *
	 * @return See above.
	 */
	public List<ExperimenterWrapper> getUsers() {
		return Collections.unmodifiableList(users);
	}


	/**
	 * Connects to OMERO using the provided connector
	 *
	 * @param connector The connector to use
	 *
	 * @return True if the connection was successful
	 *
	 * @throws AccessException  Cannot access data.
	 * @throws ServiceException Cannot connect to OMERO.
	 */
	public boolean connect(OMEROConnector connector)
	throws AccessException, ServiceException, ExecutionException {
		connector.connect(client);
		boolean connected = client.isConnected();
		if (connected) {
			user = client.getUser(client.getUser().getUserName());
			groups = client.getGroups();

			groups.removeIf(g -> g.getId() <= 2);
			groups.sort(Comparator.comparing(GroupWrapper::getName,
			                                 String.CASE_INSENSITIVE_ORDER));
		}
		return connected;
	}


	/** Disconnects from OMERO **/
	public void disconnect() {
		client.disconnect();
	}


	/** Cleans up resources **/
	public void cleanUp() {
		disconnect();
	}


	/**
	 * Loads the groups available to the user.
	 *
	 * @param group The group to select after loading.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws ServiceException   Cannot connect to OMERO.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void switchGroup(GroupWrapper group)
	throws AccessException, ServiceException, ExecutionException {
		client.switchGroup(group.getGroupId());
		groupProjects = client.getProjects();
		groupProjects.sort(Comparator.comparing(ProjectWrapper::getName,
		                                        String.CASE_INSENSITIVE_ORDER));
		GroupWrapper updatedGroup = client.getGroup(group.getName());
		users = updatedGroup.getExperimenters();
		users.sort(Comparator.comparing(ExperimenterWrapper::getUserName));
	}


	/**
	 * Loads the projects owned by the selected user in the selected group.
	 *
	 * @param experimenter The selected user. If null, all projects in the group are loaded.
	 */
	public void loadUserProjects(ExperimenterWrapper experimenter) {
		userProjects = new ArrayList<>(groupProjects);
		if (experimenter != null) {
			long userId = experimenter.getId();
			userProjects.removeIf(p -> p.getOwner().getId() != userId);
		}
	}


	/**
	 * Loads the datasets owned by the selected user in the selected project.
	 *
	 * @param project The selected project.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws ServiceException   Cannot connect to OMERO.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void loadUserDatasets(ProjectWrapper project)
	throws AccessException, ServiceException, ExecutionException {
		if (project != null) {
			project.reload(client);
			userDatasets = project.getDatasets();
			userDatasets.sort(Comparator.comparing(DatasetWrapper::getName,
			                                       String.CASE_INSENSITIVE_ORDER));
		} else {
			userDatasets.clear();
		}
	}


	/**
	 * Loads the images owned by the selected user in the selected dataset.
	 *
	 * @param dataset The selected dataset.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws ServiceException   Cannot connect to OMERO.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void loadUserImages(DatasetWrapper dataset)
	throws AccessException, ServiceException, ExecutionException {
		if (dataset != null) {
			userImages = dataset.getImages(client);
			userImages.sort(Comparator.comparing(ImageWrapper::getName,
			                                     String.CASE_INSENSITIVE_ORDER));
		} else {
			userImages.clear();
		}
	}

}
