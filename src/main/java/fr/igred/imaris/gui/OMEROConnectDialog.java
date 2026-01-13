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

import fr.igred.imaris.omero.OMEROConnector;
import fr.igred.omero.Client;
import fr.igred.omero.exception.ServiceException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;

import static javax.swing.JOptionPane.showMessageDialog;


/**
 * Connection dialogue for OMERO.
 */
public class OMEROConnectDialog extends JDialog implements ActionListener, OMEROConnector {

	/** Host field. */
	private final JTextField          hostField     = new JTextField("");
	/** Port field. */
	private final JFormattedTextField portField     = new JFormattedTextField(NumberFormat.getIntegerInstance());
	/** User field. */
	private final JTextField          userField     = new JTextField("");
	/** Password field. */
	private final JPasswordField      passwordField = new JPasswordField("");
	/** Login button. */
	private final JButton             login         = new JButton("Login");
	/** Cancel button. */
	private final JButton             cancel        = new JButton("Cancel");

	/** The client to connect. */
	private Client  client    = new Client();


	/**
	 * Creates a new dialogue to connect the specified client, but does not display it.
	 */
	public OMEROConnectDialog() {
		final int width  = 350;
		final int height = 200;

		final String defaultHost = "omero.igred.fr";
		final int    defaultPort = 4064;

		super.setModal(true);
		super.setTitle("Connection to OMERO");
		super.setSize(width, height);
		super.setMinimumSize(new Dimension(width, height));
		super.setLocationRelativeTo(null); // center the window

		Container cp = super.getContentPane();
		cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));

		JPanel panelInfo = new JPanel();
		panelInfo.setLayout(new BoxLayout(panelInfo, BoxLayout.LINE_AXIS));

		JPanel panelInfo1 = new JPanel();
		panelInfo1.setLayout(new GridLayout(4, 1, 0, 3));

		JPanel panelInfo2 = new JPanel();
		panelInfo2.setLayout(new GridLayout(4, 1, 0, 3));
		panelInfo1.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
		panelInfo2.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

		JLabel hostLabel = new JLabel("Host:");
		panelInfo1.add(hostLabel);
		panelInfo2.add(hostField);
		hostField.setText(defaultHost);

		JLabel portLabel = new JLabel("Port:");
		panelInfo1.add(portLabel);
		panelInfo2.add(portField);
		portField.setValue(defaultPort);

		JLabel userLabel = new JLabel("User:");
		panelInfo1.add(userLabel);
		panelInfo2.add(userField);

		JLabel passwdLabel = new JLabel("Password:");
		panelInfo1.add(passwdLabel);
		panelInfo2.add(passwordField);

		JPanel buttons = new JPanel();
		buttons.add(cancel);
		buttons.add(login);

		panelInfo.add(panelInfo1);
		panelInfo.add(panelInfo2);
		cp.add(panelInfo);
		cp.add(buttons);

		super.getRootPane().setDefaultButton(login);
		super.setVisible(false);
	}


	/**
	 * Displays the login window and connects the client.
	 *
	 * @param c The client.
	 */
	@Override
	public void connect(Client c) {
		this.client = c;
		login.addActionListener(this);
		cancel.addActionListener(this);
		this.setVisible(true);
	}


	/**
	 * Invoked when an action occurs.
	 *
	 * @param e the event to be processed
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source == login) {
			String host     = this.hostField.getText();
			int    port     = (Integer) this.portField.getValue();
			String username = this.userField.getText();
			char[] password = this.passwordField.getPassword();
			try {
				client.connect(host, port, username, password);
				dispose();
			} catch (ServiceException e1) {
				String errorValue = e1.getCause().getMessage();
				String message    = e1.getCause().getMessage();
				if ("Login credentials not valid".equals(errorValue)) {
					message = "Login credentials not valid";
				} else if (String.format("Can't resolve hostname %s", host).equals(errorValue)) {
					message = "Hostname not valid";
				}
				showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			} catch (RuntimeException e1) {
				String errorValue = e1.getMessage();
				String message    = e1.getMessage();
				if ("Obtained null object proxy".equals(errorValue)) {
					message = "Port not valid or no internet access";
				}
				showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			}
		} else if (source == cancel) {
			dispose();
		}
	}

}