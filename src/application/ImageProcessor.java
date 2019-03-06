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

	public int[][] findFormation(){
		if(clusters.length==1) return null;
		if(clusters.length==2){
			//return coords of two clusters
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
		int[][] mostClustersOnLine = new int[clusters.length][2];
		int mostNumOnLine = 0;
		int[][] mostClustersInDelta = new int[clusters.length][2];
		int mostNumInDelta = 0;
		for(int i=0;i<clusters.length-2;i++){
			for(int j = (int)(i+0.4*clusters.length); j<clusters.length; j++){
				int[][] clustersOnLine = getClustersOnLine(i, j, 1.5*avgWidth, 1.5*avgHeight);
				int numLineClusters = 0;
				while(clustersOnLine[numLineClusters][0]!=0) numLineClusters++;
				if(numLineClusters>mostNumOnLine){
					mostClustersOnLine = clustersOnLine;
					mostNumOnLine = numLineClusters;
				}
			}
		}
		int[][] trimmedClusters = new int[mostNumOnLine][2];
		for(int i=0;i<mostNumOnLine;i++) trimmedClusters[i] = mostClustersOnLine[i];
		return trimmedClusters;
	}


	private int[][] getClustersOnLine(int startCluster, int endCluster, double xMargin, double yMargin){
		int[][] clustersOnLine = new int[clusters.length][2];
		int numLineClusters = 1;
		//find endpoints
		int[] endpoint1 = getClusterCenterpoint(clusters[startCluster]);
		int[] endpoint2 = getClusterCenterpoint(clusters[endCluster]);
		//find line between endpoints
		int x1 = endpoint1[0];
		int y1 = endpoint1[1];
		int x2 = endpoint2[0];
		int y2 = endpoint2[1];
		float m = (float)(y2-y1)/(x2-x1);
		int c = (int) (y1 - (m*x1));
		clustersOnLine[0] = endpoint1;
		for(int k=startCluster+1;k<endCluster;k++){ //for each cluster between endpoints
			int[] centerpoint = getClusterCenterpoint(clusters[k]);
			int yCalc = (int) (m*centerpoint[0]+c);
			if(centerpoint[1] > yCalc-yMargin && centerpoint[1] < yCalc+yMargin) {
				clustersOnLine[numLineClusters++] = centerpoint;
			} else {
				int xCalc = (int) ((centerpoint[1]-c)/m);
				if(centerpoint[0] > xCalc-xMargin && centerpoint[0] < xCalc+xMargin) clustersOnLine[numLineClusters++] = centerpoint;
			}
		}
		clustersOnLine[numLineClusters] = endpoint2;
		return clustersOnLine;
	}


	/*if(clusters.length==1) return null;
		if(clusters.length==2){
		//return coords of two clusters
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
	int[][] mostClustersOnLine = new int[clusters.length][2];
	int mostNumOnLine = 0;
		for(int i=0;i<clusters.length-2;i++){
		for(int j = (int)(i+0.7*clusters.length); j<clusters.length; j++){
			int[][] clustersOnLine = new int[clusters.length][2];
			int numClusters = 1;
			//find endpoints
			int[] endpoint1 = getClusterCenterpoint(clusters[i]);
			int[] endpoint2 = getClusterCenterpoint(clusters[j]);
			//find line between endpoints
			int x1 = endpoint1[0];
			int y1 = endpoint1[1];
			int x2 = endpoint2[0];
			int y2 = endpoint2[1];
			float m = (float)(y2-y1)/(x2-x1);
			int c = (int) (y1 - (m*x1));
			clustersOnLine[0] = endpoint1;
			for(int k=i+1;k<j;k++){ //for each cluster between endpoints
				int[] centerpoint = getClusterCenterpoint(clusters[k]);
				int yCalc = (int) (m*centerpoint[0]+c);
				if(centerpoint[1] > yCalc-1.5*avgHeight && centerpoint[1] < yCalc+1.5*avgHeight) {
					clustersOnLine[numClusters++] = centerpoint;
				} else {
					int xCalc = (int) ((centerpoint[1]-c)/m);
					if(centerpoint[0] > xCalc-1.5*avgWidth && centerpoint[0] < xCalc+1.5*avgWidth) clustersOnLine[numClusters++] = centerpoint;
				}
			}
			clustersOnLine[numClusters++] = endpoint2;
			//line has been found
			if(numClusters>mostNumOnLine){
				mostClustersOnLine = clustersOnLine;
				mostNumOnLine = numClusters;
			}
		}
	}
	int[][] trimmedClusters = new int[mostNumOnLine][2];
		for(int i=0;i<mostNumOnLine;i++) trimmedClusters[i] = mostClustersOnLine[i];
		return trimmedClusters;*/

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
			if((pos%width)>edges[1] && (pos/width)>edges[2]) return edges; //if current pos has white margin around cluster, entire cluster has been processed
			pos++;
		}
		return edges;
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
