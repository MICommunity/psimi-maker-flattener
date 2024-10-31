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
package psidev.psi.mi.filemakers.xmlFlattener.structure;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.tree.TreeNode;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xerces.dom.DeferredTextImpl;
import org.exolab.castor.xml.schema.Annotated;
import org.exolab.castor.xml.schema.Structure;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import psidev.psi.mi.filemakers.xmlFlattener.mapping.TreeMapping;
import psidev.psi.mi.filemakers.xsd.AbstractXsdTreeStruct;
import psidev.psi.mi.filemakers.xsd.Utils;
import psidev.psi.mi.filemakers.xsd.XsdNode;

/**
 * 
 * This class overrides the abstract class AbstractXslTreeStruct to provide a
 * tree representation of an XML schema, with management of transformation of an
 * XML file to a flat file.
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 * 
 */
public class XsdTreeStructImpl extends AbstractXsdTreeStruct {

	private static final Log log = LogFactory
     .getLog(XsdTreeStructImpl.class);
	
	/**
	 * Clean tree. If set to true, all unused nodes will be deleted from the tree.
	 * This is not desirable for the GUI, because the user may want to change the   
	 */
	private static boolean allowCleanTree = true;

	private int curElementsCount = 0;

	/**
	 * the XML document to parse and transform into a flat file
	 */
	private Document document = null;

	private URL documentURL = null;

	/**
	 * the separator for the flat file
	 */
	private String separator = "\t";

	/**
	 * the node associated to a line of the flat file. if null, the printer will
	 * look for the deeper node that is an ancestor of every selection.
	 */
	private XsdNode lineXsdNode = null;

	/**
	 * true if the user has chosen a node that contains what he wants to see on
	 * a line of the flat file
	 */
	private boolean lineNodeIsSelected = false;

	/**
	 * the elements of the XML document associated to a line of the flat file.
	 * if null, the printer will look for the deeper node that is an ancestor of
	 * every selection.
	 */
	private ArrayList<Node> lineElements = null;

	/**
	 * Indicate if the document should be validated. Validating a document may
	 * take time, but it is necessary for instance for using xml id (e.g. PSI-MI
	 * xml 1.0)
	 */
	private static boolean validateDocument = false;

	/**
	 * create a new instance of XslTree The nodes will not be automatically
	 * duplicated even if the schema specify that more than one element of this
	 * type is mandatory
	 */
	public XsdTreeStructImpl() {
		super(false, false);
	}

	/**
	 * this map contains regular expression used to filter XML node if a node do
	 * not validate the regexp, itself or its parent element (in case of
	 * attribute) will be ignored
	 */
	private HashMap<XsdNode, String> elementFilters = new HashMap<>();

	/**
	 * set the separator for the flat file
	 * 
	 * @param s
	 *            the separator
	 */
	public void setSeparator(String s) {
		separator = s;
	}

	/**
	 * check for errors on this node (lack of associations...) and return an
	 * array of understandable Strings describing the errors
	 * 
	 * @param node
	 *            the node to check
	 * @return a boolean
	 */
	public boolean check(XsdNode node) {
		return true;
	}

	/**
	 * this method should reinitialize every variable making reference to the
	 * actual tree, such as any <code>List</code> used to make associations to
	 * externals objects.
	 * set selection as a new <code>ArrayList</code>
	 */
	public void emptySelectionLists() {
        ArrayList<XsdNode> selectionsCopy = new ArrayList<>(selections);
        for (XsdNode node : selectionsCopy) {
            unselectNode(node);
        }
		lineXsdNode = null;
		elementFilters = new HashMap<>();
	}

	// HashMap referencedElements = new HashMap();

