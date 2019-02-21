package application;

import javafx.collections.ObservableList;
import javafx.event.Event;
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
	private boolean filtersUpdated=false;


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
//            image = new Image(fis);
			fis.close();
			imgProc = new ImageProcessor(image);
			imageView.setImage(image);
		} catch (IOException e) {
			e.printStackTrace();
		}
		filtersUpdated = false;
	}

	@FXML
	private void scanImage(){
		ObservableList<Node> children = imageLayers.getChildren();
		while(children.size()>1) children.remove(1);
		if(scanImageButton.isSelected()) {
			Rectangle[] rects = imgProc.getClusterBoxes();
			for (Rectangle rect : rects) {
				imageLayers.getChildren().add(rect);
			}
			countLabel.setText(imgProc.getClusterCount()+" Birds");
			if(!filtersUpdated) filterSetup();
		}
	}

	@FXML
	private void filterSize(){
		if(filterMinMax.isSelected()){ //filter by min max
			imgProc.setSizeFilterMin((int)filterMinSlider.getValue());
			imgProc.setSizeFilterMax((int)filterMaxSlider.getValue());
			scanImage();
		}
		else if(filterIQRange.isSelected()){ //filter by interquartile range
//			scanImage();
////			imgProc.filterClusterSize(-1,-1); //illegal values signify IQ range
		}
	}

	@FXML
	private void posterise(Event e){
		if(e.getSource()==posteriseSlider) posteriseButton.setSelected(true);
		if(posteriseButton.isSelected()) imageView.setImage(imgProc.posterise((int)posteriseSlider.getValue()));
		else imageView.setImage(image);
	}

	@FXML
	private void changePanel(){
		boolean visible = controlPanel.isVisible();
		controlPanel.setVisible(!visible);
		settingsPanel.setVisible(visible);
	}

	private void filterSetup(){
		int[] minMax = imgProc.getMinMaxClusterSize();
		filterMinSlider.setMin(minMax[0]);
		filterMinSlider.setMax(minMax[1]>>1);
		filterMaxSlider.setMin(minMax[1]>>2);
		filterMaxSlider.setMax(minMax[1]);
		filterMaxSlider.setValue(minMax[1]);
		filtersUpdated = true;
	}

	@FXML
	private void exit() {System.exit(0);}
}
