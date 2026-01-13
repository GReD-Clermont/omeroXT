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
import fr.igred.omero.exception.OMEROServerError;
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
	 * Returns the group index.
	 *
	 * @param groupId The ID of the group to find in the group list.
	 *
	 * @return See above.
	 */
	public int getGroupIndex(long groupId) {
		for (int i = 0; i < groups.size(); i++) {
			if (groups.get(i).getGroupId() == groupId) {
				return i;
			}
		}
		return -1;
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
	 * Returns the index of the user with the given ID.
	 *
	 * @param userId The ID of the user.
	 *
	 * @return The index of the user, or -1 if not found.
	 */
	public int getUserIndex(long userId) {
		for (int i = 0; i < users.size(); i++) {
			if (users.get(i).getId() == userId) {
				return i;
			}
		}
		return -1;
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
		cleanUp();
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
		userImages.clear();
		userDatasets.clear();
		userProjects.clear();
		groupProjects.clear();
		users.clear();
		groups.clear();
	}


	/**
	 * Loads the groups available to the user.
	 *
	 * @param groupIndex The index of the group to select after loading.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws ServiceException   Cannot connect to OMERO.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void switchGroup(int groupIndex)
	throws AccessException, ServiceException, ExecutionException {
		users.clear();
		groupProjects.clear();
		userProjects.clear();
		userDatasets.clear();
		userImages.clear();

		GroupWrapper group = groups.get(groupIndex);
		client.switchGroup(group.getGroupId());
		groupProjects = client.getProjects();
		groupProjects.sort(Comparator.comparing(ProjectWrapper::getName,
		                                        String.CASE_INSENSITIVE_ORDER));
		GroupWrapper updatedGroup = client.getGroup(group.getName());
		users = updatedGroup.getExperimenters();
		users.sort(Comparator.comparing(ExperimenterWrapper::getUserName));
	}


	/**
	 * Loads the projects owned by the selected user.
	 *
	 * @param experimenterIndex The selected user index. If negative, all projects in the group are loaded.
	 */
	public void loadUserProjects(int experimenterIndex) {
		userImages.clear();
		userDatasets.clear();
		userProjects = new ArrayList<>(groupProjects);
		if (experimenterIndex > 0) {
			long userId = users.get(experimenterIndex).getId();
			userProjects.removeIf(p -> p.getOwner().getId() != userId);
		}
	}


	/**
	 * Loads the datasets in the selected project. If projectIndex is negative, loads all datasets owned by the user. If
	 * projectIndex is greater than the number of user projects, loads orphaned datasets.
	 *
	 * @param projectIndex The selected project index.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws ServiceException   Cannot connect to OMERO.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void loadUserDatasets(int projectIndex)
	throws AccessException, ServiceException, ExecutionException, OMEROServerError {
		userImages.clear();
		if (projectIndex < 0) {
			userDatasets = client.getDatasets();
		} else if (projectIndex >= userProjects.size()) {
			userDatasets = client.getOrphanedDatasets();
		} else {
			ProjectWrapper project = getUserProject(projectIndex);
			project.reload(client);
			userDatasets = project.getDatasets();
			userDatasets.sort(Comparator.comparing(DatasetWrapper::getName,
			                                       String.CASE_INSENSITIVE_ORDER));
		}
	}


	/**
	 * Loads the images owned by the selected user in the given dataset.
	 *
	 * @param datasetIndex The selected dataset index.
	 *
	 * @throws AccessException    Cannot access data.
	 * @throws ServiceException   Cannot connect to OMERO.
	 * @throws ExecutionException A Facility can't be retrieved or instantiated.
	 */
	public void loadUserImages(int datasetIndex)
	throws AccessException, ServiceException, ExecutionException {
		if (datasetIndex >= 0 && datasetIndex < userDatasets.size()) {
			userImages = getUserDataset(datasetIndex).getImages(client);
			userImages.sort(Comparator.comparing(ImageWrapper::getName,
			                                     String.CASE_INSENSITIVE_ORDER));
		} else {
			userImages.clear();
		}
	}

}
