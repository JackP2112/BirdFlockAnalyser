package application;

import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class ViewerController {

	@FXML private ImageView imageView;
	@FXML private  Pane imageLayers;
	@FXML private Label countLabel;
	@FXML private ToggleButton scanImageButton, posteriseButton;
	@FXML private Button settingsButton;
	@FXML private HBox controlPanel, settingsPanel;
	@FXML private Slider posteriseSlider, filterMinSlider, filterMaxSlider, filterAvgSlider;
	@FXML private RadioButton filterMinMax, filterAvgSize;
	private Image image;
	private ImageProcessor imgProc;
	private boolean filtersUpdated;


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
		countLabel.setText("");
		posteriseButton.setSelected(false);
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
		scanImageButton.setDisable(false);
		posteriseButton.setDisable(false);
		filtersUpdated = false;
	}

	@FXML
	private void scanImage(){
		ObservableList<Node> children = imageLayers.getChildren();
		while(children.size()>1) children.remove(1);
		if(scanImageButton.isSelected()) {
			Rectangle[] rects = imgProc.getClusterBoxes();
			int clusterNo = 1;
			for (Rectangle rect : rects) {
				imageLayers.getChildren().add(rect);
				imageLayers.getChildren().add(new Text(rect.getX()+rect.getWidth(), rect.getY(), Integer.toString(clusterNo++)));
			}
			countLabel.setText(imgProc.getClusterCount()+" Birds");
			settingsButton.setDisable(false);
		}
	}

	@FXML
	private void filterSize(){
		if(!filtersUpdated) updateFilters();
		if(filterMinMax.isSelected()) {
			imgProc.setSizeFilterMin((int) filterMinSlider.getValue());
			imgProc.setSizeFilterMax((int) filterMaxSlider.getValue());
			scanImage();
		}
		else if(filterAvgSize.isSelected()){
			imgProc.setSizeFilterMin(-1); //illegal value represents filter by average size
			imgProc.setSizeFilterMax((int) filterAvgSlider.getValue()); //value represents deviance from average
			scanImage();
		}
	}

	@FXML
	private void posterise(Event e){
		if(e.getSource()==posteriseSlider) posteriseButton.setSelected(true);
		if(posteriseButton.isSelected()) {
			imageView.setImage(imgProc.posterise((int)posteriseSlider.getValue()));
			if(scanImageButton.isSelected()) { //TODO: might break filters
				imgProc.findClusters(); //throwaway to update filters
				updateFilters();
				if (filterMinMax.isSelected() || filterAvgSize.isSelected()) filterSize();
				scanImage();
			}
		}
		else imageView.setImage(image);
	}

	@FXML
	private void findFormation(){
		int[][][] lines = imgProc.findFormation();
		try{
			int[][] firstLinePoints = lines[0];
			int[][] secondLinePoints = lines[1];
			Line firstLine = new Line(firstLinePoints[0][0],firstLinePoints[0][1],firstLinePoints[firstLinePoints.length-1][0],firstLinePoints[firstLinePoints.length-1][1]);
			firstLine.setStroke(Color.RED);
			try {
				Line secondLine = new Line(secondLinePoints[0][0], secondLinePoints[0][1], secondLinePoints[secondLinePoints.length - 1][0], secondLinePoints[secondLinePoints.length - 1][1]);
				secondLine.setStroke(Color.RED);
				imageLayers.getChildren().addAll(firstLine, secondLine);
				countLabel.setText(countLabel.getText()+", Delta: "+(firstLinePoints.length+secondLinePoints.length-1));
				System.out.println("delta\n"+(firstLinePoints.length+secondLinePoints.length-1));
			}
			catch (NullPointerException e){
				imageLayers.getChildren().add(firstLine);
				countLabel.setText(countLabel.getText()+", Linear: "+firstLinePoints.length);
				System.out.println("linear\n"+firstLinePoints.length);
			}
		}
		catch (NullPointerException e){
			countLabel.setText(countLabel.getText()+", Swarm: NA");
			System.out.println("swarm");
		}
	}

	@FXML
	private void changePanel(){
		boolean visible = controlPanel.isVisible();
		controlPanel.setVisible(!visible);
		settingsPanel.setVisible(visible);
	}

	private void updateFilters(){
		//setup min/max
		int[] minMax = imgProc.getMinMaxClusterSize();
		filterMinSlider.setMin(minMax[0]);
		filterMinSlider.setMax(minMax[1]/2);
		filterMinSlider.setValue(minMax[0]);
		filterMinSlider.setBlockIncrement((minMax[1]-minMax[0])/40);
		filterMaxSlider.setMin(minMax[1]/2);
		filterMaxSlider.setMax(minMax[1]);
		filterMaxSlider.setBlockIncrement((minMax[1]-minMax[0])/40);
		filterMaxSlider.setValue(minMax[1]);
		//setup average
		filterAvgSlider.setMax(imgProc.getClusterCount()/2);
		filterAvgSlider.setValue(0);
		filtersUpdated = true;
	}

	@FXML
	private void exit() {System.exit(0);}
}
