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
package psidev.psi.mi.filemakers.xsd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exolab.castor.xml.schema.Annotated;
import org.exolab.castor.xml.schema.AttributeDecl;
import org.exolab.castor.xml.schema.ComplexType;
import org.exolab.castor.xml.schema.ContentModelGroup;
import org.exolab.castor.xml.schema.ElementDecl;
import org.exolab.castor.xml.schema.Group;
import org.exolab.castor.xml.schema.Order;
import org.exolab.castor.xml.schema.Particle;
import org.exolab.castor.xml.schema.Schema;
import org.exolab.castor.xml.schema.Structure;
import org.exolab.castor.xml.schema.XMLType;
import org.exolab.castor.xml.schema.reader.SchemaReader;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This Class creates and manages a tree representation of a XML schema
 *
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 *
 */
public abstract class AbstractXsdTreeStruct extends Observable {

	private static final Log log = LogFactory.getLog(AbstractXsdTreeStruct.class);

	private MessageManagerInt messageManager = new NullMessageManager();

	/**
	 * XML attributes
	 */
	public static String SCHEMA_LANGUAGE = "http://java.sun.com/xml/jaxp/properties/schemaLanguage";

	/**
	 * XML attributes
	 */
	public static String XML_SCHEMA = "http://www.w3.org/2001/XMLSchema-instance";

	/**
	 * XML attributes
	 */
	public static String SCHEMA_SOURCE = "http://java.sun.com/xml/jaxp/properties/schemaSource";

	public void loadSchema(String schema) throws FileNotFoundException, IOException {
		if (schema.contains("http:")) {
			loadSchema(new URL(schema));
		} else {
			loadSchema(new File(schema).toURI().toURL());
		}
	}

	public void loadSchema(URL schemaUrl) throws FileNotFoundException, IOException {
		emptySelectionLists();

		this.schemaURL = schemaUrl;

		InputStream in = schemaURL.openStream();

		/* test: get keyz/keyref */
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder parser = factory.newDocumentBuilder();
			Document d = parser.parse(in);
			for (int i = 0; i < d.getChildNodes().getLength(); i++) {
				getKeys(d.getChildNodes().item(i));
			}

		} catch (ParserConfigurationException e) {
			JOptionPane.showMessageDialog(new JFrame(), "Cannot load the schema.", "ERROR", JOptionPane.ERROR_MESSAGE);
			log.error(e);
		} catch (SAXException e) {
			JOptionPane.showMessageDialog(new JFrame(), "Cannot load the schema.", "ERROR", JOptionPane.ERROR_MESSAGE);
			log.error(e);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(new JFrame(), "Cannot load the schema.", "ERROR", JOptionPane.ERROR_MESSAGE);
			log.error(e);
		}

