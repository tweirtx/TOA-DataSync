package org.theorangealliance.datasync.tabs;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import org.theorangealliance.datasync.DataSyncController;
import org.theorangealliance.datasync.json.first.AwardArray;
import org.theorangealliance.datasync.json.first.AwardFIRST;
import org.theorangealliance.datasync.json.toa.AwardTOA;
import org.theorangealliance.datasync.logging.TOALogger;
import org.theorangealliance.datasync.models.Award;
import org.theorangealliance.datasync.util.Config;
import org.theorangealliance.datasync.util.FIRSTEndpointNonLambda;
import org.theorangealliance.datasync.util.TOAEndpoint;
import org.theorangealliance.datasync.util.TOARequestBody;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.logging.Level;

public class AwardsController {

    private DataSyncController controller;
    private ObservableList<Award> awardList;
    private ArrayList<AwardTOA> uploadedAwards;

    public AwardsController(DataSyncController instance){

        this.controller = instance;

        //Setup Table
        this.controller.colAward.setCellValueFactory(new PropertyValueFactory<>("awardName"));
        this.controller.colAwardKey.setCellValueFactory(new PropertyValueFactory<>("awardKey"));
        this.controller.colAwardTeamKey.setCellValueFactory(new PropertyValueFactory<>("teamKey"));
        this.controller.colAwardIsUploaded.setCellValueFactory(cellData -> new SimpleBooleanProperty(cellData.getValue().isUploaded()));
        this.controller.colAwardIsUploaded.setCellFactory(col -> new TableCell<Award, Boolean>() {
            @Override
            protected void updateItem(Boolean done, boolean empty) {
                if (!empty && done != null) {
                    if (done) {
                        setTextFill(Color.GREEN);
                        setText("YES");
                    } else {
                        setTextFill(Color.RED);
                        setText("NO");
                    }
                }
            }
        });


        this.awardList = FXCollections.observableArrayList();
        this.controller.tableAwards.setItems(awardList);

        this.uploadedAwards = new ArrayList<>();

    }

    private void getAwardsTOA(){
        TOAEndpoint matchesEndpoint = new TOAEndpoint("GET", "event/" + Config.EVENT_ID + "/awards");
        matchesEndpoint.setCredentials(Config.TOA_API_KEY, Config.EVENT_ID);
        matchesEndpoint.execute(((response, success) -> {
            if (success) {
                uploadedAwards = matchesEndpoint.getGson().fromJson(response, ArrayList.class);
                TOALogger.log(Level.INFO, "Grabbed " + uploadedAwards.size() + " awards from TOA.");
            } else {
                this.controller.sendError("Error: " + response);
            }
        }));
    }

    public void uploadAwards() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Are you sure about this?");
        alert.setHeaderText("This operation cannot be undone.");
        String matchesText;

        alert.setContentText("YOU WILL BE PUBLICLY POSTING ALL AWARD RESULTS TO TOA. PLEASE CLICK OK ONLY IF YOU ARE SURE YOU WANT TO CONTINUE?");

        ButtonType okayButton = new ButtonType("Sure?");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(okayButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == okayButton) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Are you sure about this?");
            a.setHeaderText("This operation STILL cannot be undone.");

            a.setContentText("YOU WILL BE PUBLICLY POSTING ALL AWARD RESULTS TO TOA. ARE YOU 100% SURE YOU ARE NOT GOING TO LEAK CONFIDENTIAL DATA?");

            ButtonType o = new ButtonType("Upload");
            ButtonType c = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(o, c);

            Optional<ButtonType> r = alert.showAndWait();

