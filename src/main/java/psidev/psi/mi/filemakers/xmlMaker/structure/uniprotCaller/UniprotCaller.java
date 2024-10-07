package psidev.psi.mi.filemakers.xmlMaker.structure.uniprotCaller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class UniprotCaller {

    private static final Log log = LogFactory.getLog(UniprotCaller.class);
//    https://rest.uniprot.org/uniprotkb/search?query=O95905%20AND%20organism_id:9606
    private static final String UNIPROT_API_URL = "https://rest.uniprot.org/uniprotkb/search?query=";
    Map<String, String> alreadyParsed = new HashMap<>();

    public String fetchUniprotResults(String protein, String organismId) {
        String urlString = UNIPROT_API_URL + protein + "%20AND%20organism_id:" + organismId;
        if (alreadyParsed.containsKey(protein)) {
            return alreadyParsed.get(protein);
        }
        else {
            try {
                URL uniprotURL = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) uniprotURL.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                try (BufferedReader queryResults = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder content = new StringBuilder();
                    String inputLine;
                    while ((inputLine = queryResults.readLine()) != null) {
                        content.append(inputLine);
                    }
                    JsonObject jsonResponse = JsonParser.parseString(content.toString()).getAsJsonObject();
                    String uniprotAccession = getUniprotAC(jsonResponse);
//                    log.info("BIGGEST SEQUENCE: " + uniprotAccession);
                    System.out.println("New Uniprot AC found: " + uniprotAccession);
                    alreadyParsed.put(protein, uniprotAccession);
                    return uniprotAccession;
                }
            } catch (Exception e) {
                log.error("No Uniprot accession results found", e);
            }
        }
        return null;
    }

    public String getUniprotAC(JsonObject results) {
        ArrayList<JsonObject> swissProtUniprotACs = new ArrayList<>();
        ArrayList<JsonObject> tremblUniprotACs = new ArrayList<>();

        if (results != null && results.has("results")) {
            JsonArray resultsAsJson = results.get("results").getAsJsonArray();
            for (int i = 0; i < resultsAsJson.size(); i++) {
                JsonObject result = resultsAsJson.get(i).getAsJsonObject();
                if (result.has("entryType")) {
                    String entryType = result.get("entryType").getAsString();
                    if (entryType.equals("UniProtKB reviewed (Swiss-Prot)")) {
                        swissProtUniprotACs.add(result);
                    } else if (entryType.equals("UniProtKB unreviewed (TrEMBL)")) {
                        tremblUniprotACs.add(result);
                    }
                }
            }
        }

        log.info("NUMBER OF RESULTS: " + (swissProtUniprotACs.size() + tremblUniprotACs.size()));
        sortArrayBySequenceLength(swissProtUniprotACs);
        sortArrayBySequenceLength(tremblUniprotACs);
         return chooseUniprotAc(swissProtUniprotACs, tremblUniprotACs);
    }

    public String chooseUniprotAc(ArrayList<JsonObject> swissProtUniprotACs, ArrayList<JsonObject> tremblUniprotACs) {
        if (!swissProtUniprotACs.isEmpty()) {
            return swissProtUniprotACs.get(0).get("primaryAccession").getAsString();
        } else if (!tremblUniprotACs.isEmpty()) {
            return tremblUniprotACs.get(0).get("primaryAccession").getAsString();
        }
        return null;
    }

    public void sortArrayBySequenceLength(ArrayList<JsonObject> arrayList) {
        arrayList.sort((result1, result2) -> Integer.compare(getSequenceLength(result2), getSequenceLength(result1)));
    }

    private int getSequenceLength(JsonObject result) {
        return result.has("sequence") && result.getAsJsonObject("sequence").has("value")
                ? result.getAsJsonObject("sequence").get("value").getAsString().length()
                : 0;
    }

    public void printResults(ArrayList<JsonObject> swissProtUniprotACs, ArrayList<JsonObject> tremblUniprotACs) {
        printAccessionResults(swissProtUniprotACs, "Swiss-Prot");
        printAccessionResults(tremblUniprotACs, "TrEMBL");
    }

    private void printAccessionResults(ArrayList<JsonObject> uniprotACs, String type) {
        for (JsonObject result : uniprotACs) {
            String accession = result.get("primaryAccession").getAsString();
            String sequence = result.has("sequence") && result.getAsJsonObject("sequence").has("value")
                    ? result.getAsJsonObject("sequence").get("value").getAsString()
                    : "";
            log.info("Accession: " + accession + ", Sequence length: " + sequence.length() + ", Type: " + type);
        }
    }
}
