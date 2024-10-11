package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;


public class UniprotPanel extends JPanel {
    FileFetcher fileFetcher = new FileFetcher();
    private final JComboBox<String> suggestedOrganismsIds;
    private final SuggestedOrganisms suggestedOrganisms = new SuggestedOrganisms();
    private final JComboBox<String> sheets = new JComboBox<>();
    private final JComboBox<String> columns = new JComboBox<>();
    FileReader fileReader = new FileReader();
    String selectedOrganism = null;

    public UniprotPanel() {
        setLayout(new BorderLayout());

        JPanel filePanel = new JPanel();
        filePanel.setLayout(new BoxLayout(filePanel, BoxLayout.Y_AXIS));
        filePanel.setPreferredSize(new Dimension(50, 50));
        filePanel.add(fileFetcher.getFileNameLabel()).setPreferredSize(new Dimension(50, 25));

//        JButton openFileButton = fetchFileButton();
//        openFileButton.setAlignmentX(Component.CENTER_ALIGNMENT);


//        JButton fetchFileButton = fetchFileButton();
//        Utils.setDefaultSize(fetchFileButton);
//        filePanel.add(fetchFileButton);
        filePanel.add(fetchFileButton()).setPreferredSize(new Dimension(50, 25));

        JPanel fileProcessingPanel = new JPanel();
        fileProcessingPanel.setLayout(new BoxLayout(fileProcessingPanel, BoxLayout.Y_AXIS));
        fileProcessingPanel.setPreferredSize(new Dimension(50, 125));

        suggestedOrganismsIds = new JComboBox<>(suggestedOrganisms.getOrganismDisplayNames());
        suggestedOrganismsIds.setEditable(true);

        fileProcessingPanel.add(sheets).setPreferredSize(new Dimension(50, 25));
        sheets.addItem("Select sheet");
        fileProcessingPanel.add(columns).setPreferredSize(new Dimension(50, 25));
        columns.addItem("Select column to process");
        fileProcessingPanel.add(suggestedOrganismsIds).setPreferredSize(new Dimension(50, 25));

        JButton processFileButton = processFileButton();
        processFileButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        fileProcessingPanel.add(processFileButton).setPreferredSize(new Dimension(50, 25));

        sheets.addActionListener(e -> updateColumns());

        add(filePanel, BorderLayout.NORTH);
        add(fileProcessingPanel, BorderLayout.SOUTH);
    }

    public JButton fetchFileButton() {
        JButton openFile = new JButton("Open file");
        openFile.addActionListener(e -> {
            try {
                fileFetcher.fetchFile();
                Workbook workbook = fileReader.readExcelFile(fileFetcher.getFileUrl());
                for (int i = 0; i < fileReader.getSheetsNames(workbook).length; i++) {
                    sheets.addItem(fileReader.getSheetsNames(workbook)[i]);
                }

            } catch (IOException urie) {
                JOptionPane.showMessageDialog(new JFrame(),
                        "Unable to load file!",
                        "ERROR",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        return openFile;
    }

    public JButton processFileButton() {
        JButton processFile = new JButton("Process file");
        processFile.addActionListener(e -> {
            System.out.println("Processing file...");
            URL fileUrl = fileFetcher.getFileUrl();
            if (fileUrl != null) {
                // Get the user input directly from the combo box
                String selectedDisplayName = (String) suggestedOrganismsIds.getSelectedItem();
                selectedOrganism = selectedDisplayName;
                String organismId = suggestedOrganisms.getOrganismId(selectedDisplayName);
                // If organismId is null, it means the user has entered a custom ID
                if (organismId == null) {
                    organismId = selectedDisplayName != null ? selectedDisplayName.trim() : "";
                } else {
                    organismId = organismId.trim();
                }
                if (organismId.isEmpty() || organismId.equals("null")) {
                    JOptionPane.showMessageDialog(null, "Please enter a valid organism ID!", "ERROR", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String sheetSelected = (String) sheets.getSelectedItem();
                String columnSelected = (String) columns.getSelectedItem();
                if (sheetSelected == null || sheetSelected.equals("Select sheet") || columnSelected == null ||
                        columnSelected.equals("Select column to process")) {
                    JOptionPane.showMessageDialog(null, "Please select a valid sheet and column!", "ERROR", JOptionPane.ERROR_MESSAGE);
                } else {
                    fileReader.checkAndInsertUniprotResults(sheetSelected, organismId, columnSelected, fileUrl);
                }
            } else {
                JOptionPane.showMessageDialog(null, "No file selected!", "ERROR", JOptionPane.ERROR_MESSAGE);
            }
        });
        return processFile;
    }

    public void updateColumns() {
        Workbook workbook = fileReader.readExcelFile(fileFetcher.getFileUrl());
        Sheet selectedSheet = workbook.getSheet(Objects.requireNonNull(sheets.getSelectedItem()).toString());
        columns.removeAllItems();
        for (int i = 0; i < fileReader.getColumnsNames(selectedSheet).length; i++) {
            columns.addItem(fileReader.getColumnsNames(selectedSheet)[i]);
        }
    }
}
