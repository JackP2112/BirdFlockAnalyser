package application;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
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
    @FXML private  Pane imageLayers;
    @FXML private Label countLabel;
    @FXML private Toggle scanImageButton, posteriseButton;
    @FXML private HBox controlPanel, settingsPanel;
    @FXML private Slider posteriseSlider, filterMinSlider, filterMaxSlider;
    @FXML private RadioButton filterMinMax, filterIQRange;
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
            countLabel.setText(String.valueOf(imgProc.getClusterCount())+" Birds");
        } else{
            ObservableList<Node> children = imageLayers.getChildren();
            while(children.size()>1) children.remove(1);
        }
    }

    @FXML
    private void posterise(){
        if(posteriseButton.isSelected()) imageView.setImage(imgProc.posterise((int)posteriseSlider.getValue()));
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
