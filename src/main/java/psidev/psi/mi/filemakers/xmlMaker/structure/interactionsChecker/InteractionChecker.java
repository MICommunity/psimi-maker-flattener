package psidev.psi.mi.filemakers.xmlMaker.structure.interactionsChecker;

import psidev.psi.mi.filemakers.xmlMaker.structure.FlatFileContainer;
import psidev.psi.mi.filemakers.xmlMaker.gui.FlatFilePanel;

public class InteractionChecker {

    // TODO: fetch the elements displayed in the flatfile container? If more than 2 uniprotKB -> nary interaction
    FlatFileContainer flatFileContainer = new FlatFileContainer();
    FlatFilePanel flatFilePanel = new FlatFilePanel();

    public boolean isInteractionBinary(){
        int numberOfInteractors = 0;
//        flatFileContainer.getValue();
        System.out.println("get list " + flatFilePanel.getList());
        if (numberOfInteractors > 2){
            return true;
        } else {
            return false;
        }
    }
}
