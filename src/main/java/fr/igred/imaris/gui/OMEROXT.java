package fr.igred.imaris.gui;

import fr.igred.omero.Client;
import fr.igred.omero.GenericObjectWrapper;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.GenericRepositoryObjectWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static fr.igred.omero.repository.Image2Imaris.createImarisDataset;
import static javax.swing.JOptionPane.showMessageDialog;

/**
 * Main window for the OMERO batch plugin.
 */
public class OMEROXT extends JFrame implements Runnable {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private static final String FORMAT = "%%-%ds (ID:%%%dd)";

	// connection management
	private final JLabel connectionStatus = new JLabel("Disconnected");
	private final JButton connect = new JButton("Connect");
	private final JButton disconnect = new JButton("Disconnect");

	// group and user selection
	private final JComboBox<String> groupList = new JComboBox<>();
	private final JComboBox<String> userList = new JComboBox<>();

	// choice of the dataSet
	private final JComboBox<String> projectListIn = new JComboBox<>();
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	private final JComboBox<String> imageListIn = new JComboBox<>();
	private final JCheckBox loadROIs = new JCheckBox("Load ROIs ");

	private final JComboBox<String> projectListOut = new JComboBox<>();
	private final JComboBox<String> datasetListOut = new JComboBox<>();

	//variables to keep
	private final int imarisID;
	private transient Client client;
	private transient List<GroupWrapper> groups;
	private transient List<ProjectWrapper> groupProjects;
	private transient List<ProjectWrapper> userProjects;
	private transient List<DatasetWrapper> userDatasets;
	private transient List<ImageWrapper> userImages;
	private transient List<ProjectWrapper> myProjects;
	private transient List<DatasetWrapper> myDatasets;
	private transient List<ExperimenterWrapper> users;
	private transient ExperimenterWrapper exp;


