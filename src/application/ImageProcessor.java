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
	private int sizeFilterMin, sizeFilterMax;
	private int clusterMinSize, clusterMaxSize;
	private double minLineLengthFactor = 0.3;
	private double marginFactor = 1.5;


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

	public void findClusters(){
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
	}

	public void filterClusterSize(int min, int max){
		if(min!=-1 && (min>clusterMinSize || max<clusterMaxSize)) { //filter by min/max
			int rootsToBeRemoved = 0;
			int pos=0;
			for (int root : clusters) {
				int size = getClusterSize(root);
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
		if(min==-1 && max>0){ //filter by average size
			int[] sizes = new int[clusters.length];
			for(int i=0;i<clusters.length;i++){
				sizes[i] = getClusterSize(clusters[i]);
			}
			int[][] rootsWithSizes = {clusters, sizes};
			quickSortBySize(rootsWithSizes, 0, clusters.length-1);
			int[] newClusters = new int[clusters.length-(2*max)];
			int pos = 0;
			for(int i=max;i<clusters.length-max;i++) newClusters[pos++] = clusters[i]; //clusters represents deviance from average
			clusters = newClusters;
		}
	}

	public int[][][] findFormation(){
		if(clusters.length==1) return null;
		if(clusters.length==2){
			return new int[][][]{new int[][]{getClusterCenterpoint(clusters[0]),getClusterCenterpoint(clusters[1])},null};
		}
		//find average bird size
		int totalHeight = 0;
		int totalWidth = 0;
		for(int cluster:clusters){
			int[] edges = getClusterEdges(cluster);
			totalHeight += edges[2]-edges[0];
			totalWidth += edges[1]-edges[3];
		}
		int avgHeight = totalHeight/clusters.length;
		int avgWidth = totalWidth/clusters.length;
		//find longest line
		int[][] firstLine = new int[clusters.length][2];
		int mostNumOnLine = 0;
		for(int i=0;i<clusters.length-(int)(i+minLineLengthFactor*clusters.length);i++){
			for(int j = (int)(i+minLineLengthFactor*clusters.length); j<clusters.length; j++){ //scans such that distance between clusters is not less than min line size
				int[][] clustersOnLine = getClustersOnLine(clusters, i, j, marginFactor*avgWidth, marginFactor*avgHeight);
				int numLineClusters = 0;
				while(numLineClusters<clustersOnLine.length&&clustersOnLine[numLineClusters][0]!=0) numLineClusters++;
				if(numLineClusters>mostNumOnLine){
					firstLine = clustersOnLine;
					mostNumOnLine = numLineClusters;
				}
			}
		}
		firstLine = Arrays.copyOf(firstLine, mostNumOnLine);
		if(firstLine[0][0]==0) return null; //if line containing >(minLineFactor)% of flock does not exist
		int[] remainingClusters = new int[clusters.length-mostNumOnLine+1]; //all remaining clusters including one endpoint of first line
		int pos=1;
		for(int i=0;i<clusters.length;i++) {
			int[] cluster = getClusterCenterpoint(clusters[i]);
			if(Arrays.stream(firstLine).noneMatch(x -> Arrays.equals(x,cluster))) remainingClusters[pos++] = clusters[i];
		}
		//find longest remaining line
		int[][] secondLine = new int[remainingClusters.length+1][2];
		mostNumOnLine = 0;
		for(int e=0;e<2;e++) { //for each endpoint on first line
			remainingClusters[0] = lookupClusterCoordiantes(firstLine[e*(firstLine.length-1)]);
			for (int i = 1; i < remainingClusters.length; i++) {
				int[][] clustersOnLine = getClustersOnLine(remainingClusters, 0, i, marginFactor * avgWidth, marginFactor * avgHeight);
				int numLineClusters = 0;
				while (numLineClusters<clustersOnLine.length && clustersOnLine[numLineClusters][0] != 0) numLineClusters++; //get numLineClusters
				if (numLineClusters > mostNumOnLine) {
					secondLine = clustersOnLine;
					mostNumOnLine = numLineClusters;
				}
			}
		}
		if(mostNumOnLine<minLineLengthFactor*clusters.length){ //second line found is not of sufficient length
			return new int[][][]{firstLine,null};
		}
		//combine lines
		secondLine = Arrays.copyOf(secondLine, mostNumOnLine);
		return new int[][][]{firstLine,secondLine};
	}

	private int[][] getClustersOnLine(int[] clusters, int startIndex, int endIndex, double xMargin, double yMargin){
		int startCluster = clusters[startIndex];
		int endCluster = clusters[endIndex];
		int[][] clustersOnLine = new int[clusters.length][2];
		int numLineClusters = 1;
		//find endpoints
		int[] startpoint = getClusterCenterpoint(startCluster);
		int[] endpoint = getClusterCenterpoint(endCluster);
		//find line between endpoints
		int x1 = startpoint[0];
		int y1 = startpoint[1];
		int x2 = endpoint[0];
		int y2 = endpoint[1];
		float m = (float)(y2-y1)/(x2-x1);
		int c = (int) (y1 - (m*x1));
		clustersOnLine[0] = startpoint;
		for(int k=startIndex+1;k<endIndex;k++){ //for each cluster between endpoints
			int[] centerpoint = getClusterCenterpoint(clusters[k]);
			//slope is infinite
			if(x2-x1 == 0) {
				if (centerpoint[0] > x1 - xMargin && centerpoint[0] < x1 + xMargin)
					clustersOnLine[numLineClusters++] = centerpoint;
			}
			else {
				int yCalc = (int) (m * centerpoint[0] + c);
				if (centerpoint[1] > yCalc - yMargin && centerpoint[1] < yCalc + yMargin) {
					clustersOnLine[numLineClusters++] = centerpoint;
				} else {
					int xCalc = (int) ((centerpoint[1] - c) / m);
					if (centerpoint[0] > xCalc - xMargin && centerpoint[0] < xCalc + xMargin)
						clustersOnLine[numLineClusters++] = centerpoint;
				}
			}
		}
		clustersOnLine[numLineClusters++] = endpoint;
		clustersOnLine = Arrays.copyOf(clustersOnLine,numLineClusters);
		if(x2-x1 != 0) return rearrangeLineClusters(clustersOnLine,numLineClusters,m);
		return clustersOnLine;
	}

	private int[][] rearrangeLineClusters(int[][] clusterCoordinates, int numLineClusters, float slope){
		//place geometric endpoints at extremes
		int[] lowestX = {width, 0};
		int[] lowestY = {height, 0};
		int[] highestX = {0, 0};
		int[] highestY = {0, 0};
		for(int i=0;i<clusterCoordinates.length;i++){
			int[] coords = clusterCoordinates[i];
			if(coords[0]<lowestX[0]) {
				lowestX[0] = coords[0];
				lowestX[1] = i;
			}
			if(coords[0]>highestX[0]) {
				highestX[0] = coords[0];
				highestX[1] = i;
			}
			if(coords[1]<lowestY[0]) {
				lowestY[0] = coords[1];
				lowestY[1] = i;
			}
			if(coords[1]>highestY[0]) {
				highestY[0] = coords[1];
				highestY[1] = i;
			}
		}
		int[] endpoint1 = new int[2];
		int[] endpoint2 = new int[2];
		int[] swap = new int[0];
		if(slope>0){ //slope is positive
			if(slope>1){//steep
				endpoint1 = clusterCoordinates[lowestY[1]];
				endpoint2 = clusterCoordinates[highestY[1]];
				swap = new int[]{lowestY[1], highestY[1]};
			}
			else if(slope<=1){//slight
				endpoint1 = clusterCoordinates[lowestX[1]];
				endpoint2 = clusterCoordinates[highestX[1]];
				swap = new int[]{lowestX[1], highestX[1]};
			}
		}
		else if(slope<0){ //slope is negative
			if(slope<-1){//steep
				endpoint1 = clusterCoordinates[lowestY[1]];
				endpoint2 = clusterCoordinates[highestY[1]];
				swap = new int[]{lowestY[1], highestY[1]};
			}
			else if(slope>=-1){//slight
				endpoint1 = clusterCoordinates[lowestX[1]];
				endpoint2 = clusterCoordinates[highestX[1]];
				swap = new int[]{lowestX[1], highestX[1]};
			}
		}
		if(slope!=0) {
			clusterCoordinates[swap[0]] = clusterCoordinates[0];
			clusterCoordinates[0] = endpoint1;
			clusterCoordinates[swap[1]] = clusterCoordinates[numLineClusters-1];
			clusterCoordinates[numLineClusters-1] = endpoint2;
		}
		return clusterCoordinates;
	}

	private int[] getClusterCenterpoint(int root){
		int[] coords = new int[2];
		int[] edges = getClusterEdges(root);
		coords[0] = edges[1]-(edges[1]-edges[3])/2; //x coord
		coords[1] = edges[2]-(edges[2]-edges[0])/2; //y coord
		return coords;
	}

	private int[] getClusterSubset(int root){ //gets a subset of sets[] which only contains the pixels within a clusters edges
		int[] edges = getClusterEdges(root);
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
			boxes[i++] = boxCluster(getClusterEdges(root));
		}
		return boxes;
	}

	private Rectangle boxCluster(int[] edges){
		Rectangle rectangle = new Rectangle(edges[3],edges[0],edges[1]-edges[3],edges[2]-edges[0]);
		rectangle.setFill(null);
		rectangle.setStroke(Color.BLUE);
		return rectangle;
	}

	private int[] getClusterEdges(int root){
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
			if((pos%width)>edges[1] && (pos/width)>edges[2])
				return edges; //if current pos has white margin around cluster, entire cluster has been processed
			pos++;
		}
		return edges;
	}

	private int lookupClusterCoordiantes(int[] coords){
		if(sets[width*coords[1]+coords[0]]==-1){
			coords[0]++;
			lookupClusterCoordiantes(coords);
		}
		return DisjointSets.find(sets, sets[width*coords[1]+coords[0]]);
	}

	private int getClusterSize(int root){ return DisjointSets.size(getClusterSubset(root), root);}

	public int getClusterCount(){
		return clusters.length;
	}

	public int[] getMinMaxClusterSize(){
		int[] minMax = new int[2];
		minMax[0]=height*width;
		for(int root:clusters){
			int size = getClusterSize(root);
			if(size<minMax[0]) minMax[0]=size;
			if(size>minMax[1]) minMax[1]=size;
		}
		clusterMinSize = minMax[0];
		clusterMaxSize = minMax[1];
		return minMax;
	}

	public void setSizeFilterMin(int min){
		sizeFilterMin=min;
	}

	public void setSizeFilterMax(int max){
		sizeFilterMax=max;
	}

	private void quickSortBySize(int[][] a, int lowerIndex, int higherIndex){
		//2d array [0][i] contains cluster root [1][i] contains cluster size
		int leftIndex = lowerIndex;
		int rightIndex = higherIndex;
		int pivot = a[1][lowerIndex+(higherIndex-lowerIndex)/2];
		while(leftIndex<=rightIndex){
			while(a[1][leftIndex]<pivot) leftIndex++;
			while(a[1][rightIndex]>pivot) rightIndex--;
			if(leftIndex<=rightIndex){
				int[] swap = new int[] {a[0][leftIndex], a[1][leftIndex]};
				a[0][leftIndex] = a[0][rightIndex];
				a[1][leftIndex] = a[1][rightIndex];
				a[0][rightIndex] = swap[0];
				a[1][rightIndex] = swap[1];
				leftIndex++;
				rightIndex--;
			}
		}
		if(lowerIndex<rightIndex) quickSortBySize(a, lowerIndex, rightIndex);
		if(leftIndex<higherIndex) quickSortBySize(a, leftIndex, higherIndex);
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
