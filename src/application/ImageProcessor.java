package application;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.Arrays;

public class ImageProcessor {

	private Image image;
	private int width;
	private int height;
	private int[] sets;
	private int[] clusters;
	private int posteriseCutoff = 100;
	private int sizeFilterMin;
	private int sizeFilterMax;


	public ImageProcessor(Image image){
		this.image = image;
		width = (int) image.getWidth();
		height = (int) image.getHeight();
		sizeFilterMax=height*width;
	}

	public Image posterise(int cutoff){
		this.posteriseCutoff = cutoff;
		WritableImage wImage = new WritableImage(width, height);
		PixelReader pixelReader = image.getPixelReader();
		PixelWriter pixelWriter = wImage.getPixelWriter();
		for (int y=0;y<height;y++) {
			for (int x = 0; x < width; x++) {
				int argb = pixelReader.getArgb(x, y);
				int r = (argb >> 16) & 0xff;
				int g = (argb >> 8) & 0xff;
				int b = argb & 0xff;
				argb = (r < cutoff | g < cutoff | b < cutoff) ? 0xff000000 : 0xffffffff;
				pixelWriter.setArgb(x, y, argb);
			}
		}
		return wImage;
	}

	public int[] findClusters(){
//		if(clusters==null) { //only process image on first run, result is saved to clusters[] for later requests
			sets = new int[width * height];
			for (int i=0;i<sets.length;i++) sets[i] = i; //default set to 0,1,2,3...
			PixelReader pixelReader = posterise(posteriseCutoff).getPixelReader();
			for (int y=0;y<height;y++) {
				for (int x=0;x<width;x++) {
					int index = y*width + x;
					int argb = pixelReader.getArgb(x, y);
					if (argb == 0xff000000) { //if black pixel found
						if (x < width-1 && pixelReader.getArgb(x + 1, y) == 0xff000000) { //if pixel to the right is black, check x<width-1 to stop at image boundary
							if (sets[index + 1] != index + 1) { //if pixel is in existing set
								int currentRoot = DisjointSets.find(sets, index);
								int existingRoot = DisjointSets.find(sets, index+1);
								DisjointSets.union(sets, (existingRoot<currentRoot) ? currentRoot : existingRoot,
										(existingRoot<currentRoot) ? existingRoot : currentRoot);
								//unions sets based on the root always being the first element of the set
							} else {
								sets[index + 1] = sets[index]; //unite right pixel into current set
							}
						}
						if (y < height - 1 && pixelReader.getArgb(x, y + 1) == 0xff000000)
							sets[index + width] = sets[index]; //if pixel below is black, unite into current set
					} else {
						sets[y * width + x] = -1; //mark white pixel to be ignored
					}
				}
			}
			int count = 0;
			int[] recordedSets = new int[sets.length / 2]; //length is maximum num of unique values possible
			Arrays.fill(recordedSets, -1); //default to invalid array index
			int pos = 0;
			for(int i : sets) {
				if (i != -1) {
					int root = DisjointSets.find(sets, i);
					if (Arrays.stream(recordedSets).noneMatch(x -> x == root)) { //if set not already recorded
						recordedSets[count++] = root;
					}
					sets[pos] = root; //update element in set to be root for simplicity
				}
				pos++;
			}
			int[] setsShortlist = new int[count];
			int i = 0;
			while (recordedSets[i] != -1) setsShortlist[i] = recordedSets[i++];
			clusters = setsShortlist;
//		}
		return clusters;
	}

	public void filterClusterSize(int min, int max){
		if(min>0 || max<width*height) {
			int rootsToBeRemoved = 0;
			int pos=0;
			for (int root : clusters) {
				int[] subset = getClusterSubset(root);
				int size = DisjointSets.size(subset, root);
				if (size < min || size > max) {
					clusters[pos] = -root; //mark root for removal
					rootsToBeRemoved++;
				}
				pos++;
			}
			int[] newClusters = new int[clusters.length - rootsToBeRemoved];
			pos = 0;
			for (int root : clusters) {
				if (root > -1) newClusters[pos++] = root;
			}
			clusters = newClusters;
		}
	}

	private int[] getClusterSubset(int root){ //gets a subset of sets[] which only contains the pixels within a clusters edges
		int[] edges = determineClusterEdges(root);
		int[] subset = new int[(edges[1]-edges[3]+1)*(edges[2]-edges[0]+1)];
		for(int y=edges[0];y<=edges[2];y++){
			for(int x=edges[3];x<=edges[1];x++){
				subset[(y-edges[0])*(edges[1]-edges[3]+1)+x-edges[3]]=sets[y*width+x];
			}
		}
		return subset;
	}

	public Rectangle[] getClusterBoxes(){
		findClusters();
		filterClusterSize(sizeFilterMin, sizeFilterMax);
		Rectangle[] boxes = new Rectangle[clusters.length];
		int i=0;
		for(int root:clusters){
			boxes[i++] = boxCluster(determineClusterEdges(root));
		}
		return boxes;
	}

	private Rectangle boxCluster(int[] edges){
		Rectangle rectangle = new Rectangle(edges[3],edges[0],edges[1]-edges[3],edges[2]-edges[0]);
		rectangle.setFill(null);
		rectangle.setStroke(Color.BLUE);
		return rectangle;
	}

	private int[] determineClusterEdges(int root){
		int[] edges = new int[4]; //top, right, bottom, left
		edges[3] = width;
		int pos=root;
		edges[0] = root/width; //first pixel is top edge
		for(int i=root;i<sets.length;i++){
			if(sets[i]==root){ //if black pixel is in set
				if(pos%width>edges[1]) edges[1] = pos%width; //right edge
				if(pos/width>edges[2]) edges[2] = pos/width; //bottom edge
				if(pos%width<edges[3]) edges[3] = pos%width; //left edge
			}
			if((pos%width)>edges[1] && (pos/width)>edges[2]) return edges; //if current pos has white margin around cluster, entire cluster has been processed
			pos++;
		}
		return edges;
	}

	public int getClusterCount(){
		return clusters.length;
	}

	public int[] getMinMaxClusterSize(){
		int[] minMax = new int[2];
		minMax[0]=height*width;
		for(int root:clusters){
			int size = DisjointSets.size(getClusterSubset(root), root);
			if(size<minMax[0]) minMax[0]=size;
			if(size>minMax[1]) minMax[1]=size;
		}
		return minMax;
	}

	public void setSizeFilterMin(int min){
		sizeFilterMin=min;
	}

	public void setSizeFilterMax(int max){
		sizeFilterMax=max;
	}

	private static class DisjointSets{

		private static int find(int[] sets, int id) {
			return sets[id]==id?id: find(sets,sets[id]); //find the root of any element
		}

		private static void union(int[] sets, int childSet, int parentSet){
			sets[find(sets,childSet)]=find(sets,parentSet);
		}

		private static int size(int[] sets, int root){ //gets the size of a disjoint set
			int size = 0;
			for(int i:sets){
				if(i==root) size++; //if root of set is root i.e. is a subset
			}
			return size;
		}
	}
}