	/**
	 * Open a frame to choose an XML document and load it.
	 * 
	 */
	public void loadDocument(URL url) throws
            NullPointerException, IOException,
			SAXException {
		maxCounts = new HashMap<>();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(validateDocument);

		factory.setAttribute(SCHEMA_LANGUAGE, XML_SCHEMA);
		factory.setAttribute(SCHEMA_SOURCE, schemaURL);
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			log.debug("XML document url: "+url.toString());
			builder.setErrorHandler(xmlErrorHandler);
			document = builder.parse(url.toString());
			this.documentURL = url;

			/* get all references */
			log.debug("get keys/keyRefs");
			buildKeyMaps();
			for (String refer : refType2referredType.keySet()) {
				String referred = refType2referredType.get(refer);
				log.debug("found refType: " + refer + " refers " + referred);
			}
			log.debug("done");

			log.debug("document parsed ... get elements");
			setLineNode(lineXsdNode);

			Utils.lastVisitedDirectory = url.getPath();
			Utils.lastVisitedDocumentDirectory = url.getPath();
		} catch (ParserConfigurationException e) {
			/* TODO: manage exception */
		}
	}

	private HashMap<String, Node> xsKeyNodes = new HashMap<>();

	private void getKeyNodes(String keyName, String keySelector) {

		if (document == null)
			return;

		String[] path = keySelector.split("/");

		/* the node that contains all keys */
		/* find the parent node */
		Node nodeContainer = getContainer(document, path, 0);

		if (nodeContainer == null)
			return;
		log.debug("found list of referred node: "
				+ nodeContainer.getNodeName());

		for (int i = 0; i < nodeContainer.getChildNodes().getLength(); i++) {
			Node child = nodeContainer.getChildNodes().item(i);
            /* get refId name */
			String idFieldName = getReferredIdFieldName();
			if (child.hasAttributes()) {
				for (int j = 0; j < child.getAttributes().getLength(); j++) {
					if (child.getAttributes().item(j).getNodeName().equals(
							idFieldName)) {
						String ref = child.getAttributes().item(j)
								.getNodeValue();
						xsKeyNodes.put(keyName + "#" + ref, child);// keyName
						log.debug("add: " + keyName + "#" + ref);
					}
				}
			}

		}

	}

	private Node getContainer(Node node, String[] path, int startIdx) {

		if (startIdx == path.length - 1)
			return node;
		if (!node.hasChildNodes()) {
			return null;
		}

		for (int j = 0; j < node.getChildNodes().getLength(); j++) {
			if (node.getChildNodes().item(j).getNodeName().equals(
					path[startIdx])) {
				return getContainer(node.getChildNodes().item(j), path,
						++startIdx);
			}
		}
		return null;
	}

	private String getReferredIdFieldName() {
		return "id";
	}

	private void buildKeyMaps() {

		log.debug("get keys");
		for (Node node : keys) {
			String keyName = null;
			String keySelector = null;

            if (node.hasAttributes()) {
				for (int i = 0; i < node.getAttributes().getLength(); i++) {
					if (node.getAttributes().item(i).getNodeName().equals(
							"name")) {
						keyName = node.getAttributes().item(i).getNodeValue();

					}
				}
			}

			for (int i = 0; i < node.getChildNodes().getLength(); i++) {
				Node child = node.getChildNodes().item(i);

				if (child.getNodeName().equals("xs:selector")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
							keySelector = getXpath(child.getParentNode()
									.getParentNode())
									+ "/"
									+ child.getAttributes().item(j)
											.getNodeValue();
						}
					}
				} else if (child.getNodeName().equals("xs:field")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
                            child.getAttributes().item(j);
                        }
					}
				}
			}
			getKeyNodes(keyName, keySelector);

		}

		log.debug("get keyRefs");
		for (Node node : keyRefs) {
            String keyRefRefer = null;
			String keyRefSelector = null;

			if (node.hasAttributes()) {
				for (int i = 0; i < node.getAttributes().getLength(); i++) {
					if (node.getAttributes().item(i).getNodeName().equals(
							"name")) {
                        node.getAttributes().item(i);
                    } else if (node.getAttributes().item(i).getNodeName()
							.equals("refer")) {
						keyRefRefer = node.getAttributes().item(i)
								.getNodeValue();
					}
				}
			}
			for (int i = 0; i < node.getChildNodes().getLength(); i++) {
				Node child = node.getChildNodes().item(i);
				if (child.getNodeName().equals("xs:selector")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
							keyRefSelector = child.getAttributes().item(j)
									.getNodeValue();
						}
					}
				} else if (child.getNodeName().equals("xs:field")) {
					for (int j = 0; j < child.getAttributes().getLength(); j++) {
						if (child.getAttributes().item(j).getNodeName().equals(
								"xpath")) {
                            child.getAttributes().item(j);
                        }
					}
				}
			}
			refType2referredType.put(getSchemaXpath(node.getParentNode()) + "/"
					+ keyRefSelector, keyRefRefer);
		}
	}

	/**
	 * 
	 * @uml.property name="lineNode"
	 */
	public void setLineNode(XsdNode lineNode) {
		this.lineXsdNode = lineNode;
		maxCounts = new HashMap<>();

		lineElements = getNodes(lineNode.getPath());
		treeModel.reload(lineNode);

		lineNodeIsSelected = true;
	}

	public String getInfos(XsdNode node) {
		String infos = super.getInfos(node);
		infos += "selected: " + selections.contains(node) + "\n";
		return infos;
	}

	/**
	 * this <code>HashMap</code> keep the maximum amount of a node type found in
	 * the file for a type of node. The key is the String association of the
	 * name of the parent and the name of the node
	 */
	public HashMap<XsdNode, Integer> maxCounts = new HashMap<>();

	/**
	 * follow a path in the document to find corresponding element
	 * 
	 * @return the node in the XML document that found by following the path
	 * 
	 */
	public ArrayList<Node> getNodes(TreeNode[] path) {
		if (document == null)
			return null;

		Node value = document.getDocumentElement();
		ArrayList<Node> list = getXmlElements(path, value, 0);
		curElementsCount = list.size();
		log.debug(curElementsCount + " elements found for selection.");
		return list;

	}

	public ArrayList<Node> getXmlElements(TreeNode[] path, Node xmlNode,
			int pathIndex) {
		ArrayList<Node> list = new ArrayList<>();

		if (pathIndex < path.length - 1) {
			NodeList children = xmlNode.getChildNodes();

			for (int j = 0; j < children.getLength(); j++) {
				if (path[pathIndex + 1].toString().compareTo(
						children.item(j).getNodeName()) == 0) {
					list.addAll(getXmlElements(path, children.item(j),
							pathIndex + 1));
				}
			}

        } else {
			list.add(xmlNode);
        }
        return list;
    }

	/**
	 * 
	 * @param element
	 *            an element of the XML document
	 * @return value of this element if it exists, an empty String else
	 */
	public String getElementValue(Element element) {
		try {
			NodeList children = element.getChildNodes();
			String value = "";
			for (int i = 0; i < children.getLength(); i++) {
				if (children.item(i).getNodeType() == Node.ATTRIBUTE_NODE) {
					if (elementFilters.containsKey(children.item(i))) {
						try {
							String value2 = children.item(i).getNodeValue();
							/* TODO: done for managing filter */
							if (!value2.matches(elementFilters
                                    .get(children.item(i)))) {
								return "";
							}
						} catch (NullPointerException e) {
							log.debug(e);
							return "";
						}
					}

				}

				if (children.item(i).getNodeName().equals("#text"))
					value = children.item(i).getNodeValue();
			}

			return value;
		} catch (NullPointerException e) {
			/* element is null */
			return "";
		}

	}

	/**
	 * look in the XML schema for the deepest node that is an ancestor of every
	 * node selected @ return the deepest node in the XML schema that is an
	 * Ancestor of every node selected
	 */
	public void setXmlRoot() {
		if (lineNodeIsSelected)
			return;

		if (selections.isEmpty()) {
			lineXsdNode = (XsdNode) treeModel.getRoot();
			lineElements = getNodes(lineXsdNode.getPath());
			return;
		}

		XsdNode value = rootNode;
		XsdNode tmp = null;
		XsdNode select = null;
		XsdNode lastDuplicable = null;
		// go down while no more than one child is used and current node
		// is not selected

		// keep last node duplicable

		Enumeration<TreeNode> children = value.children();
		int nb = 0;
		while (nb <= 1 && !selections.contains(tmp)) {

			if (children.hasMoreElements()) {
				XsdNode child;
				child = (XsdNode) children.nextElement();
				if (child.isUsed()) {
					tmp = child;
					nb++;
				}
				if (selections.contains(child)) {
					select = child;
				}
			} else {
				nb = 0;
				if (Objects.requireNonNull(tmp).isDuplicable())
					lastDuplicable = tmp;
				value = tmp;
				children = value.children();
			}
		}
		if (nb <= 1)
			value = select;
		log.debug("[PSI makers: flattener] root selected: "
				+ Objects.requireNonNull(value));

		lineXsdNode = lastDuplicable;

		lineElements = getNodes(Objects.requireNonNull(lineXsdNode).getPath());

	}

	/**
	 * true if currently printed element is the first of a line. Used to know
	 * if a separator is needed when printing the flat file.
	 */
	public boolean firstElement = true;

	/**
	 * create a flat file separated by specified separator containing every
	 * element contained in the XML document and selected on the tree, with a
	 * first line that contains the titles of columns
	 * 
	 * @param out
	 *            the <code>writer</code> where to print the file
	 */
	public void write(Writer out) throws IOException {
		/*
		 * get the first interesting node, ie the deepest one that is an
		 * ancestor of every selected node, in the schema and corresponding
		 * nodes in the document
		 */
		setXmlRoot();
		firstElement = true;
		/* marshall once for title */

		if (allowCleanTree)
			lineXsdNode.clean();

		out.write(getTitle(lineXsdNode) + "\n");
		out.flush();

		firstElement = true;
		/* Marshal each element */
        for (Node lineElement : lineElements) {
            firstElement = true;
            writeNode(lineXsdNode, lineElement, out, false);
            out.write("\n");
            out.flush();
        }
	}

	/**
	 * get the maximum amount of element of a type as child of another type of
	 * element in the XML document. It is used to know how many columns have to
	 * be created in the flat file, as even empty ones have to be printed.
	 */
	public int getMaxCount(XsdNode xsdNode) {
		XsdNode originalNode = xsdNode;
		/* if no document loaded */
		if (lineElements == null) {
			return 0;
		}
		/* max count already computed */
		if (maxCounts.containsKey(xsdNode)) {
			return maxCounts.get(xsdNode);
		}

		int count;
		int max = 0;

		/* for attributes get number of parent element */
		if (((Annotated) xsdNode.getUserObject()).getStructureType() == Structure.ATTRIBUTE) {
			xsdNode = (XsdNode) xsdNode.getParent();
		}

		for (Node lineElement : lineElements) {
			count = getMaxCount(lineElement, lineXsdNode, xsdNode, xsdNode
					.pathFromAncestorEnumeration(lineXsdNode));
			if (count > max)
				max = count;
		}

		/* the fields are kept even if no element have been found */
		if (max < xsdNode.min) {
			max = xsdNode.min;
		}
		if (max == 0) {
			max = 1;
		}

		/* keep the result */
		maxCounts.put(originalNode, max);

		return max;
	}

	/**
	 * get the maximum amount of element of a type as child of another type of
	 * element in the XML document. It is used to know how many columns have to
	 * be created in the flat file, as even empty ones have to be printed.
	 * 
	 * @param element
	 *            the element in the XML document
	 * @param parent
	 *            the parent of the node on the tree
	 * @param target
	 *            the node on the tree
	 * @param path
	 *            the path to access to the node
	 */
	public int getMaxCount(Node element, XsdNode parent, XsdNode target,
			Enumeration<TreeNode> path) {
		if (target == parent) {
			if (elementFilters.containsKey(parent)) {
				String value = ((Element) element).getAttributeNode(
						target.toString()).getNodeValue();
				/* TODO: done for managing filter */
				if (!value.matches(elementFilters.get(target))) {
					log.debug(target.getName() + " filtered");
					return 0;
				}
			}
			return 1;
		}

		int currentMax;
        int max = 0;

		path.nextElement();
		XsdNode nextNode = (XsdNode) path.nextElement();

		/* get all children and refs */
		NodeList children = element.getChildNodes();

		for (int indexChildren = 0; indexChildren < children.getLength(); indexChildren++) {
			Node xmlChild = children.item(indexChildren);

			/*
			 * check for the name: a single node in the tree could have numerous
			 * corresponding elements in the XML document, that could be either
			 * the node itself or a reference.
			 */
			if (xmlChild.getNodeType() == Structure.ATTRIBUTE) {

				Enumeration<TreeNode> xsdChildren = nextNode.children();
				while (xsdChildren.hasMoreElements()) {
					XsdNode xsdChild = (XsdNode) xsdChildren.nextElement();

					if (elementFilters.containsKey(xsdChild)) {
						try {
							String value = ((DeferredTextImpl) xmlChild)
									.getNodeValue();
							/* TODO: done for managing filter */
							if (!value.matches(elementFilters
                                    .get(xmlChild))) {
								return 0;
							}
						} catch (Exception e) {
							/* TODO : manage exception */
						}
					}
				}
			}

			/* direct children */
			if (xmlChild.getNodeName().equals(nextNode.toString())) {
				currentMax = getMaxCount(xmlChild, nextNode, target, target
						.pathFromAncestorEnumeration(nextNode));
                if (target.getParent().toString().compareTo(
						parent.toString()) == 0) {
					max += currentMax;
				} else {
					if (currentMax > max)
						max = currentMax;
				}
			}

			/* references */
			else if (isRefType(xmlChild.getNodeName())) {
				Element ref = getElementById(((Element) xmlChild) // document.
						.getAttribute(refAttribute));
				if (ref != null
						&& ref.getNodeName().compareTo(nextNode.toString()) == 0) {
					// count++;
					currentMax = getMaxCount(ref, nextNode, target, target
							.pathFromAncestorEnumeration(nextNode));
                    if (target.getParent().toString().compareTo(
							parent.toString()) == 0) {
						max += currentMax;
					} else if (currentMax > max) {
						max = currentMax;
					}
				}
			}

			/* key ref */
			else if (isXsRefPath(xmlChild)) {
				Element ref = this.getElementByKeyRef(xmlChild);
				if (ref != null
						&& ref.getNodeName().compareTo(nextNode.toString()) == 0) {
					// count++;
					currentMax = getMaxCount(ref, nextNode, target, target
							.pathFromAncestorEnumeration(nextNode));
                    if (target.getParent().toString().compareTo(
							parent.toString()) == 0) {
						max += currentMax;
					} else if (currentMax > max) {
						max = currentMax;
					}
				}
			}
			/* ID */
			else if (isRefType(xmlChild.getNodeName())) {
				Element ref = getElementById(((Element) xmlChild) // document.
						.getAttribute(refAttribute));
				if (ref != null
						&& ref.getNodeName().compareTo(nextNode.toString()) == 0) {
					// count++;
					currentMax = getMaxCount(ref, nextNode, target, target
							.pathFromAncestorEnumeration(nextNode));
                    if (target.getParent().toString().compareTo(
							parent.toString()) == 0) {
						max += currentMax;
					} else if (currentMax > max) {
						max = currentMax;
					}
				}
			}

		}
		return max;
	}

	/**
	 * add to the flat file the content of a node
	 * 
	 * @param xsdNode
	 *            the node in the tree to parse
	 * @param xmlElement
	 *            the element in the XML document
	 * @param empty
	 *            title or full parsing
	 * @param out
	 * 			writer
	 */
	public boolean writeNode(XsdNode xsdNode, Node xmlElement, Writer out,
			boolean empty) throws IOException {

		/* first check if the element do not have to be filtered */
		if (!empty) {
			Enumeration<TreeNode> children = xsdNode.children();
			while (children.hasMoreElements()) {
				XsdNode child = (XsdNode) children.nextElement();

				if (((Annotated) child.getUserObject()).getStructureType() == Structure.ATTRIBUTE) {
					if (elementFilters.containsKey(child)) {
						String value = ((Element) xmlElement).getAttributeNode(
								child.toString()).getNodeValue();
						/* TODO: done for managing filter */
						Pattern p = Pattern.compile(elementFilters.get(child));
						Matcher m = p.matcher(value);
						boolean match = m.matches();

						if (!match) {
							return false;
						}
					}
				}
			}
		}

		if (!xsdNode.isUsed()) {
			return false;
		}

		if (selections.contains(xsdNode)) {
			if (xmlElement != null || empty) {
				String value = getElementValue((Element) xmlElement);
				/* TODO: done for managing filter */
				/* if empty marshaling, we do not care about filters */
				if (elementFilters.containsKey(xsdNode) && !empty
						&& elementFilters.get(xsdNode) != null
						&& !elementFilters.get(xsdNode).isEmpty()) {
					if (value.matches(elementFilters.get(xsdNode))) {
						if (firstElement)
							firstElement = false;
						else
							out.write(separator);
						out.write(getElementValue((Element) xmlElement));
					}
				} else {
					if (firstElement)
						firstElement = false;
					else
						out.write(separator);
					out.write(getElementValue((Element) xmlElement));
				}
			}
		}

		// Enumeration
		Enumeration<TreeNode> children = xsdNode.children();
		while (children.hasMoreElements()) {
			XsdNode child = (XsdNode) children.nextElement();
			if (child.isUsed()) {
				switch (((Annotated) child.getUserObject()).getStructureType()) {
				case Structure.ELEMENT:
					/* number of element found */
					int cpt = 0;
					/* number of elements really marshalled, ie not filtered */
					int nbElementFound = 0;
					/* create a NodeList with all children with tagName */
					if (xmlElement != null) {
						NodeList allElements = xmlElement.getChildNodes();
						ArrayList<Node> elements = new ArrayList<>();
						/*
						 * number of element found: could be lower than
						 * elements' length due to filters
						 */
						for (int i = 0; i < allElements.getLength(); i++) {
							if (allElements.item(i).getNodeName().compareTo(
									child.toString()) == 0) {
								elements.add(allElements.item(i));
							}

							/* get reference by xs:key */
							else if (isXsRefPath(allElements.item(i))) {
								Element ref = // document.
								getElementByKeyRef(allElements.item(i));

								log.debug("ref: "+allElements.item(i).getNodeName()+": "+allElements.item(i).getChildNodes().item(0).getNodeValue());
								if (ref != null
										&& ref.getNodeName().compareTo(
												child.toString()) == 0) {
									elements.add(ref);
								}
							}

							/* get references by XML id */
							else if (isRefType(allElements.item(i)
									.getNodeName())) {
								Element ref = // document.
								getElementById(((Element) allElements.item(i))
										.getAttribute(refAttribute));
								if (ref != null
										&& ref.getNodeName().compareTo(
												child.toString()) == 0) {
									elements.add(ref);
								}
							}

						}
						while (cpt < elements.size()) {
							boolean notEmptyElement = writeNode(child, elements
									.get(cpt), out, false);
							cpt++;
							if (notEmptyElement)
								nbElementFound++;
						}
					}
					int maxCount = getMaxCount(child);
					while (nbElementFound < maxCount) {
						writeNode(child, null, out, true);
						nbElementFound++;
					}
					break;
				case Structure.ATTRIBUTE:
					if (firstElement)
						firstElement = false;
					else
						out.write(separator);

					if (xmlElement != null) {
						try {
						out.write(((Element) xmlElement).getAttributeNode(
								child.toString()).getNodeValue());
						} catch (Exception e) {
							log.debug(child.getName()+"/"+xmlElement.getNodeName(), e);
						}
					}
					break;
				default:
					log.debug("[PSI makers: flattener] ERROR: the node is neither an attribute nor an element");
				}
			}
		}
		return true;
	}

	ArrayList<Integer> currentPath = new ArrayList<>();

	private String getCurrentPath() {
		StringBuilder path = new StringBuilder();
		for (Integer i : currentPath) {
			if (i > 0) {
				if (path.length() > 0)
					path.append(".");
				path.append(i);
			}
		}

		if (!path.toString().isEmpty())
			path.insert(0, "-");

		return path.toString();
	}

	/**
	 * Such as write but return a String instead of writing in a file. Only for
	 * the marshalling type title.
	 * 
	 * @param xsdNode
	 * 				node
	 * @return
	 * 				string
	 */
	public String getTitle(XsdNode xsdNode) {
		StringBuilder out = new StringBuilder();

		if (!xsdNode.isUsed()) {
			return out.toString();
		}

		if (selections.contains(xsdNode)) {
			if (firstElement)
				firstElement = false;
			else
				out.append(separator);
			out.append(xsdNode.getName()).append(getCurrentPath());// nextNumber(node);
		}

		Enumeration<TreeNode> children = xsdNode.children();

		if (xsdNode.isDuplicable() && !currentPath.isEmpty()) {
			Integer i = currentPath.remove(currentPath.size() - 1);
			currentPath.add(i + 1);
		}

		while (children.hasMoreElements()) {
			XsdNode xsdChild = (XsdNode) children.nextElement();

			if (xsdChild.isUsed()) {
				switch (((Annotated) xsdChild.getUserObject())
						.getStructureType()) {
				case Structure.ELEMENT:

					int cpt = 0;
					/* create a NodeList with all children with tagName */
					int maxCount = getMaxCount(xsdChild);

					if (xsdChild.isDuplicable()) {
						currentPath.add(0);
					}

					while (cpt < maxCount) {
						out.append(getTitle(xsdChild));
						cpt++;
					}

					if (xsdChild.isDuplicable()) {
						currentPath.remove(currentPath.size() - 1);
					}
					break;
				case Structure.ATTRIBUTE:
					if (firstElement)
						firstElement = false;
					else
						out.append(separator);

					out.append(xsdChild.getName()).append(getCurrentPath());// nextNumber(child);

					break;
				default:
					log.debug("[PSI makers: flattener] ERROR: the node is neither an attribute nor an element");
				}
			}
		}

		return out.toString();
	}

	public ArrayList<XsdNode> selections = new ArrayList<>();

	public void addName(XsdNode node, String name) {
		associatedNames.put(node, name);
		node.setName(name);
		treeModel.reload(node);
	}

	public void addFilter(XsdNode node, String regexp) {
		elementFilters.remove(node);
		if (regexp != null && !regexp.trim().isEmpty())
			elementFilters.put(node, regexp.trim());
	}

	public void selectNode(XsdNode xsdNode) {
		selections.add(xsdNode);
		xsdNode.use();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(xsdNode);

		setXmlRoot();
	}

	public void unselectNode(XsdNode xsdNode) {
		selections.remove(xsdNode);
		xsdNode.unused();
		check((XsdNode) treeModel.getRoot());
		treeModel.reload(xsdNode);

		setXmlRoot();
	}

	public TreeMapping getMapping() {
		TreeMapping mapping = new TreeMapping();

		if (this.documentURL != null)
			mapping.setDocumentURL(Utils.relativizeURL(this.documentURL)
					.getPath());
		if (this.getSchemaURL() != null)
			mapping.setSchemaURL(Utils.relativizeURL(this.getSchemaURL())
					.getPath());

		if (this.lineXsdNode != null)
			mapping.setLineNode(getPathForNode(this.lineXsdNode));
		mapping.setSeparator(this.separator);

		mapping.setExpendChoices(this.expendChoices);

		ArrayList<String> selections = new ArrayList<>();
        for (XsdNode selection : this.selections) {
            selections.add(getPathForNode(selection));
        }
		mapping.setSelections(selections);

		HashMap<String, String> associatedNames = new HashMap<>();
		
		for (XsdNode node : this.associatedNames.keySet()) {
			associatedNames.put(getPathForNode(node), this.associatedNames
					.get(node));
		}
		mapping.setAssociatedNames(associatedNames);

		HashMap<String, String> elementFilters = new HashMap<>();
		
		for (XsdNode node : this.elementFilters.keySet()) {
			elementFilters.put(getPathForNode(node), this.elementFilters
					.get(node));
		}
		mapping.setElementFilters(elementFilters);

		return mapping;
	}

	/**
	 * add to the flat file the content of a node
	 * 
	 * @param node
	 *            the node in the tree to parse
	 * @param element
	 *            the element in the XML document
     */
	public String marshallNode(XsdNode node, Node element) throws IOException {
		StringBuilder marshalling = new StringBuilder();
		if (!node.isUsed()) {
			return marshalling.toString();
		}

		if (selections.contains(node)) {
			if (firstElement)
				firstElement = false;
			else
				marshalling.append(separator);

			Enumeration<TreeNode> children = node.children();
			boolean filtered = false;
			while (children.hasMoreElements()) {
				XsdNode child = (XsdNode) children.nextElement();

				if (((Annotated) child.getUserObject()).getStructureType() == Structure.ATTRIBUTE) {
					if (elementFilters.containsKey(child)) {
						// try {
						String value = ((Element) element).getAttributeNode(
								child.toString()).getNodeValue();
						/* TODO: done for managing filter */
						if (!value.matches(elementFilters.get(child))) {
							filtered = true;
						}
					}
				}
			}

			if (element != null && !filtered) {
				marshalling.append(getElementValue((Element) element));
			}
		}

		Enumeration<TreeNode> children = node.children();
		while (children.hasMoreElements()) {
			XsdNode child = (XsdNode) children.nextElement();
			if (child.isUsed()) {
				switch (((Annotated) child.getUserObject()).getStructureType()) {
				case Structure.ELEMENT:
					int cpt = 0;
					/* create a NodeList with all children with tagName */
					if (element != null) {
						NodeList allElements = element.getChildNodes();
						ArrayList<Node> elements = new ArrayList<>();
						for (int i = 0; i < allElements.getLength(); i++) {
							if (allElements.item(i).getNodeName().compareTo(
									child.toString()) == 0) {
								elements.add(allElements.item(i));
							}

							/* get references */
							else if (isXsRefPath(allElements.item(i))) {
								Element ref = getElementByKeyRef(allElements
										.item(i));
								System.out.println("ref2: "+ref.getNodeName());
								if (ref != null && ref.getNodeName().compareTo(child.toString()) == 0) {
									elements.add(ref);
								}
							}

							/* get references */
							else if (isRefType(allElements.item(i)
									.getNodeName())) {
								Element ref = getElementById(((Element) allElements.item(i))
										.getAttribute(refAttribute));
								if (ref != null
										&& ref.getNodeName().compareTo(
												child.toString()) == 0) {
									elements.add(ref);
								}
							}
						}
						while (cpt < elements.size()) {
							marshalling.append(marshallNode(child, elements
                                    .get(cpt)));
							cpt++;
						}
					}
					int maxCount = getMaxCount(child);
					while (cpt < maxCount) {
						marshalling.append(marshallNode(child, null));
						cpt++;
					}
					break;
				case Structure.ATTRIBUTE:
					if (firstElement)
						firstElement = false;
					else
						marshalling.append(separator);

					if (element != null) {
						marshalling.append(((Element) element).getAttributeNode(
                                child.toString()).getNodeValue());
					}
					break;
				default:
					log.debug("[PSI makers: flattener] ERROR: the node is neither an attribute nor an element");
				}
			}
		}
		return marshalling.toString();
	}

	public void loadMapping(TreeMapping mapping) throws IOException,
			SAXException {
		if (mapping.documentURL != null)
			this.setDocumentURL(new File(mapping.documentURL).toURI().toURL());
		
		if (mapping.getSchemaURL() != null)
			this.setSchemaURL(new File(mapping.getSchemaURL()).toURI().toURL());

		File schema = new File(Utils.absolutizeURL(schemaURL).getPath());

		if (!schema.exists()) {
			log.error("file "+mapping.getSchemaURL()+" not found");
			System.exit(1);
		}
		
		loadSchema(schema.toURI().toURL());

        this.setExpendChoices(mapping.expendChoices);

		int i = 0;
		while (i < expendChoices.size()) {
			String path = expendChoices.get(i);
			i++;
			super.extendPath(super.getNodeByPath(path));
		}

		
		
		if (mapping.getLineNode() != null)
			this.setLineNode(getNodeByPath(mapping.getLineNode()));
		
		if (documentURL != null)
			this.loadDocument(documentURL);

		this.setSeparator(mapping.separator);

		for (i = 0; i < mapping.selections.size(); i++) {
			XsdNode xsdNode = getNodeByPath(mapping.selections.get(i));
			selectNode(xsdNode);
		}

		for (String path : mapping.associatedNames.keySet()) {
			String field = mapping.associatedNames.get(path);
			XsdNode node = getNodeByPath(path);
			addName(node, field);
		}

		for (String path : mapping.elementFilters.keySet()) {
			String field = mapping.elementFilters.get(path);
			XsdNode node = getNodeByPath(path);
			this.elementFilters.put(node, field);
		}

	}

	/**
	 * @return Returns the documentURI.
	 * 
	 * @uml.property name="documentURL"
	 */
	public URL getDocumentURL() {
		return documentURL;
	}

	/**
	 * @param documentURL
	 *            The documentURI to set.
	 * 
	 * @uml.property name="documentURL"
	 */
	public void setDocumentURL(URL documentURL) {
		this.documentURL = documentURL;
	}

	/**
	 * keep current values for referenced fields
	 * 
	 * @uml.property name="associatedValues"
	 */
	public HashMap<XsdNode, String> associatedNames = new HashMap<>();

	/**
	 * @return Returns the curElementCount.
	 */
	public int getCurElementCount() {
		return curElementsCount;
	}

    /**
	 * set count for each node to 0. the count is used for the display of the
	 * title line, and this function will be used before updating the preview or
	 * before printing the file.
	 */
	public void resetCount() {
		resetCount(lineXsdNode);
	}

	private void resetCount(XsdNode node) {
		node.cpt = 0;
		int nbChildren = node.getChildCount();
		for (int i = 0; i < nbChildren; i++) {
			resetCount((XsdNode) node.getChildAt(i));
		}
	}

	/**
	 * get Element referred by this id, according to the XML id specification
	 * 
	 * @param id
	 * 			identifier
	 * @return
	 * 			document element
	 */
	private Element getElementById(String id) {
        return document.getElementById(id);
	}

	/**
	 * get element referred, according to the key/keyRef xs specification
	 * 
	 * @param node
	 * 				referred node
	 * @return
	 * 			element
	 */
	private Element getElementByKeyRef(Node node) {
		Element ref;

		String refType = getDocumentXpath(node);
		/* get ref attribute */
		String refId = node.getFirstChild().getNodeValue();
		if (refId == null || refId.isEmpty()) {
			for (int i = 0; i < node.getAttributes().getLength(); i++) {
				if (node.getAttributes().item(i).getNodeName().equals(
						refAttribute)) {
					refId = node.getAttributes().item(i).getNodeValue();
				}
			}
		}

		String referredType = refType2referredType.get(refType);

		ref = (Element) xsKeyNodes.get(referredType + "#" + refId);
		return ref;
	}

	private String getXpath(Node node) {
		String xpath = "";
		if (node.getParentNode() != null) {
			xpath = getXpath(node.getParentNode());
		}
		String name = getName(node);

		if (name != null && xpath != null && !xpath.isEmpty())
			xpath += "/";

		if (name != null)
			xpath += name;
		return xpath;
	}

	/**
	 * the name of an element in the schema is contained in the attribute 'name'
	 * 
	 * @param node
	 * 				node with a name attribute
	 * @return
	 * 			node's name
	 */
	private String getName(Node node) {
		if (!node.hasAttributes())
			return null;

		for (int i = 0; i < node.getAttributes().getLength(); i++) {
			if (node.getAttributes().item(i).getNodeName().equals("name"))
				return node.getAttributes().item(i).getNodeValue();
		}
		return null;
	}

    public Document getDocument() {
		return document;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	public XsdNode getLineXsdNode() {
		return lineXsdNode;
	}

	public void setLineXsdNode(XsdNode lineXsdNode) {
		this.lineXsdNode = lineXsdNode;
	}

	public ArrayList<Node> getLineElements() {
		return lineElements;
	}

	public void setValidateDocument(boolean validateDocument) {
		XsdTreeStructImpl.validateDocument = validateDocument;
	}

	public HashMap<XsdNode, String> getElementFilters() {
		return elementFilters;
	}

	public static void setAllowCleanTree(boolean allowCleanTree) {
		XsdTreeStructImpl.allowCleanTree = allowCleanTree;
	}

}