	/**
	 * Creates a new window.
	 */
	public OMEROXT(int imarisID) {
		super("OMERO XT");
		this.imarisID = imarisID;

		final int width = 720;
		final int height = 640;

		final String projectName = "Project Name: ";
		final String datasetName = "Dataset Name: ";
		final String imageName = "Image Name: ";

		final Font listFont = new Font(Font.MONOSPACED, Font.PLAIN, 12);
		final Font warnFont = new Font("Arial", Font.ITALIC + Font.BOLD, 12);

		final Color orange = new Color(250, 140, 0);

		final Dimension smallHorizontal = new Dimension(20, 0);

		Container cp = super.getContentPane();

		super.setSize(width, height);
		super.setMinimumSize(super.getSize());
		super.setLocationRelativeTo(null);
		super.addWindowListener(new ClientDisconnector());

		JPanel panelWarning = new JPanel();
		JLabel warning = new JLabel("Warning: all windows will be closed.");
		warning.setForeground(orange);
		warning.setFont(warnFont);
		panelWarning.add(warning);
		cp.add(panelWarning);

		JPanel connection = new JPanel();
		JLabel labelConnection = new JLabel("Connection status: ");
		labelConnection.setLabelFor(connectionStatus);
		connectionStatus.setForeground(Color.RED);
		connection.add(labelConnection);
		connection.add(connectionStatus);
		connection.add(Box.createRigidArea(smallHorizontal));
		connection.add(connect);
		connection.add(disconnect);
		disconnect.setVisible(false);
		connect.addActionListener(e -> connect());
		disconnect.addActionListener(e -> disconnect());
		connection.setBorder(BorderFactory.createTitledBorder("Connection"));
		cp.add(connection);

		JLabel labelGroup = new JLabel("Group Name: ");
		JLabel labelUser = new JLabel("User Name: ");
		labelGroup.setLabelFor(groupList);
		labelUser.setLabelFor(userList);
		// choices of input images
		JPanel input1a = new JPanel();
		input1a.add(labelGroup);
		input1a.add(groupList);
		input1a.add(Box.createRigidArea(smallHorizontal));
		input1a.add(labelUser);
		input1a.add(userList);
		groupList.addItemListener(this::updateGroup);
		userList.addItemListener(this::updateUser);
		groupList.setFont(listFont);
		userList.setFont(listFont);

		JLabel labelProjectIn = new JLabel(projectName);
		JLabel labelDatasetIn = new JLabel(datasetName);
		JLabel labelImageIn = new JLabel(imageName);
		JButton preview = new JButton("Load");
		labelProjectIn.setLabelFor(projectListIn);
		labelDatasetIn.setLabelFor(datasetListIn);
		labelImageIn.setLabelFor(imageListIn);
		JPanel input1b = new JPanel();
		input1b.add(labelProjectIn);
		input1b.add(projectListIn);
		input1b.add(Box.createRigidArea(smallHorizontal));
		input1b.add(labelDatasetIn);
		input1b.add(datasetListIn);
		input1b.add(Box.createRigidArea(smallHorizontal));
		input1b.add(labelImageIn);
		input1b.add(imageListIn);
		input1b.add(preview);
		projectListIn.addItemListener(this::updateInputProject);
		datasetListIn.addItemListener(this::updateInputDataset);
		imageListIn.addItemListener(e -> repack());
		preview.addActionListener(e -> loadImage());
		projectListIn.setFont(listFont);
		datasetListIn.setFont(listFont);
		imageListIn.setFont(listFont);

		JPanel input1c = new JPanel();
		input1c.add(loadROIs);

		JPanel panelInput = new JPanel();
		panelInput.add(input1a);
		panelInput.add(input1b);
		panelInput.add(input1c);
		panelInput.setVisible(true);
		panelInput.setLayout(new BoxLayout(panelInput, BoxLayout.PAGE_AXIS));
		panelInput.setBorder(BorderFactory.createTitledBorder("Input"));
		cp.add(panelInput);


		JLabel labelProjectOut = new JLabel(projectName);
		JLabel labelDatasetOut = new JLabel(datasetName);
		labelProjectOut.setLabelFor(projectListOut);
		labelDatasetOut.setLabelFor(datasetListOut);
		// existing dataset
		JPanel output = new JPanel();
		output.add(labelProjectOut);
		output.add(projectListOut);
		output.add(Box.createRigidArea(smallHorizontal));
		output.add(labelDatasetOut);
		output.add(datasetListOut);
		output.add(Box.createRigidArea(smallHorizontal));
		JButton newDatasetBtn = new JButton("New");
		output.add(newDatasetBtn);
		projectListOut.addItemListener(this::updateOutputProject);
		datasetListOut.addItemListener(this::updateOutputDataset);
		newDatasetBtn.addActionListener(this::createNewDataset);
		output.setVisible(true);
		projectListOut.setFont(listFont);
		datasetListOut.setFont(listFont);

		// choice of output
		JPanel panelOutput = new JPanel();
		panelOutput.add(output);
		panelOutput.setLayout(new BoxLayout(panelOutput, BoxLayout.PAGE_AXIS));
		panelOutput.setBorder(BorderFactory.createTitledBorder("Output"));
		cp.add(panelOutput);

		cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));
		cp.setVisible(true);
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


	public static void main(String[] args) {
		if (args != null && args.length > 0) {
			int imarisID = Integer.parseInt(args[0]);
			OMEROXT omeroxt = new OMEROXT(imarisID);
			omeroxt.run();
		}
	}


	/**
	 * Retrieves user projects and datasets.
	 *
	 * @param username The OMERO user.
	 * @param userId   The user ID.
	 */
	public void userProjectsAndDatasets(String username, long userId) {
		if ("All members".equals(username)) {
			userProjects = groupProjects;
		} else {
			userProjects = groupProjects.stream()
										.filter(project -> project.getOwner().getId() == userId)
										.collect(Collectors.toList());
		}
		myProjects = groupProjects.stream()
								  .filter(project -> project.getOwner().getId() == exp.getId())
								  .collect(Collectors.toList());
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
				int index = ((JComboBox<?>) source).getSelectedIndex();
				DatasetWrapper dataset = this.userDatasets.get(index);
				try {
					this.userImages = dataset.getImages(client);
				} catch (AccessException | ServiceException | ExecutionException ex) {
					LOGGER.warning(ex.getMessage());
				}
				this.userImages.sort(Comparator.comparing(GenericRepositoryObjectWrapper::getName,
														  String.CASE_INSENSITIVE_ORDER));
				imageListIn.removeAllItems();
				int padName = getListPadding(userImages, i -> i.getName().length());
				int padId = getListPadding(userImages, i -> (int) (StrictMath.log10(i.getId()))) + 1;
				for (ImageWrapper i : this.userImages) {
					imageListIn.addItem(format(i.getName(), i.getId(), padName, padId));
				}
				if (!this.userImages.isEmpty()) imageListIn.setSelectedIndex(0);
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
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = userProjects.get(index);
				this.userDatasets = project.getDatasets();
				this.userDatasets.sort(Comparator.comparing(DatasetWrapper::getName,
															String.CASE_INSENSITIVE_ORDER));
				datasetListIn.removeAllItems();
				int padName = getListPadding(userDatasets, d -> d.getName().length());
				int padId = getListPadding(userDatasets, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				for (DatasetWrapper d : this.userDatasets) {
					datasetListIn.addItem(format(d.getName(), d.getId(), padName, padId));
				}
				if (!this.userDatasets.isEmpty()) datasetListIn.setSelectedIndex(0);
			}
		}
	}


	/**
	 * Updates the display when the output dataset is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateOutputDataset(ItemEvent e) {
		this.repack();
	}


	/**
	 * Updates the display when the output project is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateOutputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = myProjects.get(index);
				this.myDatasets = project.getDatasets();
				this.myDatasets.sort(Comparator.comparing(DatasetWrapper::getName, String.CASE_INSENSITIVE_ORDER));
				datasetListOut.removeAllItems();
				int padName = getListPadding(myDatasets, d -> d.getName().length());
				int padId = getListPadding(myDatasets, g -> (int) (StrictMath.log10(g.getId()))) + 1;
				for (DatasetWrapper d : this.myDatasets) {
					datasetListOut.addItem(format(d.getName(), d.getId(), padName, padId));
				}
				if (!this.userDatasets.isEmpty()) datasetListOut.setSelectedIndex(0);
			}
		}
	}


	/**
	 * Creates a new dataset and updates the display.
	 *
	 * @param e The event triggering this.
	 */
	private void createNewDataset(ActionEvent e) {
		int index = projectListOut.getSelectedIndex();
		ProjectWrapper project = myProjects.get(index);
		long id = -1;
		String name = (String) JOptionPane.showInputDialog(this,
														   "New dataset name:",
														   "Create a new dataset",
														   JOptionPane.QUESTION_MESSAGE,
														   null,
														   null,
														   null);
		if (name == null) return;
		try {
			DatasetWrapper newDataset = project.addDataset(client, name, "");
			id = newDataset.getId();
		} catch (ExecutionException | ServiceException | AccessException exception) {
			warningWindow("Could not create dataset: " + exception.getMessage());
		}
		projectListOut.setSelectedIndex(-1);
		projectListOut.setSelectedIndex(index);
		boolean searchOut = true;
		for (int i = 0; searchOut && i < myDatasets.size(); i++) {
			if (myDatasets.get(i).getId() == id) {
				datasetListOut.setSelectedIndex(i);
				searchOut = false;
			}
		}

		int inputProject = projectListIn.getSelectedIndex();
		projectListIn.setSelectedIndex(-1);
		projectListIn.setSelectedIndex(inputProject);

		boolean searchIn = true;
		long inputDatasetID = userDatasets.get(datasetListIn.getSelectedIndex()).getId();
		for (int i = 0; searchIn && i < userDatasets.size(); i++) {
			if (userDatasets.get(i).getId() == inputDatasetID) {
				datasetListIn.setSelectedIndex(i);
				searchIn = false;
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
			int index = userList.getSelectedIndex();
			String username = userList.getItemAt(index);
			long userId = -1;
			if (index >= 1) userId = users.get(index - 1).getId();
			userProjectsAndDatasets(username, userId);
			projectListIn.removeAllItems();
			projectListOut.removeAllItems();
			datasetListIn.removeAllItems();
			datasetListOut.removeAllItems();
			int padName = getListPadding(userProjects, p -> p.getName().length());
			int padId = getListPadding(userProjects, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (ProjectWrapper project : userProjects) {
				projectListIn.addItem(format(project.getName(), project.getId(), padName, padId));
			}
			int padMyName = getListPadding(myProjects, p -> p.getName().length());
			int padMyId = getListPadding(myProjects, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (ProjectWrapper project : myProjects) {
				projectListOut.addItem(format(project.getName(), project.getId(), padMyName, padMyId));
			}
			if (!userProjects.isEmpty()) projectListIn.setSelectedIndex(0);
			if (!myProjects.isEmpty()) projectListOut.setSelectedIndex(0);
		}
	}


	/**
	 * Updates the display when the selected group is changed.
	 *
	 * @param e The event triggering this.
	 */
	private void updateGroup(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int index = groupList.getSelectedIndex();
			long id = groups.get(index).getId();
			String groupName = groups.get(index).getName();
			client.switchGroup(id);

			groupProjects = new ArrayList<>(0);
			try {
				groupProjects = client.getProjects();
			} catch (ServiceException | ExecutionException | AccessException exception) {
				LOGGER.warning(exception.getMessage());
			}
			groupProjects.sort(Comparator.comparing(ProjectWrapper::getName,
													String.CASE_INSENSITIVE_ORDER));
			try {
				GroupWrapper group = client.getGroup(groupName);
				users = group.getExperimenters();
			} catch (ExecutionException | ServiceException | AccessException exception) {
				LOGGER.warning(exception.getMessage());
			}
			users.sort(Comparator.comparing(ExperimenterWrapper::getUserName));
			userList.removeAllItems();

			userList.addItem("All members");
			int padName = getListPadding(users, u -> u.getUserName().length());
			int padId = getListPadding(users, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			int selected = 0;
			for (ExperimenterWrapper user : users) {
				userList.addItem(format(user.getUserName(), user.getId(), padName, padId));
				if (user.getId() == exp.getId()) {
					selected = users.indexOf(user) + 1;
				}
			}
			userList.setSelectedIndex(selected);
		}
	}


	/**
	 * Displays a connection dialog to connect to OMERO.
	 *
	 * @return True if the connection was successful.
	 */
	private boolean connect() {
		final Color green = new Color(0, 153, 0);
		boolean connected = false;
		if (client == null) client = new Client();
		OMEROConnectDialog connectDialog = new OMEROConnectDialog();
		connectDialog.connect(client);
		if (!connectDialog.wasCancelled()) {

			long groupId = client.getCurrentGroupId();

			try {
				exp = client.getUser(client.getUser().getUserName());
			} catch (ExecutionException | ServiceException | AccessException e) {
				LOGGER.warning(e.getCause().getMessage());
			} catch (NoSuchElementException e) {
				LOGGER.warning(e.getCause().getMessage());
				return false;
			}
			groups = exp.getGroups();
			groups.removeIf(g -> g.getId() <= 2);

			int padName = getListPadding(groups, g -> g.getName().length());
			int padId = getListPadding(groups, g -> (int) (StrictMath.log10(g.getId()))) + 1;
			for (GroupWrapper group : groups) {
				groupList.addItem(format(group.getName(), group.getId(), padName, padId));
			}

			connectionStatus.setText("Connected");
			connectionStatus.setForeground(green);
			connect.setVisible(false);
			disconnect.setVisible(true);

			int index = -1;
			for (int i = 0; index < 0 && i < groups.size(); i++) {
				if (groups.get(i).getId() == groupId) index = i;
			}
			groupList.setSelectedIndex(-1);
			groupList.setSelectedIndex(index);
			connected = true;
		}
		return connected;
	}


	/**
	 * Disconnects from OMERO.
	 */
	private void disconnect() {
		client.disconnect();
		connectionStatus.setText("Disconnected");
		connectionStatus.setForeground(Color.RED);
		connect.setVisible(true);
		disconnect.setVisible(false);
		groupList.removeAllItems();
		userList.removeAllItems();
		projectListIn.removeAllItems();
		projectListOut.removeAllItems();
		datasetListIn.removeAllItems();
		datasetListOut.removeAllItems();
		imageListIn.removeAllItems();
	}


	/**
	 * Shows a new window to preview the current dataset.
	 */
	private void loadImage() {

		int index = imageListIn.getSelectedIndex();
		if (index >= 0) {
			ImageWrapper image = userImages.get(index);
			createImarisDataset(client, image, imarisID);
		}
	}


	/**
	 * Repacks this window.
	 */
	private void repack() {
		Dimension minSize = this.getMinimumSize();
		this.setMinimumSize(this.getSize());
		this.pack();
		this.setMinimumSize(minSize);
	}


	/**
	 * When an object implementing interface <code>Runnable</code> is used to create a thread, starting the thread
	 * causes the object's
	 * <code>run</code> method to be called in that separately executing
	 * thread.
	 * <p>
	 * The general contract of the method <code>run</code> is that it may take any action whatsoever.
	 *
	 * @see Thread#run()
	 */
	@Override
	public void run() {
		this.setVisible(true);
	}

	private class ClientDisconnector extends WindowAdapter {

		ClientDisconnector() {
			super();
		}


		@Override
		public void windowClosing(WindowEvent e) {
			super.windowClosing(e);
			Client c = client;
			if (c != null) c.disconnect();
		}

	}

}