            if(r.get() == o) {
                uploadAwardsNoWarning();
            }
        }
    }

    private void uploadAwardsNoWarning() {
        for(Award a : awardList){
            String methodType = "POST";
            String putRouteExtra = "";

            if(a.isUploaded()){
                methodType = "PUT";
            } else {
                if(uploadedAwards != null && uploadedAwards.size() > 0){
                    for(AwardTOA aTOA : uploadedAwards) {
                        if(aTOA.getAwardKey().equals(a.getAwardKey())){
                            methodType = "PUT";
                        }
                    }
                }

            }

            TOAEndpoint detailEndpoint = new TOAEndpoint(methodType, "event/" + Config.EVENT_ID + "/awards/" + putRouteExtra);
            detailEndpoint.setCredentials(Config.TOA_API_KEY, Config.EVENT_ID);
            TOARequestBody awardBody = new TOARequestBody();
            AwardTOA award = new AwardTOA(a.getAwardKey(), Config.EVENT_ID, a.getAwardID(), a.getTeamKey(), null, a.getAwardName());
            awardBody.addValue(award);
            detailEndpoint.setBody(awardBody);
            detailEndpoint.execute(((response, success) -> {
                if (success) {
                    int i = 0;
                    for(Award award1 : awardList){
                        if(a.getAwardKey().equals(award1.getAwardKey())){
                            awardList.get(i).setIsUploaded(true);
                            break;
                        }
                        i++;
                    }
                    uploadedAwards.add(award);
                    controller.tableAwards.refresh();
                    TOALogger.log(Level.INFO, "Successfully uploaded Award " + a.getAwardKey() + ". " + response);
                }
            }));
        }
    }

    public void getAwardsFIRST() {
        AwardArray awards = null;
        try {
            awards = FIRSTEndpointNonLambda.getGson().fromJson(FIRSTEndpointNonLambda.getResp("events/" + Config.FIRST_API_EVENT_ID  + "/awards"), AwardArray.class);
        } catch (Exception e) {
            this.controller.sendInfo("Unable to get awards " + e);
        }

        if(awards != null) {
            awardList.clear();
            for(AwardFIRST a : awards.getAwards()) {
                int recipNum = 1;
                String recipients[] = {a.getFirstPlace(), a.getSecondPlace(), a.getThirdPlace()};
                //Go Through each recipient and generate an awardID for them
                for(String r : recipients) {
                    if(!r.equals("-1") && !r.equals("") && !r.equals("(none)")) {
                        Award award = new Award();
                        String awardID = getAwardIDFromName(a.getAwardName());
                        if(awardID != null) {
                            award.setAwardID(awardID);
                            award.setAwardName(a.getAwardName());
                            award.setAwardKey(Config.EVENT_ID + "-" + awardID + recipNum);
                            award.setIsUploaded(false);
                            //Check If Award is uploaded
                            if(uploadedAwards != null && uploadedAwards.size() > 0) {
                                for(AwardTOA toaA : uploadedAwards) {
                                    if(toaA.getAwardKey().equals(award.getAwardKey())){
                                        award.setIsUploaded(true);
                                    }
                                }
                            }

                            award.setTeamKey(r + "");
                            awardList.add(award);
                            recipNum++;
                        } else {
                            this.controller.sendError("Could Not Get Award ID from " + a.getAwardName());
                            TOALogger.log(Level.INFO, "Could Not Get Award ID from " + a.getAwardName());
                        }
                    }
                }

            }
        }
        this.controller.tableAwards.refresh();
    }


    public void loadToaAwards() {

    }


    public void purgeAwards() {
        //TODO: When route becomes avaliable
    }

    private String getAwardIDFromName(String name) {
        switch (name) {
            case "Winning Alliance Award":
                return "FIN";
            case "Finalist Alliance Award":
                return "WIN";
            case "Inspire Award":
                return "INS";
            case "Think Award":
                return "THK";
            case "Connect Award":
                return "CNT";
            case "Rockwell Collins Innovate Award":
                return "INV";
            case "Design Award":
                return "DSN";
            case "Motivate Award":
                return "MOT";
            case "Control Award":
                return "CTL";
            case "Promote Award":
                return "PRM";
            case "Compass Award":
                return "CMP";
            case "Judges\u0027 Award":
                return "JUD";
            default:
                return null;
        }
    }

}