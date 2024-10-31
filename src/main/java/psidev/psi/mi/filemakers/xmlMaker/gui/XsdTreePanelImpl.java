/*  Copyright 2004 Arnaud CEOL

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.

 You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package psidev.psi.mi.filemakers.xmlMaker.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exolab.castor.xml.schema.Annotated;
import org.exolab.castor.xml.schema.Annotation;
import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.Documentation;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.Structure;

import psidev.psi.mi.filemakers.xmlMaker.structure.MarshallingObservable;
import psidev.psi.mi.filemakers.xmlMaker.structure.XsdTreeStructImpl;
import psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller.UniprotPanel;
import psidev.psi.mi.filemakers.xsd.AbstractXsdTreePanel;
import psidev.psi.mi.filemakers.xsd.MessageManagerInt;
import psidev.psi.mi.filemakers.xsd.Utils;
import psidev.psi.mi.filemakers.xsd.XsdNode;

/**
 * 
 * This class overrides the abstract class AbstractXslTree to provide a tree
 * representation of an XML schema, with management of marshaling of several flat
 * files to an XML file that respects the schema
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 * 
 */
public class XsdTreePanelImpl extends
		AbstractXsdTreePanel {

	private static final Log log = LogFactory.getLog(XsdTreePanelImpl.class);

	public class GenericAssociationListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			doAssociation();
		}
	}

	public void doAssociation() {
		XsdNode selectedNode = (XsdNode) xsdTree.tree
				.getLastSelectedPathComponent();

		if (selectedNode == null) {
			xsdTree.getMessageManager().sendMessage("no node selected",
					MessageManagerInt.errorMessage);
			return;
		}

		if (flatFileAssociation.isSelected()) {
			associateFlatFile(selectedNode,
					flatFileTabbedPanel.getSelectedIndex());
			return;
		}

		if (duplicableFieldAssociation.isSelected()) {
			String path = flatFileTabbedPanel.getSelectedPath();
			((XsdTreeStructImpl) xsdTree).associateDuplicableField(
					selectedNode, path);
			return;
		}

		if (!xsdTree
				.canHaveValue((XsdNode) xsdTree.tree
						.getLastSelectedPathComponent())) {
			xsdTree.getMessageManager().sendMessage(
					"no value can be associated to this node",
					MessageManagerInt.errorMessage);
			return;
		}

		if (fieldAssociation.isSelected()) {
			associateField(selectedNode);
		} else if (dictionaryAssociation.isSelected())
			associateDictionary(selectedNode);
		else if (defaultAssociation.isSelected())
			associateDefaultValue(selectedNode);
		else if (autoGenerationAssociationButton.isSelected())
			((XsdTreeStructImpl) xsdTree)
					.associateAutoGenerateValue(selectedNode);
	}

	/** use as default value when the user is asked for a default value */
	private String lastValueEntered = "";

	public FlatFileTabbedPanel flatFileTabbedPanel;

	/**
	 * panel for dictionaries
	 */
	public DictionaryPanel dictionaryPanel;

	/**
	 * panel for uniprot ac
	 */
	public UniprotPanel uniprotPanel;

	/**
	 * create a new instance of XslTree The nodes will be automatically
	 * duplicated if the schema specify that more than one element of this type
	 * are mandatory
	 */
	public XsdTreePanelImpl(XsdTreeStructImpl xsdTree, JTextPane messagePane) {
		super(xsdTree);

		messagePane.setEditable(false);

		JScrollPane scrollPane = new JScrollPane(messagePane);
		scrollPane.setMaximumSize(new Dimension(Short.MAX_VALUE, 150));
		scrollPane.setMinimumSize(new Dimension(200, 150));
		scrollPane.setPreferredSize(new Dimension(200, 150));
		scrollPane
				.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane
				.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setBorder(new TitledBorder("Messages"));

		add(scrollPane, BorderLayout.SOUTH);
		add(getButtonPanel(), BorderLayout.EAST);
		MouseListener mouseListener = new TreeMouseAdapter();
		xsdTree.tree.addMouseListener(mouseListener);
	}

	public ButtonGroup associationButtons;

	public JRadioButton fieldAssociation;

	public JRadioButton duplicableFieldAssociation;

	public JRadioButton dictionaryAssociation;

	public JRadioButton defaultAssociation;

	public JRadioButton autoGenerationAssociationButton;

	public JRadioButton flatFileAssociation;

	/**
	 * create a button panel that includes buttons for loading the schema, to
	 * associate a node to a flat file, a cell a default value or to specify
	 * that a value should be automatically generated, to get information about
	 * the node, print the XML file or just have a preview of it.
	 */
	public Box getButtonPanel() {
		// associationLabel.setEditable(false);

		Box buttonsPanel = new Box(BoxLayout.Y_AXIS);

		JPanel treeBox = new JPanel();//
		treeBox.setLayout(new BoxLayout(treeBox, BoxLayout.Y_AXIS));// (BoxLayout.Y_AXIS);

        Box associationBox = new Box(BoxLayout.Y_AXIS);
		associationBox.setBorder(new TitledBorder("Associations"));

		Box nodeBox = new Box(BoxLayout.Y_AXIS);
		nodeBox.setBorder(new TitledBorder("Node"));

		Box outputBox = new Box(BoxLayout.Y_AXIS);
		outputBox.setBorder(new TitledBorder("Output"));

		/* add a button for loading an XML Schema */
		JButton loadFileButton = new JButton("Open File");
		Utils.setDefaultSize(loadFileButton);
		loadFileButton.addActionListener(new LoadSchemaListener());

		JButton loadURLb = new JButton("Open URL");
		Utils.setDefaultSize(loadURLb);
		loadURLb.addActionListener(new LoadURLSchemaListener());

		JButton setIdb = new JButton("Prefix");
		Utils.setDefaultSize(setIdb);
		setIdb.addActionListener(new SetIdListener());

		/* add a button for duplicate a node (in case of lists) */
		JButton duplicateButton = new JButton("Duplicate");
		Utils.setDefaultSize(duplicateButton);
		duplicateButton.addActionListener(new DuplicateListener());

		/* add a button for restoring original choice */
		JButton choiceButton = new JButton("Restore");
		Utils.setDefaultSize(choiceButton);
		choiceButton.addActionListener(new OriginalNodeListener());

		JButton infosButton = new JButton("About");
		Utils.setDefaultSize(infosButton);
		infosButton.addActionListener(new InfosListener());

		JButton checkButton = new JButton("Check");
		Utils.setDefaultSize(checkButton);
		checkButton.addActionListener(new CheckListener());

		JButton previewButton = new JButton("Preview");
		Utils.setDefaultSize(previewButton);
		previewButton.addActionListener(new PreviewListener());

		JButton printButton = new JButton("Make XML");
		Utils.setDefaultSize(printButton);
		printButton.addActionListener(new PrintListener());

		treeBox.add(loadFileButton);
		treeBox.add(loadURLb);
		treeBox.add(setIdb);
		treeBox.add(checkButton);
		treeBox.setBorder(new TitledBorder("Schema"));

		nodeBox.add(duplicateButton);
		nodeBox.add(choiceButton);
		nodeBox.add(infosButton);

		outputBox.add(previewButton);
		outputBox.add(printButton);

		associationButtons = new ButtonGroup();

		fieldAssociation = new JRadioButton("to field");
		duplicableFieldAssociation = new JRadioButton("to duplicable field");
		dictionaryAssociation = new JRadioButton("to dictionary");
		defaultAssociation = new JRadioButton("to default value");
		autoGenerationAssociationButton = new JRadioButton("to automatic value");
		flatFileAssociation = new JRadioButton("to flat file");

		associationButtons.add(flatFileAssociation);
		associationButtons.add(duplicableFieldAssociation);
		associationButtons.add(fieldAssociation);
		associationButtons.add(dictionaryAssociation);
		associationButtons.add(defaultAssociation);
		associationButtons.add(autoGenerationAssociationButton);
		associationButtons.setSelected(flatFileAssociation.getModel(), true);

		JButton genericAssociationButton = new JButton("Associate");
		Utils.setDefaultSize(genericAssociationButton);
		genericAssociationButton.addActionListener(new GenericAssociationListener());

		JButton genericCancelAssociationButton = new JButton("Cancel");
		Utils.setDefaultSize(genericCancelAssociationButton);
		genericCancelAssociationButton
				.addActionListener(new GenericCancelAssociationListener());

		associationBox.add(flatFileAssociation);

		associationBox.add(duplicableFieldAssociation);

		associationBox.add(fieldAssociation);
		JButton editFieldButton = new JButton("validation");
		Utils.setDefaultSize(editFieldButton);
		editFieldButton.addActionListener(new EditFieldAssociationListener());
		associationBox.add(editFieldButton);
		associationBox.add(dictionaryAssociation);
//		associationBox.add(defaultAssociation); // TODO: why is it duplicated?
		associationBox.add(defaultAssociation);
		associationBox.add(autoGenerationAssociationButton);

		associationBox.add(genericAssociationButton);
		associationBox.add(genericCancelAssociationButton);

		buttonsPanel.add(treeBox);
		buttonsPanel.add(associationBox);
		buttonsPanel.add(nodeBox);
		buttonsPanel.add(outputBox);
        return buttonsPanel;
	}

	/**
	 * associate this Panel to a list of dictionaries
	 * 
	 * @param d
	 *            a DictionaryPanel
	 */
	public void setDictionaryPanel(DictionaryPanel d) {
		dictionaryPanel = d;
	}

	public void setUniprotPanel(UniprotPanel p) {
		uniprotPanel = p;
	}

	/**
	 * associate this Panel to a FlatFileTabbedPanel
	 * 
	 * @param panel
	 *            a FlatFileTabbedPanel
	 */
	public void setTabFileTabbedPanel(FlatFileTabbedPanel panel) {
		flatFileTabbedPanel = panel;
		((XsdTreeStructImpl) xsdTree).flatFiles = panel.flatFileContainer;
	}

	public class AssociateDictionaryListPanel extends JPanel {

		int column;

		JList list;

		JRadioButton closedAssociation = new JRadioButton(
				"closed association: null if value is not found in the dictionary");

		JRadioButton openAssociation = new JRadioButton(
				"open association: keep values not found in the dictionary");

		public ButtonGroup associationButtons = new ButtonGroup();

		public int getColumn() {
			return column;
		}

		public AssociateDictionaryListPanel() {
			super();
			setLayout(new BorderLayout());

			list = new JList(dictionaryPanel.getExampleList());
			JScrollPane scrollList = new JScrollPane(list);
			list.addListSelectionListener(new SetColumnListener());
			add(new JLabel(
					"Select the field that contains the definition and press OK:"),
					BorderLayout.NORTH);
			add(scrollList, BorderLayout.CENTER);

			Box box = new Box(BoxLayout.Y_AXIS);
			box.add(closedAssociation);
			box.add(openAssociation);

			associationButtons.add(closedAssociation);
			associationButtons.add(openAssociation);
			associationButtons.setSelected(closedAssociation.getModel(), true);

			add(box, BorderLayout.SOUTH);
		}

		public class SetColumnListener implements ListSelectionListener {
			public void valueChanged(ListSelectionEvent e) {
				column = list.getSelectedIndex();
			}
		}
	}

	/**
	 * used to displayed in a panel an overview of problem found
	 */
	public class CheckListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			XsdNode node = (XsdNode) xsdTree.tree
					.getLastSelectedPathComponent();

			boolean errors;

			if (node == null) {
				node = (XsdNode) xsdTree.treeModel
						.getRoot();
			}

			xsdTree.getMessageManager().sendMessage(
					"[CHECKING] CHECK NODE " + node,
					MessageManagerInt.simpleMessage);

			if (node == null) {
                xsdTree.getMessageManager().sendMessage("no schema loaded",
						MessageManagerInt.errorMessage);
				return;
			} else {
				errors = !xsdTree.check(node);
			}

			if (errors)
				xsdTree.getMessageManager().sendMessage(
						"[CHECKING] failed, errors have been found",
						MessageManagerInt.simpleMessage);
			else
				xsdTree.getMessageManager().sendMessage(
						"[CHECKING] no errors found",
						MessageManagerInt.simpleMessage);
		}
	}

	/**
	 * used to display in a new panel information about the node selected
	 */
	public class InfosListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			XsdNode node = (XsdNode) xsdTree.tree
					.getLastSelectedPathComponent();

			if (node == null) {
				xsdTree.getMessageManager().sendMessage("no node selected",
						MessageManagerInt.errorMessage);
				return;
			}

			JEditorPane editorPane = new JEditorPane();
			editorPane.setEditable(false);
			editorPane.setText(((XsdTreeStructImpl) xsdTree).name + "\n"
					+ xsdTree.getInfos(node));

			JScrollPane scrollPane = new JScrollPane(editorPane);
			scrollPane
					.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

			JFrame frame = new JFrame();
			frame.setSize(400, 300);
			frame.getContentPane().add(scrollPane);
			frame.setVisible(true);
		}
	}

	/** used for loading the schema */
	public class LoadSchemaListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			loadSchema();
        }
	}

	public class LoadURLSchemaListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			loadUrlSchema();
		}
	}

	/** used to duplicate the node selected */
	public class DuplicateListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			XsdNode node = (XsdNode) xsdTree.tree
					.getLastSelectedPathComponent();
			if (node == null) {
				xsdTree.getMessageManager().sendMessage("no node selected",
						MessageManagerInt.errorMessage);
				return;
			}
			xsdTree.duplicateNode(node);
		}
	}


	/**
	 * used to replace the node by its original value, if a choice has been
	 * done.
	 */
	public class OriginalNodeListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			XsdNode node = (XsdNode) xsdTree.tree
					.getLastSelectedPathComponent();
			if (node == null) {
				xsdTree.getMessageManager().sendMessage("no node selected",
						MessageManagerInt.errorMessage);
				return;
			}

			if (!node.transparent) {
				xsdTree.getMessageManager().sendMessage(
						"No choice has been done for this node.",
						MessageManagerInt.errorMessage);
				return;
			}

			XsdNode parent = (XsdNode) node.getParent();

			xsdTree.undoChoice(node);

			xsdTree.treeModel.reload(parent);
		}
	}

	public class TreeMouseAdapter extends MouseAdapter {
		public void mouseClicked(MouseEvent e) {
			XsdNode selectedNode = (XsdNode) xsdTree.tree
					.getLastSelectedPathComponent();
			if (selectedNode == null)
				return;
			String value;
			String text = ((XsdTreeStructImpl) xsdTree)
					.getAssociationInfo(selectedNode);
			if (!(value = ((XsdTreeStructImpl) xsdTree).getValue(selectedNode)).isEmpty()) {
				text += " value: " + value;
			}

			if (e.getClickCount() == 2) {
				doAssociation();
			}

			if (!text.trim().isEmpty()) {
				xsdTree.getMessageManager().sendMessage(
						"[" + selectedNode.getName() + "] " + text.trim(),
						MessageManagerInt.simpleMessage);
			}
		}
	}

	public void associateFlatFile(XsdNode node, int flatFileIndex) {
		if (flatFileTabbedPanel.getFlatFileByIndex(0).fileURL == null) {
			xsdTree.getMessageManager().sendMessage(
					"no flat file has been loaded in selected tab yet",
					MessageManagerInt.errorMessage);
			return;
		}

		int previousFlatFileAssociated = ((XsdTreeStructImpl) xsdTree).associatedFlatFiles
				.indexOf(node);

		try {
			((XsdTreeStructImpl) xsdTree).associatedFlatFiles.set(
					flatFileTabbedPanel.getSelectedIndex(), null);
		} catch (Exception e) {
			/* ok, no association yet */
		}

		/* delete name from ex-associated flat-file */
		if (previousFlatFileAssociated > -1)
			flatFileTabbedPanel.tabbedPane.setTitleAt(
					previousFlatFileAssociated, "");

		((XsdTreeStructImpl) xsdTree).associateFlatFile(node, flatFileIndex);
		flatFileTabbedPanel.tabbedPane.setTitleAt(
				flatFileTabbedPanel.tabbedPane.getSelectedIndex(),
				node.toString());
	}

	public void associateDefaultValue(XsdNode node) {
		String value;
		if (((XsdTreeStructImpl) xsdTree).hasDefaultValue(node)) {
			value = JOptionPane.showInputDialog(new JFrame(),
					"Enter a default value, \n",
                    ((XsdTreeStructImpl) xsdTree).associatedValues
                            .get(node));
		} else {
			value = JOptionPane.showInputDialog(new JFrame(),
					"Enter a default value, \n", lastValueEntered);
		}

		if (value != null) {
			if (((Annotated) (node.getUserObject())).getStructureType() != Structure.ELEMENT
					&& ((Annotated) (node.getUserObject())).getStructureType() != Structure.ATTRIBUTE) {
				JOptionPane
						.showMessageDialog(
								new JFrame(),
								"a value can only be associated with an node of type element or attribute",
								"associating a value",
								JOptionPane.ERROR_MESSAGE);
				return;
			}
			lastValueEntered = value;
			((XsdTreeStructImpl) xsdTree).associateDefaultValue(node, value);
		}
	}

	/**
	 * associate a dictionary to the node selected. Each time a value will be
	 * requested for this node, it will be changed for its replacement value in
	 * target list if it exists
	 *
     */
	public void associateDictionary(XsdNode node) {

		int dictionary = dictionaryPanel.getSelectedDictionnary();

		if (dictionary == -1) { // no selection
			xsdTree.getMessageManager().sendMessage("No dictionary selected",
					MessageManagerInt.errorMessage);
			return;
		}

		if (dictionaryPanel.getExampleList().length == 0) { // no selection
			xsdTree.getMessageManager()
					.sendMessage(
							"This dictionary does not contain any value,"
									+ " maybe the separator has not been set properly.",
							MessageManagerInt.errorMessage);
			return;
		}

		AssociateDictionaryListPanel adp = new AssociateDictionaryListPanel();
		int confirm = JOptionPane.showConfirmDialog(null, adp,
				"[XML maker] load dictionary", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE);

		if (confirm != JOptionPane.OK_OPTION)
			return;

		if (node == null) {
			xsdTree.getMessageManager().sendMessage("No node selected",
					MessageManagerInt.errorMessage);
			return;
		}

		((XsdTreeStructImpl) xsdTree).associateDictionary(node, dictionary,
				adp.getColumn(), adp.closedAssociation.isSelected());
	}

	public void associateField(XsdNode node) {
		String path = flatFileTabbedPanel.getSelectedPath();
		if (!path.matches("([0-9]+\\.)*[0-9]+")) {
			xsdTree.getMessageManager()
					.sendMessage("No field selected " + path,
							MessageManagerInt.errorMessage);
			return;
		}

		if (node == null) {
			xsdTree.getMessageManager().sendMessage("No node selected",
					MessageManagerInt.errorMessage);
			return;
		}

		((XsdTreeStructImpl) xsdTree).associateField(node, path, false);
	}

	public class GenericCancelAssociationListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			XsdNode selectedNode = (XsdNode) xsdTree.tree
					.getLastSelectedPathComponent();

			if (selectedNode == null) {
				xsdTree.getMessageManager().sendMessage("No node selected",
						MessageManagerInt.errorMessage);
				return;
			}

			if (fieldAssociation.isSelected())
				((XsdTreeStructImpl) xsdTree)
						.cancelAssociateField(selectedNode);
			else if (dictionaryAssociation.isSelected())
				((XsdTreeStructImpl) xsdTree)
						.cancelAssociateDictionary(selectedNode);
			else if (defaultAssociation.isSelected())
				((XsdTreeStructImpl) xsdTree).cancelDefaultValue(selectedNode);
			else if (autoGenerationAssociationButton.isSelected())
				((XsdTreeStructImpl) xsdTree).cancelAutogenerate(selectedNode);
			else if (flatFileAssociation.isSelected()) {
				((XsdTreeStructImpl) xsdTree)
						.cancelAssociateFlatFile(selectedNode);

			} else if (duplicableFieldAssociation.isSelected()) {
				((XsdTreeStructImpl) xsdTree)
						.cancelDuplicableField(selectedNode);
            }
		}
	}

	/** used to display the preview */
	public class PreviewListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			if (xsdTree.rootNode == null) {
				xsdTree.getMessageManager().sendMessage("No schema loaded",
						MessageManagerInt.errorMessage);
				return;
			}

			JEditorPane editorPane = new JEditorPane();
			editorPane.setEditable(false);
			try {
				editorPane
						.setText(((XsdTreeStructImpl) xsdTree)
								.previewNode((XsdNode) xsdTree.tree
										.getLastSelectedPathComponent()));
			} catch (NullPointerException noNodeException) {
				/* no node selected */
				editorPane.setText("No preview available.");
			}
			JFrame frame = new JFrame();
			frame.setSize(400, 300);
			frame.getContentPane().add(new JScrollPane(editorPane));
			frame.setVisible(true);
		}
	}

	/** set an id that will be used as prefix for autogenerated values */
	public class SetIdListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String s = JOptionPane.showInputDialog(new JFrame(),
					"Enter a default value, \n",
					((XsdTreeStructImpl) xsdTree).id);

			if (s != null)
				((XsdTreeStructImpl) xsdTree).id = s;
		}
	}

	public void setCellRenderer() {
		try {
			xsdTree.tree
					.setCellRenderer(new XsdTreeRenderer());
		} catch (Exception e) {		
			log.error(xsdTree + ", " + xsdTree.tree, e);
		}
	}

	public class XsdTreeRenderer extends DefaultTreeCellRenderer {
		ImageIcon iconAttribute;
		ImageIcon iconElement;

		public XsdTreeRenderer() {
			iconAttribute = new ImageIcon("images/ic-att.gif");
			iconElement = new ImageIcon("images/ic-elt.gif");
		}

		public Component getTreeCellRendererComponent(JTree tree, Object value,
				boolean sel, boolean expanded, boolean leaf, int row,
				boolean hasFocus) {

			super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
			XsdNode node = (XsdNode) value;
			/* set icon and tooltip */
			switch (((Annotated) node.getUserObject()).getStructureType()) {
			case Structure.GROUP:
				setIcon(null);
				break;
			case Structure.ATTRIBUTE:
				setIcon(iconAttribute);
				setToolTipText("default value: "
						+ ((AttributeDecl) node.getUserObject())
								.getDefaultValue());
				break;
			case Structure.ELEMENT:
				setIcon(iconElement);
				try {
					setToolTipText(((Documentation) ((Annotation) ((ElementDecl) node
							.getUserObject()).getAnnotations().nextElement())
							.getDocumentation().nextElement()).getContent());
				} catch (Exception e) {
					setToolTipText("no documentation");
				}

				break;
			}

			switch (((Annotated) node.getUserObject()).getStructureType()) {
			case Structure.ATTRIBUTE:
				try {
					setText(getText()
							+ " ("
							+ ((AttributeDecl) node.getUserObject())
									.getSimpleType().getName() + ")      ");
				} catch (NullPointerException npe) {
					/* no type defined, assume its text */
					setText(getText() + " (" + "no type" + ")      ");
				}
				break;
			case Structure.ELEMENT:
				String type = null;
				
				if (null != ((ElementDecl) node.getUserObject()).getType()) {
					type = ((ElementDecl) node.getUserObject()).getType()
							.getName();
				}
				
				int max = node.max;
				String text = getText();
				if (null !=  ((ElementDecl) node.getUserObject()).getType() && null != ((ElementDecl) node.getUserObject()).getType() 
						.getBaseType()) {
					text += " ["
							+ ((ElementDecl) node.getUserObject()).getType()
									.getBaseType().getName() + "]";
				}
				
				text += " (";
				if (type != null)
					text += type + ", ";
				text += "max: ";
				if (max == -1)
					text += "unbounded";
				else
					text += max;
				text += ")      ";
				setText(text);
			}

			if (((XsdTreeStructImpl) xsdTree).associatedDuplicableFields
					.containsKey(node)) {
				setText(getText().substring(0, getText().length() - 5) + "*");
			}

			if (((XsdTreeStructImpl) xsdTree).unduplicableNodes.contains(node)) {
				setText(getText().substring(0, getText().length() - 5) + "+");
			}

			if (((XsdTreeStructImpl) xsdTree).associatedOpenDictionary
					.containsKey(node)
					|| ((XsdTreeStructImpl) xsdTree).associatedClosedDictionary
							.containsKey(node)) {
				setText(getText().substring(0, getText().length() - 5)
						+ "(dictionary)");
			}

			setForeground(Color.LIGHT_GRAY);

			XsdNode node2check = node;
			/*
			 * in case of transparent node: look at the first child: transparent
			 * node are only the one used for making a choice, and have always
			 * only one child;
			 */
			if (node.transparent && node.getChildCount() > 0)
				node2check = (XsdNode) node.getChildAt(0);

			if (node2check.transparent)
				setForeground(Color.LIGHT_GRAY);

			if (node2check.isUsed())
				setForeground(Color.BLACK);

			/* show error */
			if (!node2check.isCheckedOk && node.isRequired)
				setForeground(Color.RED);

			if (((XsdTreeStructImpl) xsdTree).isAffected(node2check)
					|| ((XsdTreeStructImpl) xsdTree).hasDefaultValue(node)
					|| ((XsdTreeStructImpl) xsdTree).associatedAutogeneration.contains(node))
				setForeground(Color.BLUE);

			/* show nodes associated to flat files */
			if (((XsdTreeStructImpl) xsdTree).associatedFlatFiles.contains(node2check))
				setForeground(Color.GREEN);

			return this;
		}
	}

	/**
	 * print a xml output for the whole file
	 */
	public class PrintListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			if (xsdTree.rootNode == null) {
				xsdTree.getMessageManager().sendMessage("No schema loaded",
						MessageManagerInt.errorMessage);
				return;
			}

			try {
				String defaultDirectory = Utils.lastVisitedDirectory;
				if (Utils.lastVisitedOutputDirectory != null)
					defaultDirectory = Utils.lastVisitedOutputDirectory;

				JFileChooser fileChooser = new JFileChooser(defaultDirectory);

				int confirm = fileChooser.showSaveDialog(new JFrame());

				if (confirm != JOptionPane.OK_OPTION)
					return;

				Utils.lastVisitedDirectory = fileChooser.getSelectedFile().getPath();
				Utils.lastVisitedOutputDirectory = fileChooser.getSelectedFile().getPath();

				File out = fileChooser.getSelectedFile();

//				MarshallingObserver observer = new MarshallingObserver();
//				observer.setObservable(((XsdTreeStructImpl) xsdTree).observable);
//				((XsdTreeStructImpl) xsdTree).observable.addObserver(observer);
				MarshallingObserver observer = new MarshallingObserver();
				MarshallingObservable observable = ((XsdTreeStructImpl) xsdTree).observable;
				observable.addPropertyChangeListener(observer);
				
				Date DateCurrent = new Date(System.currentTimeMillis());

				xsdTree.getMessageManager().sendMessage(
						"[CREATE XML] start writing XML document: "
								+ DateCurrent,
						MessageManagerInt.simpleMessage);
				((XsdTreeStructImpl) xsdTree).createXml(out);
				DateCurrent = new Date(System.currentTimeMillis());
				xsdTree.getMessageManager().sendMessage(
						"[CREATE XML] finished writing XML document: "
								+ DateCurrent,
						MessageManagerInt.simpleMessage);

			} catch (IOException ex) {
				xsdTree.getMessageManager().sendMessage("unable to write file",
						MessageManagerInt.errorMessage);
			}
		}

	}

	public static class AssociateFieldPanel extends JPanel {
		String filter = "";

		JTextField regexp = new JTextField("");

		JLabel regexpLbl = new JLabel("validate value on regexp: ");

		JCheckBox unduplicableAssociation = new JCheckBox(
				"do not duplicate the node (keep value of first line)");

		public AssociateFieldPanel(String regexp,
				boolean unduplicableAssociation) {
			super();
			setLayout(new BorderLayout());
			if (regexp != null) {
				this.filter = regexp;
				this.regexp = new JTextField(regexp);
			}
			this.unduplicableAssociation.setSelected(unduplicableAssociation);

			Box box = new Box(BoxLayout.Y_AXIS);
			box.add(this.unduplicableAssociation);
			box.add(this.regexpLbl);
			box.add(this.regexp);

			add(box, BorderLayout.SOUTH);
		}
	}

	/**
	 * used to display in a new panel information about the node selected
	 */
	public class EditFieldAssociationListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			XsdNode node = (XsdNode) xsdTree.tree
					.getLastSelectedPathComponent();

			if (node == null) {
				xsdTree.getMessageManager().sendMessage("no node selected",
						MessageManagerInt.errorMessage);
				return;
			}
			if (!((XsdTreeStructImpl) xsdTree).associatedFields
					.containsKey(node)) {
				xsdTree.getMessageManager().sendMessage(
						"this node is not associated to a field",
						MessageManagerInt.errorMessage);
				return;
			}

			/* keep old value to suggest it in the panel */
			boolean unduplicableNode = ((XsdTreeStructImpl) xsdTree).unduplicableNodes
					.contains(node);

			String regexp = ((XsdTreeStructImpl) xsdTree).getRegexp(node);

			AssociateFieldPanel afp = new AssociateFieldPanel(regexp,
                    unduplicableNode);
			int confirm = JOptionPane.showConfirmDialog(null, afp,
					"[XML maker] field association",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

			if (confirm != JOptionPane.OK_OPTION)
				return;

			if (!afp.regexp.getText().trim().isEmpty()) {
				try {
					Pattern.compile(afp.regexp.getText().trim());
				} catch (PatternSyntaxException pse) {
					xsdTree.getMessageManager().sendMessage(
							"invalid regular expression",
							MessageManagerInt.errorMessage);
					return;
				}
			}

			((XsdTreeStructImpl) xsdTree).unduplicableNodes.remove(node);
			if (afp.unduplicableAssociation.isSelected())
				((XsdTreeStructImpl) xsdTree).unduplicableNodes.add(node);

			((XsdTreeStructImpl) xsdTree).validationRegexps.remove(node);
			if (!afp.regexp.getText().trim().isEmpty())
				((XsdTreeStructImpl) xsdTree).associateValidationRegexp(node,
						afp.regexp.getText().trim());

		}
	}

}