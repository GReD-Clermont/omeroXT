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

package fr.igred.imaris.gui;

import Imaris.Error;
import fr.igred.imaris.omero.OMEROConnector;
import fr.igred.imaris.omero.OMEROXTService;
import fr.igred.omero.GenericObjectWrapper;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Logger;

import static java.awt.Font.MONOSPACED;
import static java.awt.Font.PLAIN;
import static javax.swing.JOptionPane.showMessageDialog;


/**
 * Main window for the OMERO XT application.
 */
public class OMEROXTGui extends JFrame implements Runnable {

	/** Logger **/
	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	/** Format string for object qualifier **/
	private static final String FORMAT = "%%-%ds (ID:%%%dd)";

	/** Font for lists **/
	private static final Font LIST_FONT = new Font(MONOSPACED, PLAIN, 12);

	/** Connect button **/
	private final JButton connect    = new JButton("Connect");
	/** Disconnect button **/
	private final JButton disconnect = new JButton("Disconnect");

	/** Imaris instances list **/
	private final JComboBox<Integer> imarisList = new JComboBox<>();

	/** Groups list **/
	private final JComboBox<String> groupList = new JComboBox<>();
	/** Users list **/
	private final JComboBox<String> userList  = new JComboBox<>();

	/** Projects list **/
	private final JComboBox<String> projectListIn = new JComboBox<>();
	/** Datasets list **/
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	/** Images list **/
	private final JComboBox<String> imageListIn   = new JComboBox<>();

	/** Underlying OMEROXTService handler **/
	private final OMEROXTService omeroxt = new OMEROXTService();


