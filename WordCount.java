import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TreeSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class WordCount {

	public static TreeSet<ResultPair> sortedOutput = new TreeSet<>();

	public static class myMapper extends Mapper <LongWritable, Text, Text, LongWritable> {

		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

			String[] line = value.toString().trim().split("\\s+");
			for(String word: line) {
				if(word.matches("^\\w+$")) {
					context.write(new Text(word.trim() + " " + "&"), new LongWritable(1));
				}
			}

			for (int index = 0; index < line.length; index++) {
				if (index == line.length - 1) {
					break;
				} else {
					if (line[index].matches("^\\w+$") && line[index + 1].matches("^\\w+$")) {
						String word = line[index] + " " + line[index + 1];
						context.write(new Text(word), new LongWritable(1));
					}
				}
			}

		}

	}

	public static class Combiner extends Reducer<Text, LongWritable, Text, LongWritable> {

		public void reduce(Text key, Iterable<LongWritable> values, Context con) throws IOException, InterruptedException {

			long freq = 0;
			for (LongWritable val : values) {
				freq += val.get();
			}
			con.write(key, new LongWritable(freq));
		}

	}

	public static void main(String[] args) throws Exception {

		Job conf = Job.getInstance(new Configuration());
		conf.setJarByClass(WordCount.class);

		FileInputFormat.setInputPaths(conf, new Path(args[0]));
		conf.setInputFormatClass(TextInputFormat.class);

		conf.setMapperClass(myMapper.class);
		conf.setCombinerClass(Combiner.class);
		conf.setReducerClass(myReducer.class);

		FileOutputFormat.setOutputPath(conf, new Path(args[1]));
		conf.setMapOutputKeyClass(Text.class);
		conf.setMapOutputValueClass(LongWritable.class);

		conf.setOutputFormatClass(TextOutputFormat.class);
		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(Text.class);

		conf.waitForCompletion(true);

		File output = new File(args[1] + "/output.txt");
		output.createNewFile();
		FileWriter Filewrite = new FileWriter(output);
		for (ResultPair val : sortedOutput) {
			Filewrite.write(val.key + " : " + val.value + " : " + val.relFreq + "\n");
		}
		Filewrite.close();

	}
	
	public static class ResultPair implements Comparable<ResultPair>  {

		double relFreq;
		double count;
		String key;
		String value;

		ResultPair(double relFreq, double count, String key, String value) {
			this.relFreq = relFreq;
			this.count = count;
			this.key = key;
			this.value = value;
		}

		@Override
		public int compareTo(ResultPair resultPair) {
			if (this.count <= resultPair.count) {
				return 1;
			} 
			else {
				return -1;
			}
		}
	}

	public static class myReducer extends Reducer <Text, LongWritable,Text, Text> {

		DoubleWritable freq = new DoubleWritable();
		DoubleWritable relFreq = new DoubleWritable();
		Text word = new Text("");

		@Override
		public void reduce(Text key, Iterable<LongWritable> value, Context con) throws IOException, InterruptedException {

			String[] pair = key.toString().split("\\s");
			if(pair[1].equals("&")) {
				if(pair[0].equals(word.toString())) {
					freq.set(freq.get() + compFreq(value));
				}
				else { 
					word.set(pair[0]);
					freq.set(0);
					freq.set(compFreq(value));
				}
			}
			else {
				double freq = compFreq(value);
				if (freq != 1) {
					relFreq.set((double)freq / this.freq.get());
					double rel = relFreq.get();

					if(rel == 1.0d) {
						sortedOutput.add(new ResultPair(rel, freq, key.toString(), word.toString()));

						if(sortedOutput.size() > 100) {
							sortedOutput.pollLast();
						}
						con.write(key, new Text(Double.toString(rel)));
					}
				}

			}

		}

		public double compFreq(Iterable<LongWritable> value) {
			double freq = 0;

			for (LongWritable val : value) {
				freq+= val.get();
			}
			return freq;
		}

	}

}