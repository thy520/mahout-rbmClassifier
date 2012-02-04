package org.apache.mahout.classifier.rbm;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Closeable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.apache.mahout.common.AbstractJob;
import org.apache.mahout.common.HadoopUtil;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.VectorWritable;

import com.google.common.io.Closeables;

public class MnistPreparer extends AbstractJob{

	public static void main(String[] args) throws Exception {
		if(args == null || args.length==0)
			args = new String[]{"--imagepath","/home/dirk/mnist/train-images-idx3-ubyte",
							    "--labelpath","/home/dirk/mnist/train-labels-idx1-ubyte",
						  		"--output","/home/dirk/mnist/out",
						  		"-cnr","440"};
		
	    ToolRunner.run(new Configuration(), new MnistPreparer(), args);
	}
	

	/**
	 * only processes 44.000 images like the paper [hinton,2006] proposed
	 * (http://www.cs.toronto.edu/~hinton/absps/ncfast.pdf)
	 */
	@Override
	public int run(String[] args) throws Exception {		
		addOutputOption();
		//chunknumber 600 gives nullpointer exception???
		addOption("chunknumber","cnr","number of chunks to be created",true);
		addOption("labelpath","l","path to the label file",true);
		addOption("imagepath","i","path to image file",true);
		addOption("size","s","number of pairs to be processed",true);

		Map<String, String> parsedArgs = parseArguments(args);
	    if (parsedArgs == null) {
	      return -1;
	    }
	    
	 
	    Path output = getOutputPath(); 
	    
	    FileSystem fileSystem = output.getFileSystem(getConf());	    
	    HadoopUtil.delete(getConf(), getOutputPath());
	    
	    fileSystem.mkdirs(output);
	    
	    DataInputStream dataReader = new DataInputStream(
	    		new FileInputStream(new File(getOption("imagepath"))));
	    DataInputStream labelReader = new DataInputStream(
	    		new FileInputStream(new File(getOption("labelpath"))));
	    
    
    	labelReader.skipBytes(8);
    	dataReader.skipBytes(16);
    	int label;
    	IntWritable labelVector = new IntWritable();
    	VectorWritable imageVector = new VectorWritable(new DenseVector(28*28));

    	double[] pixels=new double[28*28];
    	
    	Integer chunks = Integer.parseInt(getOption("chunknumber"));
    	Integer size = Integer.parseInt(getOption("size"));

    	SequenceFile.Writer[] writer = new SequenceFile.Writer[chunks];
    	int writernr=0;
    	Integer closedwriters = 0;
    	int cntr = 0;

    	
    	//counter for the ten labels, each batch should have size/chunks /10(labels) examples of each label
		Integer[][] batches = new Integer[chunks][10];
		for (int i = 0; i < batches.length; i++) {
			for(int j=0; j<10; j++)
				batches[i][j]=size/(10*chunks);
		}
    	
    	try {
	    	while(cntr<size) {
	    		writernr =-1;
	    		label = labelReader.readUnsignedByte();
	    		labelVector.set(label);
	    		for (int i = 0; i < pixels.length; i++) {
					pixels[i]=Double.valueOf(String.valueOf(dataReader.readUnsignedByte()))/255.0;
				}
	    		for(int i = closedwriters; i<chunks; i++) {
	    			if(batches[i][label]>0) {
	    				writernr = i;
	    				//open writers only when they are needed
	    				if(writer[writernr]==null)
	    					writer[writernr] = new Writer(fileSystem, getConf(), new Path(output,"chunk"+i), IntWritable.class, VectorWritable.class);
	    				break;
	    			} else
	    				//close writers, that are opened, yet finished
	    				for(int j=0;j<10;j++) {
	    					if(batches[i][j]!=0)
	    						break;
	    					if(j==9){
	    						writer[i].close();
	    						closedwriters++;
	    					}
	    				}
	    		}
	    		
	    		cntr++;
	    		if(cntr%1000==0)
	    			Logger.getLogger(this.getClass()).info(cntr+" processed pairs");
	    		
	    		if(closedwriters>=chunks)
	    			break;
	    		if(writernr==-1)
	    			continue;
	    		
	    		imageVector.get().assign(pixels);
	    		writer[writernr].append(labelVector, imageVector);	    		
	    		
	    		batches[writernr][label]--;
	    	}
    	}
    	catch(EOFException ex){
    		//close last writer
    		Closeables.closeQuietly(writer[writernr]);
    	}
    	
    	Closeables.closeQuietly(writer[writernr]);
    	
		return 0;
	}
	
}