	/**
	 * Creates a new window.
	 */
	public OMEROXTGui() {
		super("OMERO XT");
		super.setDefaultCloseOperation(EXIT_ON_CLOSE);

		final int width  = 800;
		final int height = 260;

		Dimension smallHorizontal = new Dimension(20, 0);

		Container cp = super.getContentPane();

		super.setSize(width, height);
		super.setMinimumSize(super.getSize());
		super.setLocationRelativeTo(null);
		super.addWindowListener(new ClientDisconnector());

		JPanel imaris = createImarisPanel();
		refreshImaris();

		JPanel connection = createConnectionPanel();

		JPanel context = createContextPanel();

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.LINE_AXIS));
		header.add(imaris);
		header.add(connection);
		header.add(context);
		cp.add(header);

		JPanel input = createInputPanel();
		cp.add(input);

		JPanel actions = createActionsPanel();
		cp.add(actions);

		cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));
		cp.setVisible(true);
		repack();

		Dimension imarisSize = imaris.getSize();
		imarisSize.width += smallHorizontal.width;
		imaris.setMaximumSize(imarisSize);

		connection.setMaximumSize(connection.getSize());

		Dimension contextSize = context.getMaximumSize();
		contextSize.height = context.getSize().height;
		context.setMaximumSize(contextSize);

		Dimension inputSize = input.getMaximumSize();
		inputSize.height = input.getSize().height;
		input.setMaximumSize(inputSize);
	}


	/**
	 * Creates a new window, selecting the given Imaris ID.
	 *
	 * @param aImarisID The Imaris ID to select.
	 */
	public OMEROXTGui(int aImarisID) {
		this();
		this.imarisList.setSelectedItem(aImarisID);
	}


	/**
	 * Creates an horizontal rigid area of fixed size.
	 *
	 * @return The horizontal rigid area.
	 */
	private static Component createHorizontalRigidArea() {
		return Box.createRigidArea(new Dimension(10, 0));
	}


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
	 * @param list       The list to populate.
	 * @param <T>        The type of object.
	 */
	private static <T extends GenericObjectWrapper<?>>
	void updateBox(Collection<T> objects,
	               Function<? super T, String> nameMapper,
	               JComboBox<? super String> list) {
		// Preserve action listeners
		ActionListener[] actionListeners = list.getActionListeners();

		// Remove them temporarily
		for (ActionListener listener : actionListeners) {
			list.removeActionListener(listener);
		}

		// Populate the list
		list.removeAllItems();
		int padName = getListPadding(objects, o -> nameMapper.apply(o).length());
		int padId   = getListPadding(objects, OMEROXTGui::getIDNbDigits) + 1;
		for (T object : objects) {
			list.addItem(format(nameMapper.apply(object), object.getId(), padName, padId));
		}

		// Re-add action listeners
		for (ActionListener listener : actionListeners) {
			list.addActionListener(listener);
		}
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
	 * Shows a warning window.
	 *
	 * @param message The warning message.
	 */
	public static void warningWindow(String message) {
		showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
	}


	/**
	 * Shows a error window.
	 *
	 * @param message The error message.
	 */
	public static void errorWindow(String message) {
		showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}


	/**
	 * Creates the Imaris panel.
	 *
	 * @return The Imaris panel.
	 */
	private JPanel createImarisPanel() {
		JPanel  imaris        = new JPanel();
		JLabel  labelImaris   = new JLabel("Instance: ");
		JButton refreshImaris = new JButton("Refresh");
		refreshImaris.addActionListener(e -> refreshImaris());
		labelImaris.setLabelFor(imarisList);
		imaris.add(labelImaris);
		imaris.add(imarisList);
		imaris.add(createHorizontalRigidArea());
		imaris.add(refreshImaris);
		imaris.setBorder(BorderFactory.createTitledBorder("Imaris"));
		return imaris;
	}


	/**
	 * Creates the connection panel.
	 *
	 * @return The connection panel.
	 */
	private JPanel createConnectionPanel() {
		JPanel connection       = new JPanel();
		JLabel labelConnection  = new JLabel("Status: ");
		JLabel connectionStatus = new JLabel("Disconnected");
		labelConnection.setLabelFor(connectionStatus);
		connectionStatus.setForeground(Color.RED);
		connection.add(labelConnection);
		connection.add(connectionStatus);
		connection.add(createHorizontalRigidArea());
		connection.add(connect);
		connection.add(disconnect);
		disconnect.setVisible(false);
		connect.addActionListener(e -> connect(connectionStatus));
		disconnect.addActionListener(e -> disconnect(connectionStatus));
		connection.setBorder(BorderFactory.createTitledBorder("Connection"));
		return connection;
	}


	/**
	 * Creates the context panel.
	 *
	 * @return The context panel.
	 */
	private JPanel createContextPanel() {
		JLabel labelGroup = new JLabel("Group: ");
		JLabel labelUser  = new JLabel("User: ");
		labelGroup.setLabelFor(groupList);
		labelUser.setLabelFor(userList);

		JPanel context = new JPanel();
		context.add(labelGroup);
		context.add(groupList);
		context.add(createHorizontalRigidArea());
		context.add(labelUser);
		context.add(userList);
		context.setBorder(BorderFactory.createTitledBorder("Group & User"));

		groupList.addItemListener(this::updateGroup);
		userList.addItemListener(this::updateUser);
		groupList.setFont(LIST_FONT);
		userList.setFont(LIST_FONT);

		return context;
	}


	/**
	 * Creates the containers panel.
	 *
	 * @return The containers panel.
	 */
	private JPanel createContainersPanel() {
		JLabel labelProjectIn = new JLabel("Project: ");
		JLabel labelDatasetIn = new JLabel("Dataset: ");
		labelProjectIn.setLabelFor(projectListIn);
		labelDatasetIn.setLabelFor(datasetListIn);

		JPanel containers = new JPanel();
		containers.add(labelProjectIn);
		containers.add(projectListIn);
		containers.add(createHorizontalRigidArea());
		containers.add(labelDatasetIn);
		containers.add(datasetListIn);
		return containers;
	}


	/**
	 * Creates the images panel.
	 *
	 * @return The images panel.
	 */
	private JPanel createImagesPanel() {
		JLabel labelImageIn = new JLabel("Image: ");
		labelImageIn.setLabelFor(imageListIn);
		JPanel images = new JPanel();
		images.add(labelImageIn);
		images.add(imageListIn);
		return images;
	}


	/**
	 * Creates the input panel.
	 *
	 * @return The input panel.
	 */
	private JPanel createInputPanel() {
		JPanel input = new JPanel();
		input.add(createContainersPanel());
		input.add(createImagesPanel());
		input.setLayout(new BoxLayout(input, BoxLayout.PAGE_AXIS));
		input.setBorder(BorderFactory.createTitledBorder("Input"));
		projectListIn.addItemListener(this::updateInputProject);
		datasetListIn.addItemListener(this::updateInputDataset);
		imageListIn.addItemListener(e -> repack());
		projectListIn.setFont(LIST_FONT);
		datasetListIn.setFont(LIST_FONT);
		imageListIn.setFont(LIST_FONT);
		return input;
	}


	/**
	 * Creates the actions panel containing the action buttons.
	 *
	 * @return The actions panel.
	 */
	private JPanel createActionsPanel() {
		JPanel  actions   = new JPanel();
		JButton loadImage = new JButton("Load image");
		JButton loadROIs  = new JButton("Load ROIs");
		loadImage.addActionListener(e -> loadImage());
		loadROIs.addActionListener(e -> loadROIs());
		actions.add(loadImage);
		actions.add(loadROIs);
		return actions;
	}


	/**
	 * Refreshes the list of Imaris instances.
	 */
	private void refreshImaris() {
		omeroxt.refreshImaris();
		imarisList.removeAllItems();
		for (Integer id : omeroxt.getImarisIDs()) {
			imarisList.addItem(id);
		}
		this.repack();
	}


	/**
	 * Updates the display when the input dataset is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateInputDataset(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				JComboBox<?> box = (JComboBox<?>) source;

				int datasetIndex = box.getSelectedIndex();
				try {
					omeroxt.loadUserImages(datasetIndex);
				} catch (AccessException | ServiceException | ExecutionException ex) {
					LOGGER.warning(ex.getMessage());
				}
				List<ImageWrapper> userImages = omeroxt.getUserImages();

				imageListIn.removeAllItems();
				updateBox(userImages, ImageWrapper::getName, imageListIn);
				if (!userImages.isEmpty()) {
					imageListIn.setSelectedIndex(0);
				}
			}
		}
		this.repack();
	}


	/**
	 * Updates the display when the input project is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateInputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int projectIndex = ((JComboBox<?>) source).getSelectedIndex();

				try {
					omeroxt.loadUserDatasets(projectIndex);
				} catch (AccessException | ServiceException | ExecutionException | OMEROServerError ex) {
					LOGGER.warning(ex.getMessage());
				}
				List<DatasetWrapper> userDatasets = omeroxt.getUserDatasets();

				imageListIn.removeAllItems();
				datasetListIn.removeAllItems();
				updateBox(userDatasets, DatasetWrapper::getName, datasetListIn);
				if (!userDatasets.isEmpty()) {
					datasetListIn.setSelectedIndex(0);
				}
			}
		}
	}


	/**
	 * Updates the display when the selected user is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateUser(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int userIndex = userList.getSelectedIndex() - 1;

			omeroxt.loadUserProjects(userIndex);

			List<ProjectWrapper> userProjects = omeroxt.getUserProjects();

			imageListIn.removeAllItems();
			datasetListIn.removeAllItems();
			updateBox(userProjects, ProjectWrapper::getName, projectListIn);
			projectListIn.addItem("[Orphaned datasets]");
			if (!userProjects.isEmpty()) {
				projectListIn.setSelectedIndex(0);
			}
		}
	}


	/**
	 * Updates the display when the selected group is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateGroup(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int groupIndex = groupList.getSelectedIndex();

			try {
				omeroxt.switchGroup(groupIndex);
			} catch (ServiceException | ExecutionException | AccessException exception) {
				LOGGER.warning(exception.getMessage());
			}

			List<ExperimenterWrapper> users = omeroxt.getUsers();

			updateBox(users, ExperimenterWrapper::getUserName, userList);
			userList.insertItemAt("All members", 0);

			int userIndex = omeroxt.getUserIndex(omeroxt.getUser().getId());
			// Add 1 to skip "All members"
			userList.setSelectedIndex(userIndex + 1);
		}
	}


	/**
	 * Displays a connection dialogue to connect to OMERO.
	 */
	private void connect(JLabel connectionStatus) {
		Color green = new Color(0, 153, 0);

		OMEROConnector connectDialog = new OMEROConnectDialog();
		try {
			omeroxt.connect(connectDialog);
		} catch (ExecutionException | ServiceException | AccessException | NoSuchElementException e) {
			LOGGER.warning(e.getCause().getMessage());
		}
		long currentGroupId = omeroxt.getCurrentGroupId();

		List<GroupWrapper> groups = omeroxt.getGroups();

		updateBox(groups, GroupWrapper::getName, groupList);

		connectionStatus.setText("Connected");
		connectionStatus.setForeground(green);
		connect.setVisible(false);
		disconnect.setVisible(true);

		int groupIndex = omeroxt.getGroupIndex(currentGroupId);
		groupList.setSelectedIndex(-1);
		groupList.setSelectedIndex(groupIndex);
	}


	/**
	 * Disconnects from OMERO.
	 */
	private void disconnect(JLabel connectionStatus) {
		omeroxt.disconnect();
		connectionStatus.setText("Disconnected");
		connectionStatus.setForeground(Color.RED);
		connect.setVisible(true);
		disconnect.setVisible(false);
		groupList.removeAllItems();
		userList.removeAllItems();
		projectListIn.removeAllItems();
		datasetListIn.removeAllItems();
		imageListIn.removeAllItems();
	}


	/**
	 * Cleans up resources.
	 */
	private void cleanUp() {
		disconnect.doClick();
		omeroxt.cleanUp();
	}


	/**
	 * Releases all of the native screen resources used by this {@code Window}, its subcomponents, and all of its owned
	 * children. That is, the resources for these {@code Component}s will be destroyed, any memory they consume will be
	 * returned to the OS, and they will be marked as undisplayable.
	 * <p>
	 * The {@code Window} and its subcomponents can be made displayable again by rebuilding the native resources with a
	 * subsequent call to {@code pack} or {@code show}. The states of the recreated {@code Window} and its subcomponents
	 * will be identical to the states of these objects at the point where the {@code Window} was disposed (not
	 * accounting for additional modifications between those actions).
	 * <p>
	 * <b>Note</b>: When the last displayable window
	 * within the Java virtual machine (VM) is disposed of, the VM may terminate.  See <a
	 * href="doc-files/AWTThreadIssues.html#Autoshutdown"> AWT Threading Issues</a> for more information.
	 *
	 * @see Component#isDisplayable
	 * @see #pack
	 * @see #show
	 */
	@Override
	public void dispose() {
		cleanUp();
		super.dispose();
	}


	/**
	 * Loads the selected image into Imaris.
	 */
	private void loadImage() {
		int imageIndex = imageListIn.getSelectedIndex();
		int imarisIdx  = imarisList.getSelectedIndex();

		if (imageIndex >= 0 && imarisIdx >= 0) {
			try {
				omeroxt.loadImage(imageIndex, imarisIdx);
			} catch (AccessException | ExecutionException | Error e) {
				LOGGER.warning(e.getMessage());
			}
		} else {
			warningWindow("No image or Imaris instance selected.");
		}
	}


	/**
	 * Loads the ROIs from the selected image into Imaris, as Surfaces.
	 */
	private void loadROIs() {
		int imageIndex = imageListIn.getSelectedIndex();
		int imarisIdx  = imarisList.getSelectedIndex();

		if (imageIndex >= 0 && imarisIdx >= 0) {
			try {
				omeroxt.loadROIs(imageIndex, imarisIdx);
			} catch (AccessException | ServiceException | ExecutionException | Error e) {
				LOGGER.warning(e.getMessage());
			}
		} else {
			warningWindow("No image or Imaris instance selected.");
		}
	}


	/**
	 * Repacks this window.
	 */
	private void repack() {
		this.pack();
		this.setMinimumSize(this.getSize());
	}


	/**
	 * When an object implementing interface {@link Runnable} is used to create a thread, starting the thread causes the
	 * object run method to be called in that separately executing thread.
	 * <br>
	 * The general contract of the method {@code run} is that it may take any action whatsoever.
	 *
	 * @see Thread#run()
	 */
	@Override
	public void run() {
		this.setVisible(true);
	}


	/**
	 * Window adapter to clean up and dispose of the current object when closing.
	 */
	private class ClientDisconnector extends WindowAdapter {

		/** Constructor **/
		ClientDisconnector() {
		}


		/**
		 * Invoked when a window is in the process of being closed.
		 *
		 * @param e the event to be processed
		 */
		@Override
		public void windowClosing(WindowEvent e) {
			dispose();
		}

	}

}