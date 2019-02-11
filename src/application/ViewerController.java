package application;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ViewerController {

    @FXML private ImageView imageView;
    @FXML public Pane imageLayers; //TODO: DO NOT LEAVE PUBLIC
    @FXML private Toggle scanImageButton, posteriseButton;
    @FXML private HBox controlPanel, settingsPanel;
    private Image image;
    private ImageProcessor imgProc;


    @FXML
    private void chooseFile(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select an Image");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.bmp", "*.gif", "*.png", "*.jpg"));
        File selectedFile = fileChooser.showOpenDialog(imageView.getScene().getWindow());
        if(selectedFile!=null) loadImage(selectedFile.getPath());
    }

    private void loadImage(String path){
        scanImageButton.setSelected(false);
        scanImage(); //clears previous boxes from screen
        try {
            FileInputStream fis = new FileInputStream(path);
            image = new Image(fis, imageView.getFitWidth(), imageView.getFitHeight(), true, false);
            fis.close();
            imgProc = new ImageProcessor(image);
            imageView.setImage(image);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @FXML
    private void scanImage(){
        if(scanImageButton.isSelected()) {
            Rectangle[] rects = imgProc.getClusterBoxes();
            for (Rectangle rect : rects) {
                imageLayers.getChildren().add(rect);
            }
        } else{
            ObservableList<Node> children = imageLayers.getChildren();
            while(children.size()>1) children.remove(1);
        }
    }

    @FXML
    private void posterise(){
        if(posteriseButton.isSelected()) imageView.setImage(imgProc.posterise(100));
        else imageView.setImage(image);
    }

    @FXML
    private void changePanel(){
        boolean visible = controlPanel.isVisible();
        controlPanel.setVisible(!visible);
        settingsPanel.setVisible(visible);
    }

    @FXML
    private void exit() {System.exit(0);}
}
