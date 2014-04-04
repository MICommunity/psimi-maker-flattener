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
package psidev.psi.mi.filemakers.xmlMaker;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.XMLDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.TitledBorder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import psidev.psi.mi.filemakers.xmlMaker.gui.DictionaryPanel;
import psidev.psi.mi.filemakers.xmlMaker.gui.FlatFileTabbedPanel;
import psidev.psi.mi.filemakers.xmlMaker.gui.XsdTreePanelImpl;
import psidev.psi.mi.filemakers.xmlMaker.mapping.DictionaryMapping;
import psidev.psi.mi.filemakers.xmlMaker.mapping.FlatFileMapping;
import psidev.psi.mi.filemakers.xmlMaker.mapping.Mapping;
import psidev.psi.mi.filemakers.xmlMaker.mapping.TreeMapping;
import psidev.psi.mi.filemakers.xmlMaker.structure.Dictionary;
import psidev.psi.mi.filemakers.xmlMaker.structure.FlatFile;
import psidev.psi.mi.filemakers.xmlMaker.structure.XsdTreeStructImpl;
import psidev.psi.mi.filemakers.xsd.JTextPaneMessageManager;
import psidev.psi.mi.filemakers.xsd.Utils;
import psidev.psi.mi.filemakers.xsd.XsdNode;
//import org.exolab.castor.xml.Marshaller;

/**
 * Main class for the PSI files maker: this class displays a graphical interface
 * that allows to load and display an XML schema as a tree, to load several flat
 * files and display them as a list of columns, to select on the tree to wich
 * nodes should be associated the flat files and to which nodes should be
 * associated the columns, to load dictionnaries and associate them to nodes for
 * which values form the flat file will be replaced by their definition, and to
 * print in an XML file. Some checkings can be made and some warnings are
 * displayed that allow to know if associations respect or not the schema.
 * 
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 * 
 */
public class XmlMakerGui extends JFrame {

	static String mappingFileName = null;

