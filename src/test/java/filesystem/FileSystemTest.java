package filesystem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemTest {

    private FileSystem fileSystem;

    @BeforeEach
    void setUp() throws IOException {
        // Initialize the FileSystem with 3 disks for RAID 0
        fileSystem = new FileSystem(3);
    }

    @Test
    void create() throws IOException {
        int fd = fileSystem.create("testFile");
        assertEquals(0, fd, "The file descriptor should be 0 for the very first file");
    }

    @Test
    void delete() throws IOException {
        fileSystem.create("testFile");
        System.out.println("File created successfully.");
        fileSystem.delete("testFile");
        System.out.println("File deleted successfully.");
        Exception exception = assertThrows(IOException.class, () -> fileSystem.open("testFile"));
        assertTrue(exception.getMessage().contains("FileSystem::open: File not found"));
    }

    @Test
    void open() throws IOException {
        fileSystem.create("testFile");
        int fd = fileSystem.open("testFile");
        assertEquals(0, fd, "The file descriptor should be 0 for the opened file");
    }

    @Test
    void close() throws IOException {
        fileSystem.create("testFile");
        int fd = fileSystem.open("testFile");
        assertDoesNotThrow(() -> fileSystem.close(fd), "Closing the file should not throw an exception");
    }

    @Test
    void read() throws IOException {
        fileSystem.create("testFile");
        int fd = fileSystem.open("testFile");
        fileSystem.write(fd, "Omg Hello RAID 0!!!");
        String content = fileSystem.read(fd);
        assertEquals("Omg Hello RAID 0!!!", content, "Read content should match the written content");
    }

    @Test
    void write() throws IOException {
        fileSystem.create("testFile");
        int fd = fileSystem.open("testFile");
        assertDoesNotThrow(() -> fileSystem.write(fd, "Testing the  write functionality!"),
                "Writing to the file should not throw an exception");
    }

    @Test
    void createDuplicateFileThrowsException() throws IOException {
        fileSystem.create("testFile");
        Exception exception = assertThrows(IOException.class, () -> fileSystem.create("testFile"));
        assertTrue(exception.getMessage().contains("already exists"),
                "Creating a duplicate file should throw an exception indicating it already exists");
    }

    @Test
    void testAllocateBlocksForFile() throws IOException {
        System.out.println(" Currently Testing allocateBlocksForFile...");
        // Simulated inode number
        int iNodeNumber = 0;
        // Allocate space for 1024 bytes (2 blocks of 512 bytes each)
        int numBytes = 1024;
        int[] allocatedBlocks = fileSystem.allocateBlocksForFile(iNodeNumber, numBytes);

        // Assert that the blocks are allocated
        assertNotNull(allocatedBlocks, "Allocated blocks should not be null");
        assertEquals(2, allocatedBlocks.length, "Should allocate 2 blocks for 1024 bytes");

        // Print allocated blocks for debugging
        System.out.println("Allocated blocks:");
        for (int block : allocatedBlocks) {
            System.out.println("Block: " + block);
        }
    }

    @Test
    void testDeallocateBlocksForFile() throws IOException {
        System.out.println("Currently Testing deallocateBlocksForFile...");
        int iNodeNumber = 0; // Simulated inode number
        int numBytes = 1024; // Allocate and then deallocate 1024 bytes

        // Allocate first
        fileSystem.allocateBlocksForFile(iNodeNumber, numBytes);

        // Deallocate
        fileSystem.deallocateBlocksForFile(iNodeNumber);

        // Verify inode is reset
        INode inode = fileSystem.diskDevice.readInode(iNodeNumber);
        assertEquals(0, inode.getSize(), "File size should be reset to 0");
        for (int i = 0; i < INode.NUM_BLOCK_POINTERS; i++) {
            assertEquals(-1, inode.getBlockPointer(i), "Block pointers should be reset to -1");
        }
    }

    //This is printing out the result for the output of read
    @Test
    void testRead() throws IOException {
        System.out.println("Currently Testing read...");
        int fd = fileSystem.create("testFile");
        String data = "Hello, RAID 0!";
        fileSystem.write(fd, data);

        String readData = fileSystem.read(fd);

        // Assert that the read data matches the written data
        assertEquals(data, readData, "Read data should match written data");

        System.out.println("Read operation completed successfully.");
    }

    @Test
    void testWrite() throws IOException {
        System.out.println("Currently Testing write...");
        int fd = fileSystem.create("testFile");
        String data = "Hello, RAID 0!";
        fileSystem.write(fd, data);

        // Assert that the inode reflects the correct size
        INode inode = fileSystem.diskDevice.readInode(fd);
        assertEquals(data.length(), inode.getSize(), "Inode size should match written data size");

        System.out.println("Write operation completed successfully.");
    }
}
