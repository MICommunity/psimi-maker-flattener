package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;

import psidev.psi.mi.filemakers.xsd.Utils;
import javax.swing.*;
import java.io.IOException;
import java.net.URL;


public class FileFetcher {
    private URL fileUrl;
    private final JLabel fileNameLabel = new JLabel("No file selected");

    public JLabel getFileNameLabel() {
        return fileNameLabel;
    }

    public URL getFileUrl() {
        return fileUrl;
    }

    public void fetchFile() throws IOException {
        try {
            String defaultDirectory = Utils.lastVisitedDirectory;
            if (Utils.lastVisitedDictionaryDirectory != null)
                defaultDirectory = Utils.lastVisitedDictionaryDirectory;

            JFileChooser fc = new JFileChooser(defaultDirectory);

            int returnVal = fc.showOpenDialog(new JFrame());
            if (returnVal != JFileChooser.APPROVE_OPTION) {
                return;
            }

            fileUrl = fc.getSelectedFile().toURL();
            fileNameLabel.setText("Selected file: " + fc.getSelectedFile().getName());

        } catch (NullPointerException npe) {
            JOptionPane.showMessageDialog(new JFrame(), "Unable to load file! Please select a file under xls format!",
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
