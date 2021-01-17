import com.zhbitcxy.Engine;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    static Engine engine;
    static String DIR_PATH = "";
    static final String INPUT_FILE_NAME = "params.txt";
    static final String OUTPUT_FILE_NAME = "out.txt";
    static final String INDEX_FILE_DIR = "index";

    static final String VERSION = "2.0.5";

    static List<Integer> resultList = new ArrayList<>();

    static List<Long> doneTimeList = new ArrayList<>();

    public static void main(String[] args) {
        String dir = System.getProperty("user.dir");
        DIR_PATH = dir.substring(0, dir.lastIndexOf("/"));
        engine = new Engine(DIR_PATH);
        long startTime = System.currentTimeMillis();
        printConfig();
        buildIndex();
        System.out.println(String.format("Build costTime(ms) : %d", (System.currentTimeMillis() - startTime) ));
        long startTime2 = System.currentTimeMillis();

        doWork();
        outputResult();
        System.out.println(String.format("Search costTime(ms) : %d", (System.currentTimeMillis() - startTime2) ));
        System.out.println(String.format("Total costTime(ms) : %d", (System.currentTimeMillis() - startTime) ));
        System.exit(0);
    }

    public static void printConfig(){
        System.out.println(String.format("Version: %s", VERSION));
        System.out.println(System.getProperty("user.dir"));
        System.out.println(String.format("max usage cpu: %d", Runtime.getRuntime().availableProcessors()));
        System.out.println("max usage memory："+ (Runtime.getRuntime().maxMemory()/1024.0/1024.0) +" M");
    }

    public static void buildIndex(){
        engine.init();
    }

    public static void doWork(){
        final String inputFilePath = DIR_PATH + "/" + INPUT_FILE_NAME;
        File file = new File(inputFilePath);

        try {
            try(BufferedReader br = new BufferedReader(new FileReader(file))){
                String line = null;

                while ((line = br.readLine()) != null){
                    long startTime = System.currentTimeMillis();
                    int result = execCommand(line);
                    long costTime = System.currentTimeMillis() - startTime;

                    resultList.add(result);
                    doneTimeList.add(costTime);
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        for (int i = 0, len = resultList.size(); i < len; i++){
            System.out.println(
                    String.format(
                            "param-%d res:%d costTime(ms):%d", (i+1), resultList.get(i), doneTimeList.get(i)));
        }

        System.out.println("end");
    }

    private static int execCommand(String commandLine){
        System.out.println(commandLine);
        return engine.find(commandLine);
    }

    public static void outputResult(){
        File file = new File(DIR_PATH + "/" + OUTPUT_FILE_NAME);
        if (file.exists()){
            file.delete();
        }
        try {
            file.createNewFile();
            try(BufferedWriter bw = new BufferedWriter(new FileWriter(file))){
                for (int i = 0, len = resultList.size(); i < len; i++){
                    bw.write(String.valueOf(resultList.get(i)));
                    if (i != len-1){
                        bw.newLine();
                    }
                }
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

}