	private static void displayUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1) {
			formatter.printHelp("bin/xmlmaker-gui.bat ",
				options);
		} else {
			formatter.printHelp("sh bin/xmlmaker-gui ",
					options);
		}
	}

	public void load() {
		JFileChooser fc;
		if (Utils.lastVisitedMappingDirectory != null) {
			fc = new JFileChooser(Utils.lastVisitedMappingDirectory);
		} else
			fc = new JFileChooser(".");

		int returnVal = fc.showOpenDialog(new JFrame());
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		load(fc.getSelectedFile());
	}

	private void load(File mappingFile) {
		try {
			FileInputStream fin = new FileInputStream(mappingFile);

			Utils.lastVisitedDirectory = mappingFile.getPath();
			Utils.lastVisitedMappingDirectory = mappingFile.getPath();
			
			JAXBContext jaxbContext = JAXBContext.newInstance(Mapping.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			Mapping mapping = (Mapping) jaxbUnmarshaller.unmarshal(fin);
			load(mapping);
			fin.close();
			
		} catch (FileNotFoundException fe) {
			JOptionPane.showMessageDialog(new JFrame(),
					"Unable to load mapping" + mappingFile.getName(),
					"[PSI makers: PSI maker] load mapping",
					JOptionPane.ERROR_MESSAGE);
		} catch (IOException ioe) {
			JOptionPane.showMessageDialog(new JFrame(),
					"IO error, unable to load mapping",
					"[PSI makers: PSI maker] load mapping",
					JOptionPane.ERROR_MESSAGE);
		} catch (JAXBException jbe) {
			System.out.println("Not a JAXB file, try with old format");
			loadOldFormat(mappingFile);
		}
	}

	private void loadOldFormat(File mappingFile) {
		try {
			FileInputStream fin = new FileInputStream(mappingFile);

			Utils.lastVisitedDirectory = mappingFile.getPath();
			Utils.lastVisitedMappingDirectory = mappingFile.getPath();
			
			// Create XML encoder.
			XMLDecoder xdec = new XMLDecoder(fin);

			/* get mapping */
			Mapping mapping = (Mapping) xdec.readObject();

			load(mapping);

			xdec.close();
			fin.close();
		} catch (FileNotFoundException fe) {
			JOptionPane.showMessageDialog(new JFrame(),
					"Unable to load mapping" + mappingFile.getName(),
					"[PSI makers: PSI maker] load mapping",
					JOptionPane.ERROR_MESSAGE);
		} catch (IOException ioe) {
			JOptionPane.showMessageDialog(new JFrame(),
					"IO error, unable to load mapping",
					"[PSI makers: PSI maker] load mapping",
					JOptionPane.ERROR_MESSAGE);
		} 
	}
	
	private void load(Mapping mapping) {
		try {

			/* flat files */
			flatFileTabbedPanel.flatFileContainer.flatFiles = new ArrayList<FlatFile>();

			for (int i = 0; i < mapping.getFlatFiles().size(); i++) {
				FlatFileMapping ffm = (FlatFileMapping) mapping.getFlatFiles()
						.get(i);
				FlatFile f = new FlatFile();
				if (ffm != null) {
					f.lineSeparator = ffm.getLineSeparator();
					f.firstLineForTitles = ffm.isFisrtLineForTitle();
					f.setSeparators(ffm.getSeparators());

					try {
						URL url = new File(ffm.getFileURL()).toURI().toURL();
						if (url != null)
							f.load(url);
					} catch (FileNotFoundException fe) {
						JOptionPane.showMessageDialog(new JFrame(),
								"Unable to load file" + ffm.getFileURL(),
								"[PSI makers: PSI maker] load flat file",
								JOptionPane.ERROR_MESSAGE);
					}
				}
				treePanel.flatFileTabbedPanel.flatFileContainer.addFlatFile(f);
			}
			treePanel.flatFileTabbedPanel.reload();

			/* dictionaries */
			dictionnaryLists.dictionaries.dictionaries = new ArrayList<Dictionary>();

			for (int i = 0; i < mapping.getDictionaries().size(); i++) {
				DictionaryMapping dm = (DictionaryMapping) mapping
						.getDictionaries().get(i);
				Dictionary d = new Dictionary();

				try {
					URL url = null;
					if (dm.getFileURL() != null)
						url = new File(dm.getFileURL()).toURI().toURL();
					if (url != null)
						d = new Dictionary(url, dm.getSeparator(),
								dm.isCaseSensitive());
					else
						d = new Dictionary();
				} catch (FileNotFoundException fe) {
					JOptionPane.showMessageDialog(new JFrame(),
							"Unable to load file" + dm.getFileURL(),
							"[PSI makers: PSI maker] load dictionnary",
							JOptionPane.ERROR_MESSAGE);
					d = new Dictionary();
				}
				treePanel.dictionaryPanel.dictionaries.addDictionary(d);
			}
			treePanel.dictionaryPanel.reload();

			/* tree */
			TreeMapping treeMapping = mapping.getTree();

			String schemaUrl = treeMapping.getSchemaURL();
			try {
				treePanel.loadSchema(schemaUrl);
				
				((XsdTreeStructImpl) treePanel.xsdTree)
						.loadMapping(treeMapping);

				treePanel.xsdTree.check();

				treePanel.reload();
								
				/* set titles for flat files */
				for (int i = 0; i < mapping.getFlatFiles().size(); i++) {
					try {
						flatFileTabbedPanel.tabbedPane.setTitleAt(i,
								((XsdNode) xsdTree.getAssociatedFlatFiles()
										.get(i)).toString());
					} catch (IndexOutOfBoundsException e) {
						/** TODO: manage exception */
					}
				}

			} catch (FileNotFoundException fe) {
				JOptionPane.showMessageDialog(new JFrame(), "File not found: "
						+ schemaUrl, "[PSI makers]",
						JOptionPane.ERROR_MESSAGE);
			} catch (IOException ioe) {
				JOptionPane.showMessageDialog(new JFrame(),
						"Unable to load file" + ioe.toString(), "[PSI makers]",
						JOptionPane.ERROR_MESSAGE);
			}
		} catch (IOException ioe) {
			JOptionPane.showMessageDialog(new JFrame(),
					"IO error, unable to load mapping",
					"[PSI makers: PSI maker] load mapping",
					JOptionPane.ERROR_MESSAGE);
		} catch (NoSuchElementException nsee) {
			JOptionPane.showMessageDialog(new JFrame(), "Unable to load file",
					"[PSI makers]", JOptionPane.ERROR_MESSAGE);
		}

	}

	public void save() {
		try {
			JFileChooser fc;
			if (Utils.lastVisitedMappingDirectory != null) {
				fc = new JFileChooser(Utils.lastVisitedMappingDirectory);
			} else
				fc = new JFileChooser(".");

			int returnVal = fc.showSaveDialog(new JFrame());
			if (returnVal != JFileChooser.APPROVE_OPTION) {
				return;
			}

			FileOutputStream fos = new FileOutputStream(fc.getSelectedFile());

			// Create XML encoder.
			JAXBContext jaxbContext = JAXBContext.newInstance(Mapping.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
			
			Mapping mapping = new Mapping();
			mapping.setTree(((XsdTreeStructImpl) treePanel.xsdTree)
					.getMapping());

			/* dictionaries */
			for (int i = 0; i < treePanel.dictionaryPanel.dictionaries
					.getDictionaries().size(); i++) {
				mapping.getDictionaries().add(((Dictionary) xsdTree.dictionaries
						.getDictionaries().get(i)).getMapping());
			}

			/* flat files */
			for (int i = 0; i < xsdTree.flatFiles.flatFiles.size(); i++) {		
				mapping.getFlatFiles().add((xsdTree.flatFiles.getFlatFile(i))
						.getMapping());
			}
			
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			 
			jaxbMarshaller.marshal(mapping, fos);
			
			fos.close();
		} catch (FileNotFoundException fe) {
			JOptionPane.showMessageDialog(new JFrame(), "Unable to write file",
					"[PSI makers: PSI maker] save mapping",
					JOptionPane.ERROR_MESSAGE);
		} catch (Exception ex) {
			System.out.println("pb: " + ex);
			StackTraceElement[] s = ex.getStackTrace();
			for (int i = 0; i < s.length; i++) {
				System.out.println(s[i]);
			}
		}
	}

	public XsdTreePanelImpl treePanel;

	public FlatFileTabbedPanel flatFileTabbedPanel;

	public DictionaryPanel dictionnaryLists;

	public XsdTreeStructImpl xsdTree;

	public XmlMakerGui() {
		super("XML Maker");

		getContentPane().setLayout(new BorderLayout());

		xsdTree = new XsdTreeStructImpl();
		JTextPaneMessageManager messageManager = new JTextPaneMessageManager();
		xsdTree.setMessageManager(messageManager);
		treePanel = new XsdTreePanelImpl(xsdTree, messageManager);

		flatFileTabbedPanel = new FlatFileTabbedPanel(xsdTree.flatFiles);
		flatFileTabbedPanel.setBorder(new TitledBorder("Flat files"));

		dictionnaryLists = new DictionaryPanel(xsdTree.dictionaries);
		dictionnaryLists.setBorder(new TitledBorder("Dictionnary"));

		Box associationsPanels = new Box(BoxLayout.Y_AXIS);

		associationsPanels.add(flatFileTabbedPanel);

		associationsPanels.add(dictionnaryLists);
		getContentPane().add(associationsPanels, BorderLayout.WEST);

		getContentPane().add(treePanel, BorderLayout.CENTER);

		treePanel.setTabFileTabbedPanel(flatFileTabbedPanel);
		treePanel.setDictionnaryPanel(dictionnaryLists);
		final CloseView fv = new CloseView();
		addWindowListener(fv);
		setJMenuBar(new XmlMakerMenu());
		// setSize(800, 600);
		this.pack();
		setVisible(true);

		if (mappingFileName != null) {
			load(new File(mappingFileName));
		}

	}

	/**
	 * close properly the frame
	 */
	public class CloseView extends WindowAdapter implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			close();
		}

		public void windowClosing(WindowEvent e) {
			close();
		}

		public void close() {
			XmlMakerGui.this.setVisible(false);
			XmlMakerGui.this.dispose();
			System.exit(0);
		}
	}

	public class clearListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			clear();
		}
	}

	/**
	 * Save the mapping
	 * 
	 * @author arnaud
	 * 
	 */
	public class SaveListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			save();
		}
	}

	/**
	 * the menu bar
	 */
	public class XmlMakerMenu extends JMenuBar {
		public XmlMakerMenu() {
			JMenu file = new JMenu(new String("File"));
			JMenuItem save = new JMenuItem(new String("Save mapping"));
			JMenuItem load = new JMenuItem(new String("Load mapping"));
			JMenuItem clear = new JMenuItem(new String("New mapping"));
			JMenuItem exit = new JMenuItem(new String("Exit"));
			clear.addActionListener(new clearListener());
			load.addActionListener(new LoadListener());
			save.addActionListener(new SaveListener());
			exit.addActionListener(new CloseView());
			file.add(clear);
			file.add(load);
			file.add(save);
			file.add(exit);
			add(file);

			JMenu help = new JMenu(new String("Help"));
			JMenuItem documentation = new JMenuItem("Documentation");
			documentation.addActionListener(new DisplayDocumentationListener());
			JMenuItem about = new JMenuItem("About");
			about.addActionListener(new DisplayAboutListener());
			help.add(documentation);
			help.add(about);
			add(help);
		}

		public class DisplayDocumentationListener implements ActionListener {
			public void actionPerformed(ActionEvent e) {
				JEditorPane editorPane = new JEditorPane();
				editorPane.setEditable(false);
				try {
					editorPane.setContentType("text/html");
					editorPane.setPage("file:doc/documentation.html");

					JScrollPane areaScrollPane = new JScrollPane(editorPane);
					areaScrollPane
							.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
					areaScrollPane.setPreferredSize(new Dimension(600, 650));
					JOptionPane.showMessageDialog(new JFrame(), areaScrollPane);
				} catch (IOException ioe) {
					JOptionPane.showMessageDialog(new JFrame(),
							"Documentation not found.", "Documentation",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		}

		public class DisplayAboutListener implements ActionListener {
			public void actionPerformed(ActionEvent e) {
				JEditorPane editorPane = new JEditorPane();
				editorPane.setEditable(false);
				try {
					editorPane.setPage("file:doc/about.html");
					editorPane.setContentType("text/html");
					JScrollPane areaScrollPane = new JScrollPane(editorPane);
					areaScrollPane
							.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
					areaScrollPane.setPreferredSize(new Dimension(600, 650));
					JOptionPane.showMessageDialog(new JFrame(), areaScrollPane);
				} catch (IOException ioe) {
					JOptionPane.showMessageDialog(new JFrame(),
							"About not found.", "About...",
							JOptionPane.ERROR_MESSAGE);
				}
			}
		}
	}

	public static void main(String[] args) {

		Options options = new Options();
		
		// Load look'n feel
		try {
			UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
		} catch (Exception e){
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception e2) {
				
			}
		}
		
		// create Option objects
		Option helpOpt = new Option("help", "print this message.");
		options.addOption(helpOpt);
		Option option = new Option("mapping", true,
				"the mapping file, created by the GUI application");
		option.setRequired(false);
		options.addOption(option);

		option = new Option("o", true, "xml document");
		option.setRequired(false);
		options.addOption(option);

		option = new Option("flatfiles", true,
				"names of the flat files in the right order, separated by comma");
		option.setRequired(false);
		options.addOption(option);

		option = new Option("dictionaries", true,
				"names of the dictionary files in the right order, separated by comma");
		option.setRequired(false);
		options.addOption(option);

		option = new Option("schema", true, "the XML schema");
		option.setRequired(false);
		options.addOption(option);

		// create the parser
		CommandLineParser parser = new BasicParser();
		CommandLine line = null;
		try {
			// parse the command line arguments
			line = parser.parse(options, args, true);
		} catch (ParseException exp) {
			displayUsage(options);
			System.exit(1);
		}

		if (line.hasOption("help")) {
			displayUsage(options);
			System.exit(0);
		}

		String mappingFileName = line.getOptionValue("mapping");
		String flatFiles = line.getOptionValue("flatfiles");
		String dictionaries = line.getOptionValue("dictionaries");
		String schema = line.getOptionValue("schema");

		// These argument are mandatory.
		mappingFileName = line.getOptionValue("mapping");

		XmlMakerGui xml = new XmlMakerGui();

		if (mappingFileName != null) {
			try {
				FileInputStream fin = new FileInputStream(mappingFileName);
				// Create XML decoder.
				XMLDecoder xdec = new XMLDecoder(fin);
				Mapping mapping = (Mapping) xdec.readObject();

				if (flatFiles != null) {
					String[] files = flatFiles.replaceAll("'", "").split(",");
					for (int j = 0; j < files.length; j++) {
						((FlatFileMapping) mapping.getFlatFiles().get(j)).setFileURL(files[j]);
						System.out.println("flat file " + j + ": " + files[j]);
					}
				}

				if (dictionaries != null) {
					String[] files = dictionaries.replaceAll("'", "")
							.split(",");
					for (int j = 0; j < files.length; j++) {
						((DictionaryMapping) mapping.getDictionaries().get(j))
								.setFileURL(files[j]);
						System.out.println("dictionary " + j + ": " + files[j]);
					}
				}

				if (schema != null) {
					mapping.getTree().setSchemaURL(schema.replaceAll("'", ""));
				}

				xml.load(mapping);
			} catch (FileNotFoundException e) {
				System.err.println("File not found : " + mappingFileName);
			}
		}

	}

	public class LoadListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			load();
		}
	}

	public void clear() {
		flatFileTabbedPanel.flatFileContainer.flatFiles = new ArrayList<FlatFile>();
		flatFileTabbedPanel.flatFileContainer.flatFiles.add(new FlatFile());
		treePanel.flatFileTabbedPanel.reload();

		/* dictionaries */
		dictionnaryLists.dictionaries.dictionaries = new ArrayList<Dictionary>();
		treePanel.dictionaryPanel.reload();

		getContentPane().remove(treePanel);

		xsdTree = new XsdTreeStructImpl();
		JTextPaneMessageManager messageManager = new JTextPaneMessageManager();
		xsdTree.setMessageManager(messageManager);
		treePanel = new XsdTreePanelImpl(xsdTree, messageManager);

		getContentPane().add(treePanel, BorderLayout.CENTER);

		treePanel.setTabFileTabbedPanel(flatFileTabbedPanel);
		treePanel.setDictionnaryPanel(dictionnaryLists);

		treePanel.xsdTree.treeModel.reload();
		treePanel.xsdTree.emptySelectionLists();
		treePanel.reload();
	}

}