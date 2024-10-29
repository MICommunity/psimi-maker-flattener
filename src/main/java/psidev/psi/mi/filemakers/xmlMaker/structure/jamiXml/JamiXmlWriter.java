package psidev.psi.mi.filemakers.xmlMaker.structure.jamiXml;

import psidev.psi.mi.jami.commons.MIDataSourceOptionFactory;
import psidev.psi.mi.jami.commons.MIWriterOptionFactory;
import psidev.psi.mi.jami.commons.PsiJami;
import psidev.psi.mi.jami.datasource.InteractionStream;
import psidev.psi.mi.jami.datasource.InteractionWriter;
import psidev.psi.mi.jami.factory.InteractionWriterFactory;
import psidev.psi.mi.jami.factory.MIDataSourceFactory;
import psidev.psi.mi.jami.model.*;
import psidev.psi.mi.jami.xml.PsiXmlVersion;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class JamiXmlWriter {

    public void readAndWriteXml(String fileName, String xmlFileName) throws IOException {

        PsiJami.initialiseAllFactories();
        MIDataSourceOptionFactory sourceOptionFactory = MIDataSourceOptionFactory.getInstance();
        MIDataSourceFactory dataSourceFactory = MIDataSourceFactory.getInstance();
        Map<String, Object> parsingOptions = sourceOptionFactory.getDefaultOptions(new File(fileName));
        MIWriterOptionFactory optionFactory = MIWriterOptionFactory.getInstance();

        Map<String, Object> xmlWritingOptions = optionFactory.getDefaultExpandedXmlOptions(
                new File(xmlFileName),
                InteractionCategory.evidence,
                ComplexType.n_ary,
                PsiXmlVersion.v3_0_0);

        InteractionStream interactionSource = null;
        InteractionWriter xmlInteractionWriter = null;
        InteractionWriter mitabInteractionWriter = null;

        try {
            interactionSource = dataSourceFactory.getInteractionSourceWith(parsingOptions);
            InteractionWriterFactory writerFactory = InteractionWriterFactory.getInstance();
            xmlInteractionWriter = writerFactory.getInteractionWriterWith(xmlWritingOptions);

            if (interactionSource != null) {
                Iterator interactionIterator = interactionSource.getInteractionsIterator();
                mitabInteractionWriter.start();
                xmlInteractionWriter.start();

                while (interactionIterator.hasNext()) {
                    Interaction interaction = (Interaction) interactionIterator.next();
                    if (interaction instanceof InteractionEvidence) {
                        InteractionEvidence interactionEvidence = (InteractionEvidence) interaction;
                        // TODO: process the interaction evidence
                    }

                    else if (interaction instanceof ModelledInteraction) {
                        ModelledInteraction modelledInteraction = (ModelledInteraction) interaction;
                        // TODO: process the modelled interaction
                    }
                    xmlInteractionWriter.write(interaction);
                }
                xmlInteractionWriter.end();
            }
        } finally {
            if (interactionSource != null) {
                interactionSource.close();
            }
            if (xmlInteractionWriter != null) {
                xmlInteractionWriter.close();
            }
        }
    }

    public ArrayList<String[]> readFlatFile(String fileName) {
        ArrayList<String[]> data = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/Users/susiehuget/Desktop/Data/273t_with_set_baits_1.tsv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split("\t");
                data.add(row);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return data;
    }
}
