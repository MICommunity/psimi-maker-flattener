package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UniprotCallerUtils {

    private static final Logger log = Logger.getLogger(UniprotCallerUtils.class.getName());

    public Workbook readFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            log.log(Level.SEVERE, "File does not exist: {0}", path);
            throw new IOException("File not found: " + path);
        }
        try (FileInputStream fileStream = new FileInputStream(file)) {
            return WorkbookFactory.create(fileStream);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to read file: " + path, e);
            throw e;
        }
    }
}