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

import java.beans.XMLDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import psidev.psi.mi.filemakers.xmlMaker.mapping.DictionaryMapping;
import psidev.psi.mi.filemakers.xmlMaker.mapping.FlatFileMapping;
import psidev.psi.mi.filemakers.xmlMaker.mapping.Mapping;
import psidev.psi.mi.filemakers.xmlMaker.mapping.TreeMapping;
import psidev.psi.mi.filemakers.xmlMaker.structure.Dictionary;
import psidev.psi.mi.filemakers.xmlMaker.structure.FlatFile;
import psidev.psi.mi.filemakers.xmlMaker.structure.XsdTreeStructImpl;
import psidev.psi.mi.filemakers.xsd.FileMakersException;
import psidev.psi.mi.filemakers.xsd.SimpleMessageManager;
import psidev.psi.mi.filemakers.xsd.Utils;

/**
 * 
 * Executable class for the maker, without graphical user interface
 * 
 * Main class for the maker: load an XML schema as a tree, load the flat files,
 * the dictionaries get selected nodes from the mapping file and write an XML
 * document.
 * 
 * Available parameters: -mapping: the mapping file, created by the GUI
 * application -o: name of the XML document to write -log: name of the log file
 * -flatfiles: names of the flat files in the right order, separated by comma
 * -dictionaries: names of the dictionary files in the right order, separated by
 * comma example: java -Xms500M -Xmx500M -classpath
 * classes/:libs/xercesImpl.jar:
 * libs/castor-0.9.5-xml.jar:libs/xml-apis.jar:libs/xmlParserAPIs.jar
 * mint.filemakers.xmlFlattener.XmlMaker -mapping mapping.xml -flatfiles
 * data/interactions.txt,data/interactors.txt,data/experiments.txt -dictionaries
 * data/organisms.txt -o db-psi.xml -log db-psi.log
 * 
 * 
 * @author Arnaud Ceol, University of Rome "Tor Vergata", Mint group,
 *         arnaud.ceol@gmail.com
 * 
 */
public class XmlMaker {

	private static final Log log = LogFactory.getLog(XmlMaker.class);

	private static void displayUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1) {
			formatter.printHelp("bin/xmlmaker.bat ",
				options);
		} else {
			formatter.printHelp("sh bin/xmlmaker ",
					options);
		}
	}

	public void load(Mapping mapping) throws MalformedURLException,
			FileMakersException {
		xsdTree.flatFiles.flatFiles = new ArrayList<FlatFile>();

		/* flat files */
		for (int i = 0; i < mapping.getFlatFiles().size(); i++) {
			if (mapping.getFlatFiles().get(i) == null)
				continue;
			FlatFileMapping ffm = (FlatFileMapping) mapping.getFlatFiles().get(i);
			FlatFile f = new FlatFile();
			f.lineSeparator = ffm.getLineSeparator();
			f.firstLineForTitles = ffm.isFisrtLineForTitle();
			f.setSeparators(ffm.getSeparators());
			try {
				URL url = Utils.absolutizeURL(ffm.getFileURL());
				f.load(url);
			} catch (IOException ioe) {
				log.error("ERROR: unable to load flat file "
						+ f.fileURL.getFile());
				throw new FileMakersException("unable to load flat file "
						+ ffm.getFileURL());
			}
			xsdTree.flatFiles.addFlatFile(f);
		}

		/* dictionaries */
		xsdTree.dictionaries.dictionaries = new ArrayList<Dictionary>();

		for (int i = 0; i < mapping.getDictionaries().size(); i++) {
			DictionaryMapping dm = (DictionaryMapping) mapping.getDictionaries()
					.get(i);
			try {
				URL url = Utils.absolutizeURL(dm.getFileURL());
				Dictionary d1 = new Dictionary(url, dm.getSeparator(),
						dm.isCaseSensitive());
				xsdTree.dictionaries.dictionaries.add(d1);
			} catch (IOException ioe) {
				log.error("ERROR: unable to load dictionary file "
						+ dm.getFileURL());

				throw new FileMakersException("unable to load dictionary file "
						+ dm.getFileURL());
			}
		}

		/* tree */
		TreeMapping treeMapping = mapping.getTree();

		try {
			xsdTree.loadSchema(treeMapping.getSchemaURL());
		} catch (IOException ioe) {
			ioe.printStackTrace();
			
			log.error("ERROR: unable to load schema "
					+ treeMapping.getSchemaURL());
			throw new FileMakersException("unable to load schema "
					+ treeMapping.getSchemaURL());
		}
		((XsdTreeStructImpl) xsdTree).loadMapping(treeMapping);
		xsdTree.check();
	}

	public XsdTreeStructImpl xsdTree;

	public XmlMaker() {
		xsdTree = new XsdTreeStructImpl();
		xsdTree.setMessageManager(new SimpleMessageManager());
	}

	public static void main(String[] args) throws Exception {

		System.setProperty("java.awt.headless", "true");
		XmlMaker f = new XmlMaker();

		Options options = new Options();

		// create Option objects
		Option helpOpt = new Option("help", "print this message.");
		options.addOption(helpOpt);

		Option option = new Option("mapping", true,
				"the mapping file, created by the GUI application");
		option.setRequired(true);
		options.addOption(option);

		option = new Option("o", true, "xml document");
		option.setRequired(true);
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
		String xmlFile = line.getOptionValue("o");

		log.info("mapping = " + mappingFileName + ", output = " + xmlFile);

		FileInputStream fin = new FileInputStream(mappingFileName);
		
		Mapping mapping;
		
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Mapping.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			mapping = (Mapping) jaxbUnmarshaller.unmarshal(fin);		

			fin.close();
		} catch (JAXBException jbe) {
			System.out.println("Doesn't look like the right mapping format, trying with the old one.");
			fin = new FileInputStream(mappingFileName);
			XMLDecoder xdec = new XMLDecoder(fin);
			mapping = (Mapping) xdec.readObject();
			xdec.close();
			fin.close();
			System.out.println("Ok, mapping loaded.");
		}
		// Create XML decoder.

		if (flatFiles != null) {
			String[] files = flatFiles.replaceAll("'", "").split(",");
			for (int j = 0; j < files.length; j++) {
				((FlatFileMapping) mapping.getFlatFiles().get(j)).setFileURL(files[j]);
				log.info("flat file " + j + ": " + files[j]);
			}
		}

		if (dictionaries != null) {
			String[] files = dictionaries.replaceAll("'", "").split(",");
			for (int j = 0; j < files.length; j++) {
				((DictionaryMapping) mapping.getDictionaries().get(j))
						.setFileURL(files[j]);
				log.info("dictionary " + j + ": " + files[j]);
			}
		}

		if (schema != null) {
			mapping.getTree().setSchemaURL(schema.replaceAll("'", ""));
		}

		try {
			f.load(mapping);
		} catch (FileMakersException fme) {
			log.error("exit from program: unable to load the mapping");
			return;
		}

		f.xsdTree.print2(new File(xmlFile));
		log.debug("done");
		return;

	}

}