		in = schemaURL.openStream();
		SchemaReader reader = new SchemaReader(new InputSource(in));
		schema = reader.read();
		createTree();
		Utils.lastVisitedDirectory = schemaURL.getPath();
		Utils.lastVisitedSchemaDirectory = schemaURL.getPath();
	}

	private void getKeys(Node node) {
		try {
			for (int i = 0; i < node.getChildNodes().getLength(); i++) {
				if (node.getChildNodes().item(i).getNodeName().indexOf("keyref") > 0) {
					keyRefs.add(node.getChildNodes().item(i));
				} else if (node.getChildNodes().item(i).getNodeName().indexOf("key") > 0) {
					keyz.add(node.getChildNodes().item(i));
				}
				getKeys(node.getChildNodes().item(i));
			}

		} catch (Exception e) {
			log.error(e);
		}
	}

	protected ArrayList<Node> keyz = new ArrayList<Node>();

	protected ArrayList<Node> keyRefs = new ArrayList<Node>();

	protected void print(Node node) {
		try {
			log.debug("attributes:");
			if (node.hasAttributes()) {
				for (int i = 0; i < node.getAttributes().getLength(); i++) {
					log.debug(node.getAttributes().item(i).getNodeName() + "; "
							+ node.getAttributes().item(i).getNodeValue());
				}
			}

			log.debug("chidren:");
			for (int i = 0; i < node.getChildNodes().getLength(); i++) {
				log.debug(node.getChildNodes().item(i).getNodeName() + "; "
						+ node.getChildNodes().item(i).getNodeValue());
				print(node.getChildNodes().item(i));
			}

		} catch (Exception e) {
			log.error(e);
		}
	}

	/**
	 * DefaultTreeSelectionModel tree
	 */
	public JTree tree;

	/**
	 */
	public XmlErrorHandler xmlErrorHandler = new XmlErrorHandler();

	/**
	 * the root node of the tree
	 */
	public XsdNode rootNode;

	public DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);

	/**
	 * if this variable is setted to true (by the constructor) while expanding a
	 * node, the tree will automaticaly create the minimum amount of nodes required
	 * according by the schema
	 */
	public boolean autoDuplicate;

	/**
	 * this hashmap keep trace of choices made by user when expanding the tree. It
	 * is usefull for example in case of saving/loading . It associate a path
	 * (String) to a name.
	 */
	public ArrayList<String> expendChoices = new ArrayList<String>();

	/**
	 * if this variable is set to true (by the constructor) while expanding a node
	 * that discribe a choice, the tree will give to the user the possibility to
	 * choose what element to expand. Else all possibility is displayed
	 */
	public boolean manageChoices;

	/**
	 * the schema
	 */
	public Schema schema;

	/**
	 * Returns an instance of <code>AbstractXslTree</code>
	 *
	 * @param autoduplicate
	 *            indicates that new nodes will be automaticly created according to
	 *            the minimum defined in the schema (minOccurs)
	 */
	public AbstractXsdTreeStruct(boolean autoduplicate, boolean manageChoices) {
		this.autoDuplicate = autoduplicate;
		this.manageChoices = manageChoices;
		this.tree = new JTree(treeModel);
	}

	public AbstractXsdTreeStruct() {
		this.autoDuplicate = true;
		this.manageChoices = true;
		tree = new JTree(treeModel);
	}

	/**
	 * the xsl file describing the schema
	 *
	 */
	// public File schemaFile;
	public URL schemaURL;

	protected HashMap<String, String> refType2referedType = new HashMap<String, String>();

	/**
	 * this method should reinitialize every variable makin reference to the actual
	 * tree, such as any <code>List</code> used to make associations to externals
	 * objects.
	 */
	public abstract void emptySelectionLists();

	/**
	 * create the tree according to the schema loaded. The root node is displayed
	 *
	 */
	public void createTree() {
		Enumeration<ElementDecl> elts = schema.getElementDecls();
		ElementDecl elt = elts.nextElement();
		XsdNode node = new XsdNode(elt);
		/* rootNode is mandatory */
		rootNode = node;
		rootNode.use();
		treeModel = new DefaultTreeModel(rootNode);
		tree.setModel(treeModel);
	}

	/**
	 * get the number of children with given name for a node used for example to
	 * know if the maximum number of node of that type is already reach or to create
	 * the minimum number of node of that type required according to the schema
	 */
	public int getChildrenCount(XsdNode node, String childrenName) {
		int count = 0;
		for (Iterator<XsdNode> it = getChildren(node); it.hasNext();) {
			XsdNode child = it.next();
			if (child.toString() == childrenName) {
				count++;
			}
		}
		return count;
	}

	/**
	 * an enumeration of all children add recursively to this enumeration the
	 * childrens of childrens if child is a choice
	 *
	 * @result a list of <code>String</code>
	 */
	public ArrayList<Annotated> getChoices(Group g) {
		ArrayList<Annotated> choices = new ArrayList<Annotated>();
		Enumeration<Annotated> childrens = g.enumerate();
		while (childrens.hasMoreElements()) {
			Annotated child = childrens.nextElement();
			choices.add(child);
		}
		return choices;
	}

	/**
	 * type for references used for going deeper in case of normalized document
	 */
	public final static String refType = "refType";

	public final static String refAttribute = "ref";

	public final static String refId = "id";

	/**
	 * retrun true if an element of this type is a reference to another element.
	 */
	public boolean isXsRefPath(Node node) {
		if (node.getNodeName() == null) {
			return false;
		}
		return refType2referedType.keySet().contains(getDocumentXpath(node));
	}

	/**
	 * retrun true if an element of this type is a reference to another element.
	 */
	public boolean isRefType(String nodeName) {
		return nodeName.equals(refType);
	}

	/**
	 * describes the node with informations such as its name or its XML type. Other
	 * informations should probably have to be added when extending this class
	 *
	 * @param node
	 *            the node on which informations are required
	 * @return a string describing informations about the node
	 */
	public String getInfos(XsdNode node) {
		String infos = "";
		/* name */
		infos += "name: " + node.toString() + "\n";
		/* XML type */
		infos += "Xml type: ";
		switch (((Annotated) node.getUserObject()).getStructureType()) {
		case Structure.ATTRIBUTE:
			infos += "attribute\n";
			infos += "This node is ";
			if (!((AttributeDecl) node.getUserObject()).isRequired()) {
				infos += "not ";
			}
			infos += "required\n";
			break;
		case Structure.ELEMENT:
			infos += "element\n";
			infos += "minimum occurences: " + node.min + "\n";
			infos += "maximum occurences: ";
			if (node.max >= 0) {
				infos += node.max + "\n";
			} else {
				infos += "unbounded\n\n";
			}
			infos += "This node is ";
			if (((ElementDecl) node.getUserObject()).getMinOccurs() == 0) {
				infos += "not ";
			}
			infos += "required\n";
			break;
		default:
			infos += "not XML\n";
		}
		if (node.transparent) {
			infos += "TRANSPARENT";
		}
		infos += "\nchecked ok: " + node.isCheckedOk;
		return infos;
	}

	/**
	 * return a understandable <code>String</code> describing the path of a node, on
	 * type: "grandparentNode.ParentNode.Node"
	 *
	 * @param path
	 *            the path to describe
	 * @return an understandable <code>String</code> to describe the node
	 */
	public String printPath(TreeNode[] path) {
		String value = "";
		for (int i = 0; i < path.length; i++) {
			if (((Annotated) ((XsdNode) path[i]).getUserObject()).getStructureType() != Structure.GROUP) {
				value += "[" + ((XsdNode) path[i]).toString() + "]";
			}
		}
		return value;
	}

	/**
	 * a node ca have a value if it is an attribute or if it has a simple content
	 *
	 * @param node
	 *            a node
	 * @return true if the node can have a value
	 */
	public boolean canHaveValue(XsdNode node) {
		if (((Annotated) node.getUserObject()).getStructureType() == Structure.ATTRIBUTE) {
			return true;
		}
		if (!((ElementDecl) node.getUserObject()).getType().isComplexType()) {
			return true;
		}
		if (((ElementDecl) node.getUserObject()).getType().getBaseType() != null) {
			if (!((ElementDecl) node.getUserObject()).getType().getBaseType().isComplexType()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * check for errors on this node (lack of associations...) a return an array of
	 * understandable Strings describing the errors
	 *
	 * @param node
	 *            the node to check
	 * @return an array of Strings describing the errors found
	 */
	public abstract boolean check(XsdNode node);

	public boolean check() {
		return check(rootNode);
	}

	/**
	 * follow indexes in the tree and return the node found
	 *
	 * @param indexes
	 *            a string describing the indexes, e.g. 1.1.2.1
	 */
	public XsdNode getNodeByPath(String indexes) {

		boolean treeChanged = false;

		String nextIndexes = indexes;

		XsdNode currentNode = null; // rootNode;

		int step = 0;

		try {
			int index = -1;
			while (nextIndexes.indexOf(".") >= 0) {
				step++;
				index = Integer.parseInt(nextIndexes.substring(0, nextIndexes.indexOf(".")));
				nextIndexes = nextIndexes.substring(nextIndexes.indexOf(".") + 1);
				if (currentNode == null) {
					currentNode = rootNode;
				} else {
					/**
					 * TODO : check if it really can be commented
					 */
					if (false == currentNode.isExtended) {
						log.error("EXTEND: " + currentNode + " / " + index + ", step=" + step);
						extendPath(currentNode);
						treeChanged = true;
					}
					currentNode = (XsdNode) currentNode.getChildAt(index);
				}
				if (false == currentNode.isExtended) {
					extendPath(currentNode);
					treeChanged = true;
				}
			}
			index = Integer.parseInt(nextIndexes);
			/* no more "." in the path just a last index */
			if (currentNode == null) {
				return rootNode;
			}

			if (!currentNode.isExtended) {
				extendPath(currentNode);
				treeChanged = true;
			}
			try {
				currentNode = (XsdNode) currentNode.getChildAt(index);
			} catch (java.lang.ArrayIndexOutOfBoundsException aiobe) {
				/*
				 * to keep compatibility: if mapping done without auto-duplication of nodes and
				 * applied without this option, an exception will be raised if the node has not
				 * been manually duplicated. In this case try to duplicate "upper" node.
				 */
				/* get upper node's index */
				duplicateNode((XsdNode) currentNode.getChildAt(index - 1));
				currentNode = (XsdNode) currentNode.getChildAt(index);
			}

			if (treeChanged) {
				check((XsdNode) treeModel.getRoot());
				treeModel.reload((XsdNode) treeModel.getRoot());
			}

			return currentNode;

			/**
			 * TODO: add check?
			 */
		} catch (ArrayIndexOutOfBoundsException aoobe) {
			log.debug("Path not found: " + indexes + " / " + nextIndexes);
			return null;
		}
	}

	/**
	 * return a String describing the indexes to follow to go from root node to
	 * target node
	 *
	 * @param node
	 * @return
	 */
	public String getPathForNode(XsdNode node) {
		TreeNode nodesPath[] = node.getPath();
		String path = "0";
		for (int i = 0; i < nodesPath.length - 1; i++) {
			path += "." + nodesPath[i].getIndex(nodesPath[i + 1]);
		}
		return path;
	}

	public void reload() {
		treeModel.reload();
	}

	/**
	 * @return Returns the autoDuplicate.
	 *
	 */
	public boolean isAutoDuplicate() {
		return autoDuplicate;
	}

	/**
	 * @return Returns the expendChoices.
	 *
	 */
	public ArrayList<String> getExpandChoices() {
		return expendChoices;
	}

	/**
	 * @param expendChoices
	 *            The expendChoices to set.
	 *
	 */
	public void setExpendChoices(ArrayList<String> expendChoices) {
		this.expendChoices = expendChoices;
	}

	/**
	 * @return Returns the manageChoices.
	 *
	 */
	public boolean isManageChoices() {
		return manageChoices;
	}

	/**
	 * @return Returns the rootNode.
	 */
	public XsdNode getRootNode() {
		return rootNode;
	}

	/**
	 * @param rootNode
	 *            The rootNode to set.
	 */
	public void setRootNode(XsdNode rootNode) {
		this.rootNode = rootNode;
	}

	/**
	 * @return Returns the schema.
	 */
	public Schema getSchema() {
		return schema;
	}

	public void setSchema(Schema schema) {
		this.schema = schema;
	}

	/**
	 * @return Returns the tree.
	 *
	 */
	public JTree getTree() {
		return tree;
	}

	/**
	 * @param tree
	 *            The tree to set.
	 */
	public void setTree(JTree tree) {
		this.tree = tree;
	}

	/**
	 * @return Returns the treeModel.
	 */
	public DefaultTreeModel getTreeModel() {
		return treeModel;
	}

	/**
	 * @param treeModel
	 *            The treeModel to set.
	 *
	 */
	public void setTreeModel(DefaultTreeModel treeModel) {
		this.treeModel = treeModel;
	}

	/**
	 * @param autoDuplicate
	 *            The autoDuplicate to set.
	 */
	public void setAutoDuplicate(boolean autoDuplicate) {
		this.autoDuplicate = autoDuplicate;
	}

	/**
	 * @param manageChoices
	 *            The manageChoices to set.
	 */
	public void setManageChoices(boolean manageChoices) {
		this.manageChoices = manageChoices;
	}

	public void undoChoice(XsdNode node) {
		String path = getPathForNode(node);
		int i = 0;
		while (i < this.expendChoices.size()) {
			String current_path = this.expendChoices.get(i);
			if (current_path.startsWith(path)) {
				/* remove path and choice, ie two elements */
				this.expendChoices.remove(i);
				this.expendChoices.remove(i);
			} else {
				i = i + 2;
			}
		}
		node.isExtended = false;
		node.transparent = false;

		node.removeAllChildren();
	}

	public void redoChoice(String path, String choice) {
		try {
			XsdNode currentNode = rootNode;
			/* never a choice on rootNode */
			String nextIndexes = path.substring(path.indexOf(".") + 1);
			int index = 0;
			Annotated annotated = (Annotated) (currentNode.getUserObject());
			/* for each element on the path */
			while (nextIndexes.length() > 0 && nextIndexes != "-1") {
				/* if choice do it */
				annotated = (Annotated) (currentNode.getUserObject());
				/* if not extended, do it */
				if (!currentNode.isExtended) {
					extendPath(currentNode);
				} else {
					if (nextIndexes.indexOf(".") >= 0) {
						index = Integer.parseInt(nextIndexes.substring(0, nextIndexes.indexOf(".")));
						nextIndexes = nextIndexes.substring(nextIndexes.indexOf(".") + 1);
						try {
							currentNode = (XsdNode) currentNode.getChildAt(index);
						} catch (java.lang.ArrayIndexOutOfBoundsException aiobe) {
							/*
							 * to keep compatibility: if mapping done without auto-duplication of nodes and
							 * applied without this option, an exception will be raised if the node has not
							 * been manually duplicated. In this case try to duplicate "upper" node.
							 */
							/* get upper node's index */
							log.error("Index: " + path + " -> " + choice + " , " +index );
							duplicateNode((XsdNode) currentNode.getChildAt(index - 1));
							currentNode = (XsdNode) currentNode.getChildAt(index);
							aiobe.printStackTrace();
						}
					} else {

						index = Integer.parseInt(nextIndexes);
						nextIndexes = "-1";
						try {
							currentNode = (XsdNode) currentNode.getChildAt(index);
						} catch (ArrayIndexOutOfBoundsException e) {
							e.printStackTrace();
							return;
						}
					}
				}
			}

			/* add choice */
			annotated = (Annotated) (currentNode.getUserObject());
			Group g = (Group) annotated;
			try {
				if (g.getOrder().getType() == Order.CHOICE && manageChoices) {
					XsdNode parent = (XsdNode) currentNode.getParent();
					int position = parent.getIndex(currentNode);
					ArrayList<Annotated> choices = getChoices(g);
					ArrayList<String> possibilities = new ArrayList<String>();
					for (int i = 0; i < choices.size(); i++) {
						try {
							possibilities.add(((ElementDecl) choices.get(i)).getName());
						} catch (ClassCastException e) {
							/* a group: give an overview */
							possibilities.add(XsdNode.choiceToString((Group) choices.get(i)));
						}
					}

					Annotated chosenChoice = choices.get(possibilities.indexOf(choice));
                    // If the selected choice is a group of elements, we need to create a new node for each of them.
                    // Otherwise, we create a new node for the element chosen.
                    // This code to handle a choice with group of elements differently was added because of the
                    // complexType 'bibref', which has a choice with two sequence nodes (one with two element nodes,
                    // the other one with one element node).
                    // Without this code, mapping set for the 'bibref' type was not being saved.
					if (chosenChoice.getStructureType() == Structure.GROUP) {
						Enumeration<ElementDecl> elements = ((Group) chosenChoice).enumerate();
						while (elements.hasMoreElements()) {
							ElementDecl elementDecl = elements.nextElement();
							XsdNode newNode = newNodeFromElement(currentNode, elementDecl);
							addNewNodeToCurrentNode(currentNode, newNode);
						}
					} else {
						XsdNode newNode = newNodeFromChoice(currentNode, g, chosenChoice);
						addNewNodeToCurrentNode(currentNode, newNode);
					}
				}
			} catch (StringIndexOutOfBoundsException e) {
				e.printStackTrace();
				return;
			}

		} catch (ArrayIndexOutOfBoundsException aioobe) {
			log.error("Restore choice, path not found: " + path);
			throw aioobe;
		}
		check((XsdNode) treeModel.getRoot());
		treeModel.reload((XsdNode) treeModel.getRoot());

		expendChoices.add(path);
		expendChoices.add(choice);
	}

	/**
	 * @return Returns the schemaURI.
	 */
	public URL getSchemaURL() {
		return schemaURL;
	}

	/**
	 * @param schemaURI
	 *            The schemaURI to set.
	 */
	public void setSchemaURL(URL schemaURI) {
		this.schemaURL = schemaURI;
	}

	/**
	 * @return Returns the sCHEMA_LANGUAGE.
	 */
	public static String getSCHEMA_LANGUAGE() {
		return SCHEMA_LANGUAGE;
	}

	/**
	 * @param schema_language
	 *            The sCHEMA_LANGUAGE to set.
	 */
	public static void setSCHEMA_LANGUAGE(String schema_language) {
		SCHEMA_LANGUAGE = schema_language;
	}

	/**
	 * @return Returns the sCHEMA_SOURCE.
	 */
	public static String getSCHEMA_SOURCE() {
		return SCHEMA_SOURCE;
	}

	/**
	 * @param schema_source
	 *            The sCHEMA_SOURCE to set.
	 */
	public static void setSCHEMA_SOURCE(String schema_source) {
		SCHEMA_SOURCE = schema_source;
	}

	/**
	 * @return Returns the xML_SCHEMA.
	 */
	public static String getXML_SCHEMA() {
		return XML_SCHEMA;
	}

	/**
	 * @param xml_schema
	 *            The xML_SCHEMA to set.
	 */
	public static void setXML_SCHEMA(String xml_schema) {
		XML_SCHEMA = xml_schema;
	}

	/**
	 * @return Returns the xmlErrorHandler.
	 */
	public XmlErrorHandler getXmlErrorHandler() {
		return xmlErrorHandler;
	}

	/**
	 * @param xmlErrorHandler
	 *            The xmlErrorHandler to set.
	 */
	public void setXmlErrorHandler(XmlErrorHandler xmlErrorHandler) {
		this.xmlErrorHandler = xmlErrorHandler;
	}

	/**
	 * check elements and attributes for the structure contained in this node create
	 * nodes and add'em to the tree
	 */
	public void extendPath(XsdNode node) {
		if (node.isExtended) {
			return;
		}

		Annotated annotated = (Annotated) (node.getUserObject());
		String path = getPathForNode(node);

		switch (annotated.getStructureType()) {
		case Structure.ATTRIBUTE:
			break;

		case Structure.GROUP:
			/* if it's a complex type, look inside */
			Group g = (Group) annotated;

			XsdNode parent = (XsdNode) node.getParent();

			/* position is important when adding new node */
			// int position = parent.getIndex(node);
			/*
			 * if a sequence: add all childs, if a choice, ask user
			 */
			if (g.getOrder().getType() == Order.CHOICE && manageChoices) {
				XsdNode newNode;

				String choice = expendChoices.get(expendChoices.indexOf(path) + 1);

				ArrayList<Annotated> choices = getChoices(g);
				ArrayList<String> possibilities = new ArrayList<String>();
				for (int i = 0; i < choices.size(); i++) {
					try {
						possibilities.add(((ElementDecl) choices.get(i)).getName());
					} catch (ClassCastException e) {
						/* a group: give an overview */
						possibilities.add(XsdNode.choiceToString((Group) choices.get(i)));
					}
				}

				newNode = new XsdNode(choices.get(possibilities.indexOf(choice)));
				newNode.isRequired = node.isRequired;

				/**
				 * If the max occurs is specified, transfer it to the child.
				 */
				/**
				 * TODO: check if we should verify that max/min is indedd specified for the
				 * group.
				 */

				newNode.min = g.getMinOccurs(); // node.min;
				newNode.max = g.getMaxOccurs(); // node.max;
				newNode.originalParent = node;
				node.transparent = true;
				node.add(newNode);
				extendPath(newNode);
				// if (((Annotated) newNode.getUserObject()).getStructureType() !=
				// Structure.GROUP) {
				// extendPath(newNode);
				// } else if (((Group) newNode.getUserObject()).getOrder().getType() !=
				// Order.CHOICE) {
				// extendPath(newNode);
				// }

			} else { /* sequence */

				if (g.getOrder().getType() == Order.CHOICE) {
					expendChoices.add(path);
				}

				int parentIndex = parent.getIndex(node);
				((XsdNode) node.getParent()).remove(parent.getIndex(node));

				Enumeration<Annotated> elts = g.enumerate();
				boolean firstElement = true;
				while (elts.hasMoreElements()) {
					Annotated element = elts.nextElement();
					XsdNode newNode = new XsdNode(element);

					/*
					 * In some cases the minOccurs argument is on the sequence element instead of
					 * the chidrens themseves. In those cases propagat it from sequence element to
					 * the children elements
					 */
					if (node.min == 0) {
						newNode.min = 0;
						newNode.isRequired = false;
					}

					if (firstElement) {
						parent.insert(newNode, parentIndex);
						firstElement = false;
					} else {
						parent.add(newNode);
					}

					if (((Annotated) newNode.getUserObject()).getStructureType() != Structure.GROUP) {
						extendPath(newNode);
					} else if (((Group) newNode.getUserObject()).getOrder().getType() != Order.CHOICE) {
						extendPath(newNode);
					}

					if (autoDuplicate) {
						if (element.getStructureType() == Structure.ELEMENT) {
							for (int i = 1; i < ((ElementDecl) element).getMinOccurs(); i++) {

								newNode = new XsdNode(element);
								parent.add(newNode);

								if (((Annotated) newNode.getUserObject()).getStructureType() != Structure.GROUP) {
									extendPath(newNode);
								} else if (((Group) newNode.getUserObject()).getOrder().getType() != Order.CHOICE) {
									extendPath(newNode);
								}
							}
						}
					}
				}
			}
			// check((XsdNode) treeModel.getRoot());
			treeModel.reload(parent);
			break;
		case Structure.ELEMENT:
			/* if it's a complex type, look inside */
			XMLType type = ((ElementDecl) annotated).getType();
			/* no type : send message */
			if (type == null) {
				log.warn("WARNING: no type declaration for element " + node.toString());
				return;
			}
			if (type.isSimpleType()) {
				return;
			}
			Enumeration<AttributeDecl> attributes = ((ComplexType) type).getAttributeDecls();
			while (attributes.hasMoreElements()) {
				node.add(new XsdNode(attributes.nextElement()));
			}

			Enumeration<Particle> elements = getElementsFromType(type);
			while (elements.hasMoreElements()) {
				Particle ptc = elements.nextElement();
				XsdNode child = new XsdNode((Annotated) ptc);
				node.add(child);
				if (ptc.getStructureType() != Structure.GROUP) {
					extendPath(child);
				} else if (((Group) child.getUserObject()).getOrder().getType() != Order.CHOICE) {
					extendPath(child);
				}
			}

			/* do not forget the base type */
			try {
				attributes = ((ComplexType) type.getBaseType()).getAttributeDecls();
				while (attributes.hasMoreElements()) {
					node.add(new XsdNode(attributes.nextElement()));
				}
				elements = ((ComplexType) type.getBaseType()).enumerate();
				while (elements.hasMoreElements()) {
					Particle ptc = elements.nextElement();
					XsdNode child = new XsdNode((Annotated) ptc);
					node.add(child);
					if (ptc.getStructureType() != Structure.GROUP) {
						extendPath(child);
					} else if (((Group) child.getUserObject()).getOrder().getType() != Order.CHOICE) {
						extendPath(child);
					}
				}
			} catch (Exception e) {
				/* no base type */
			}

			// check((XsdNode) treeModel.getRoot());
			// treeModel.reload(node);
			break;
		default:
			log.debug("default type: " + annotated.getStructureType());
		}

		node.isExtended = true;

	} // extendPath

	public MessageManagerInt getMessageManager() {
		return messageManager;
	}

	public void setMessageManager(MessageManagerInt messageManager) {
		this.messageManager = messageManager;
	}

	/**
	 * create a copy of the node and add it to the parent of this node if the node
	 * is not duplicable or if the maximum amount of this type of node according to
	 * the schema has been reached, do nothing
	 *
	 * @param node
	 *            the node to duplicate
	 */
	public void duplicateNode(XsdNode node) {
		if (!node.isDuplicable()) {
			return;
		}
		if (node.max == getChildrenCount((XsdNode) node.getParent(), node.toString())) {
			return;
		}

		XsdNode child = node.createBrother();

		XsdNode parentNode = (XsdNode) node.getParent();

		/* add it to the end for not corrupting maping */
		treeModel.insertNodeInto(child, parentNode, parentNode.getChildCount());

		/* be sure that this node is not already used */
		child.init();
		if (((Annotated) child.getUserObject()).getStructureType() != Structure.GROUP) {
			extendPath(child);
		} else if (((Group) child.getUserObject()).getOrder().getType() != Order.CHOICE) {
			extendPath(child);
		}

		expendChoices.add(node.getPath2String());
		expendChoices.add(null);

		check((XsdNode) treeModel.getRoot());
		treeModel.reload((XsdNode) treeModel.getRoot());

	}

	/**
	 * the name of an element in the schema is contained in the attribute 'name'
	 *
	 * @param node
	 * @return
	 */
	private String getName(Node node) {
		if (node.hasAttributes() == false) {
			return null;
		}

		for (int i = 0; i < node.getAttributes().getLength(); i++) {
			if (node.getAttributes().item(i).getNodeName().equals("name")) {
				return node.getAttributes().item(i).getNodeValue();
			}
		}
		return null;
	}

	protected String getSchemaXpath(Node node) {
		String xpath = "";
		if (node.getParentNode() != null) {
			xpath = getSchemaXpath(node.getParentNode());
		}
		String name = getName(node);

		if (name != null && xpath != null && xpath.equals("") == false) {
			xpath += "/";
		}

		if (name != null) {
			xpath += name;
		}
		return xpath;
	}

	protected String getDocumentXpath(Node node) {
		String xpath = "";
		if (node.getParentNode() != null) {
			xpath = getDocumentXpath(node.getParentNode());
			String name = node.getNodeName();

			if (name != null && xpath != null && xpath.equals("") == false) {
				xpath += "/";
			}

			if (name != null) {
				xpath += name;
			}
		}
		return xpath;
	}

	/**
	 * return an enumeration of all children of given node if one of the chidren is
	 * transparent, add the child'children instead of the child itself
	 *
	 * @return
	 */
	protected Iterator<XsdNode> getChildren(XsdNode node) {
		Enumeration<XsdNode> enumeration = node.children();
		ArrayList<XsdNode> children = new ArrayList<XsdNode>();
		while (enumeration.hasMoreElements()) {
			XsdNode child = enumeration.nextElement();
			if (child.transparent) {
				Iterator<XsdNode> littleChildren = getChildren(child);
				while (littleChildren.hasNext()) {
					children.add(littleChildren.next());
				}
			} else {
				children.add(child);
			}
		}

		return children.iterator();
	}

	private XsdNode newNodeFromElement(XsdNode currentNode, ElementDecl elementDecl) {
		XsdNode newNode = new XsdNode(elementDecl);
		newNode.isRequired = currentNode.isRequired;
		newNode.min = elementDecl.getMinOccurs();
		newNode.max = elementDecl.getMaxOccurs();
		return newNode;
	}

	private XsdNode newNodeFromChoice(XsdNode currentNode, Group g, Annotated choice) {
		XsdNode newNode = new XsdNode(choice);
		newNode.isRequired = currentNode.isRequired;
		// If the max occurs is specified, transfer it to the child.
		newNode.min = g.getMinOccurs();
		newNode.max = g.getMaxOccurs();
		return newNode;
	}

	private void addNewNodeToCurrentNode(XsdNode currentNode, XsdNode newNode) {
		currentNode.transparent = true;
		currentNode.isExtended = true;
		currentNode.add(newNode);
	}

	private boolean isGroupParticleWithChoiceOrder(Particle ptc) {
		return ptc.getStructureType() == Structure.GROUP && ((Group) ptc).getOrder().getType() == Order.CHOICE;
	}

	private Enumeration<Particle> getElementsFromType(XMLType type) {
        // If a type has a single node of type sequence containing a single node of type choice, then the code
        // interprets the sequence node as a choice and the user is presented with two consecutive choices.
        // First, a choice with a single option and then the actual choice with the right options.
		// To avoid this, if we find a type with a single group node (sequence with choice order) containing
		// a single group node (with choice order), then we enumerate the elements from the sequence to get
        // the actual choice options. With this approach, the user will be presented with just one choice
        // to select.
        // This code to handle a sequence with a single choice element was added because of the complexType
        // 'interactionList', which has a single sequence node with a single choice node containing multiple
        // elements.
        // Without this code, mapping set for the 'interactionList' type could not be loaded.
		if (((ContentModelGroup) type).getParticleCount() == 1) {
			Particle ptc = ((ContentModelGroup) type).getParticle(0);
			if (isGroupParticleWithChoiceOrder(ptc) &&
					((Group) ptc).getContentModelGroup().getParticleCount() == 1) {
				Particle childPtc = ((Group) ptc).getContentModelGroup().getParticle(0);
				if (isGroupParticleWithChoiceOrder(childPtc)) {
					return ((Group) ptc).enumerate();
				}
			}
		}
		return ((ComplexType) type).enumerate();
	}

}
