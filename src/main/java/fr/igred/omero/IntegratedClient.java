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

package fr.igred.omero;

import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * OMERO client to connect and browse through the data.
 */
public class IntegratedClient {

	/** Format string for object qualifier **/
	private static final String FORMAT = "%%-%ds (ID:%%%dd)";

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
	 * Formats the object name using its ID and some padding.
	 *
	 * @param name    Object name.
	 * @param id      Object ID.
	 * @param padName Padding used for the name.
	 * @param padId   Padding used for the ID.
	 *
	 * @return The formatted object qualifier.
	 */
	private static String format(String name, long id, int padName, int padId) {
		String format = String.format(FORMAT, padName, padId);
		return String.format(format, name, id);
	}


	/**
	 * Gets the number of digits in an OMERO object ID (in base 10).
	 *
	 * @param object The OMERO object.
	 *
	 * @return The number of digits.
	 */
	private static Number getIDNbDigits(GenericObjectWrapper<?> object) {
		return StrictMath.log10(object.getId()) + 1;
	}


	/**
	 * Gets the padding value for a list of OMERO objects.
	 *
	 * @param objects The OMERO objects.
	 * @param mapper  The function applied to these objects.
	 * @param <T>     The type of object.
	 *
	 * @return The padding required.
	 */
	private static <T extends GenericObjectWrapper<?>>
	int getListPadding(Collection<T> objects, Function<? super T, ? extends Number> mapper) {
		return objects.stream()
		              .map(mapper)
		              .mapToInt(Number::intValue)
		              .max()
		              .orElse(0);
	}


	/**
	 * Adds items from a list of OMERO objects to a String JComboBox.
	 *
	 * @param objects    The OMERO objects.
	 * @param nameMapper The function to get the name of these objects.
	 * @param <T>        The type of object.
	 */
	private static <T extends GenericObjectWrapper<?>>
	List<String> formatList(Collection<T> objects, Function<? super T, String> nameMapper) {
		int padName = getListPadding(objects, o -> nameMapper.apply(o).length());
		int padId   = getListPadding(objects, IntegratedClient::getIDNbDigits) + 1;

		return objects.stream()
		              .map(o -> format(nameMapper.apply(o), o.getId(), padName, padId))
		              .collect(Collectors.toList());
	}


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
	 * Returns the names of the groups available to the user.
	 *
	 * @return See above.
	 */
	public List<String> getGroupNames() {
		return formatList(getGroups(), GroupWrapper::getName);
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
	 * Returns the names of the projects owned by the selected user in the selected group.
	 *
	 * @return See above.
	 */
	public List<String> getUserProjectNames() {
		return formatList(getUserProjects(), ProjectWrapper::getName);
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
	 * Returns the names of the datasets owned by the selected user in the selected project.
	 *
	 * @return See above.
	 */
	public List<String> getUserDatasetNames() {
		return formatList(getUserDatasets(), DatasetWrapper::getName);
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
	 * Returns the names of the images owned by the selected user in the selected dataset.
	 *
	 * @return See above.
	 */
	public List<String> getUserImageNames() {
		return formatList(getUserImages(), ImageWrapper::getName);
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
	 * Returns the names of the users available in the selected group.
	 *
	 * @return See above.
	 */
	public List<String> getUserNames() {
		return formatList(getUsers(), ExperimenterWrapper::getUserName);
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
	 * @throws OMEROException Cannot connect to OMERO or access data.
	 */
	public boolean connect(Connector connector) throws OMEROException {
		cleanUp();
		connector.connect(client);
		boolean connected = client.isConnected();
		if (connected) {
			try {
				user = client.getUser(client.getUser().getUserName());
				groups = client.getGroups();
			} catch (ExecutionException | AccessException | ServiceException e) {
				throw new OMEROException(e.getMessage(), e);
			}

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
	 * @throws OMEROException Cannot connect to OMERO or access data.
	 */
	public void switchGroup(int groupIndex) throws OMEROException {
		users.clear();
		groupProjects.clear();
		userProjects.clear();
		userDatasets.clear();
		userImages.clear();

		GroupWrapper group = groups.get(groupIndex);
		client.switchGroup(group.getGroupId());
		try {
			groupProjects = client.getProjects();
			groupProjects.sort(Comparator.comparing(ProjectWrapper::getName,
			                                        String.CASE_INSENSITIVE_ORDER));
			GroupWrapper updatedGroup = client.getGroup(group.getName());
			users = updatedGroup.getExperimenters();
		} catch (AccessException | ServiceException | ExecutionException e) {
			throw new OMEROException(e.getMessage(), e);
		}
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
		userProjects = new ArrayList<>(getGroupProjects());
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
	 * @throws OMEROException Cannot connect to OMERO or access data.
	 */
	public void loadUserDatasets(int projectIndex) throws OMEROException {
		userImages.clear();
		try {
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
		} catch (AccessException | ServiceException | ExecutionException | OMEROServerError e) {
			throw new OMEROException(e.getMessage(), e);
		}
	}


	/**
	 * Loads the images owned by the selected user in the given dataset.
	 *
	 * @param datasetIndex The selected dataset index.
	 *
	 * @throws OMEROException Cannot connect to OMERO or access data.
	 */
	public void loadUserImages(int datasetIndex) throws OMEROException {
		if (datasetIndex >= 0 && datasetIndex < userDatasets.size()) {
			try {
				userImages = getUserDataset(datasetIndex).getImages(client);
				userImages.sort(Comparator.comparing(ImageWrapper::getName,
				                                     String.CASE_INSENSITIVE_ORDER));
			} catch (AccessException | ExecutionException | ServiceException e) {
				throw new OMEROException(e.getMessage(), e);
			}
		} else {
			userImages.clear();
		}
	}

}
