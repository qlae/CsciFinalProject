package filesystem;

import java.io.IOException;

public class Main {
    public static final int NUM_LINES = 100;
    public static final String testData = "This is some text ";

    public static void main(String[] args) {
        try {

            //THis Initializes FileSystem with 2 disks and this must be set
            FileSystem fs = new FileSystem(2);
            System.out.println("FileSystem initialized with 2 disks for RAID 0.");


            //This will Create and write to files
            System.out.println("Creating and writing files...");
            String fileNameBase = "file";
            String fileName;
            String theMessage;

            for (int i = 0; i < NUM_LINES; i++) {
                fileName = fileNameBase + i + ".txt";
                int fd = fs.create(fileName);
                theMessage = "";
                for (int j = 0; j < i + 1; j++) {
                    theMessage = theMessage.concat(testData + j + ".  ");
                }
                System.out.println("Writing to file: " + fileName);
                fs.write(fd, theMessage);
                fs.close(fd);
            }

            /**
             * Delete every 2nd file
             */
            System.out.println("Deleting every 2nd file...");
            for (int i = 0; i < NUM_LINES; i += 2) {
                fileName = fileNameBase + i + ".txt";
                System.out.println("Deleting file: " + fileName);
                fs.delete(fileName);
            }
            /**
             *This shows that it Reads the remaining files
             */

            System.out.println("Reading remaining files...");
            for (int i = 1; i < NUM_LINES; i += 2) {
                fileName = fileNameBase + i + ".txt";
                int fd = fs.open(fileName);
                String message = fs.read(fd);
                System.out.println("******************");
                System.out.println("Printing file: " + fileName);
                System.out.println("------------------------------------");
                System.out.println(message);
                System.out.println("******************");
                fs.close(fd);
            }

            System.out.println("All operations completed successfully!");

        } catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
