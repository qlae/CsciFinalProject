package filesystem;

import java.io.IOException;

public class FileSystem {
    // Array of disks for RAID 0
    public Disk[] disks;
    public Disk diskDevice;
    public int iNodeNumber;
    public int fileDescriptor;
    public INode iNodeForFile;
    public int numDisks;

    public FileSystem(int numDisks) throws IOException {
        this.numDisks = numDisks;

        // Initialize the disks array for RAID 0
        this.disks = new Disk[numDisks];
        for (int i = 0; i < numDisks; i++) {
            disks[i] = new Disk();
            // Formats each disk
            disks[i].format();
        }

        diskDevice = new Disk();
        // This Format the main disk device
        diskDevice.format();
    }

    public FileSystem() throws IOException {
        this(2); // Default to a single disk
    }

    /***
     * Create a file with the name <code>fileName</code>
     *
     * @param fileName - name of the file to create
     * @throws IOException
     */
    public int create(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        boolean isCreated = false;

        for (int i = 0; i < Disk.NUM_INODES && !isCreated; i++) {
            INode tmpINode = diskDevice.readInode(i);
            String name = tmpINode.getFileName();

            if (name != null && name.trim().equals(fileName)) {
                throw new IOException("FileSystem::create: " + fileName + " already exists");
            } else if (tmpINode.getFileName() == null) {
                this.iNodeForFile = new INode();
                this.iNodeForFile.setFileName(fileName);
                this.iNodeNumber = i;
                this.fileDescriptor = i;
                isCreated = true;
                diskDevice.writeInode(this.iNodeForFile, i);
            }
        }

        if (!isCreated) {
            throw new IOException("FileSystem::create: Unable to create file");
        }

        return fileDescriptor;
    }

    /**
     * Removes the file
     *
     * @param fileName
     * @throws IOException
     */
    public void delete(String fileName) throws IOException {
        INode tmpINode = null;
        boolean isFound = false;
        int inodeNumForDeletion = -1;

        System.out.println("Attempting to delete file: " + fileName);
        /**
         * Find the non-null named inode that matches,
         * If you find it, set its file name to null
         * to indicate it is unused
         */
        for (int i = 0; i < Disk.NUM_INODES; i++) {
            tmpINode = diskDevice.readInode(i);

            String fName = tmpINode.getFileName();

            if (fName != null && fName.trim().compareTo(fileName.trim()) == 0) {
                isFound = true;
                inodeNumForDeletion = i;
                break;
            }
        }

        /***
         * If file found, go ahead and deallocate its
         * blocks and null out the filename.
         */
        if (isFound) {
            deallocateBlocksForFile(inodeNumForDeletion);
            tmpINode.setFileName(null);
            diskDevice.writeInode(tmpINode, inodeNumForDeletion);
            this.iNodeForFile = null;
            this.fileDescriptor = -1;
            this.iNodeNumber = -1;
        }


    }
    /***
     * Makes the file available for reading/writing
     *
     * @return
     * @throws IOException
     */
    public int open(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        boolean isFound = false;

        for (int i = 0; i < Disk.NUM_INODES && !isFound; i++) {
            INode tmpINode = diskDevice.readInode(i);
            String fName = tmpINode.getFileName();

            if (fName != null && fName.trim().equals(fileName.trim())) {
                isFound = true;
                this.iNodeForFile = tmpINode;
                this.fileDescriptor = i;
                this.iNodeNumber = i;
            }
        }

        if (!isFound) {
            throw new IOException("FileSystem::open: File not found");
        }

        return this.fileDescriptor;
    }
    /***
     * Closes the file
     *
     * @throws IOException If disk is not accessible for writing
     */
    public void close(int fileDescriptor) throws IOException {
        if (fileDescriptor != this.iNodeNumber) {
            throw new IOException("FileSystem::close: Invalid file descriptor"+fileDescriptor + " does not match file descriptor " +
                    "of open file");
        }

        diskDevice.writeInode(this.iNodeForFile, this.iNodeNumber);
        this.iNodeForFile = null;
        this.fileDescriptor = -1;
        this.iNodeNumber = -1;
    }
    /**
     * Reads the content of a file identified by its file descriptor.
     * This method retrieves the data stored in the blocks allocated to the file.
     *
     * @param fileDescriptor The descriptor of the file to read.
     * @return The content of the file as a string.
     * @throws IOException If the file descriptor is invalid or the file cannot be found.
     */
    public String read(int fileDescriptor) throws IOException {
        System.out.println("Now will be Attempting to read file with descriptor: " + fileDescriptor);

        if (fileDescriptor < 0 || fileDescriptor >= Disk.NUM_INODES) {
            throw new IOException("Invalid file descriptor");
        }

        INode inode = diskDevice.readInode(fileDescriptor);
        if (inode == null || inode.getFileName() == null) {
            throw new IOException("FileSystem::read: File not found");
        }

        int fileSize = inode.getSize();
        StringBuilder data = new StringBuilder();
        System.out.println("Reading file of size " + fileSize + " bytes...");

        for (int i = 0; i < INode.NUM_BLOCK_POINTERS; i++) {
            int blockPointer = inode.getBlockPointer(i);
            if (blockPointer == -1) break;

            byte[] blockData = diskDevice.readDataBlock(blockPointer);
            int bytesToRead = Math.min(fileSize - (i * Disk.BLOCK_SIZE), Disk.BLOCK_SIZE);
            data.append(new String(blockData, 0, bytesToRead));

            System.out.printf("Read block %d: %d bytes read\n", blockPointer, bytesToRead);
        }

        System.out.println("Finished reading file. Total size: " + fileSize + " bytes.");
        return data.toString();
    }

    /**
     * Writes the data to a file that is identified by its file descriptor.
     * This method allocates blocks for the file and then writes the data across disks in a  RAID 0 like setup.
     *
     * @param fileDescriptor The descriptor of the file to write to.
     * @param data           The data to write to the file.
     * @throws IOException If the file descriptor is invalid or there are not enough blocks available.
     */
    public void write(int fileDescriptor, String data) throws IOException {
        System.out.println("Status... Now Attempting to write data to file with descriptor: " + fileDescriptor);

        if (fileDescriptor < 0 || fileDescriptor >= Disk.NUM_INODES) {
            throw new IOException("Invalid file descriptor: " + fileDescriptor);
        }

        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("Data to write cannot be null or empty");
        }

        byte[] dataBytes = data.getBytes();
        int totalBlocks = (int) Math.ceil((double) dataBytes.length / Disk.BLOCK_SIZE);
        System.out.println("Data size: " + dataBytes.length + " bytes, requiring " + totalBlocks + " blocks.");

        int[] allocatedBlocks = allocateBlocksForFile(fileDescriptor, dataBytes.length);

        if (allocatedBlocks.length < totalBlocks) {
            throw new IOException("Not enough blocks available to write data");
        }

        int offset = 0;
        for (int i = 0; i < totalBlocks; i++) {

            // Disk for this stripe
            int diskIndex = i % numDisks;
            int blockPointer = allocatedBlocks[i];

            byte[] blockData = new byte[Disk.BLOCK_SIZE];
            int length = Math.min(dataBytes.length - offset, Disk.BLOCK_SIZE);
            System.arraycopy(dataBytes, offset, blockData, 0, length);

            System.out.printf("Writing %d bytes to disk %d, block %d\n", length, diskIndex, blockPointer);
            disks[diskIndex].writeDataBlock(blockData, blockPointer);
            offset += Disk.BLOCK_SIZE;
        }

        INode inode = diskDevice.readInode(fileDescriptor);
        inode.setSize(dataBytes.length);
        for (int i = 0; i < allocatedBlocks.length; i++) {
            inode.setBlockPointer(i, allocatedBlocks[i]);
        }
        diskDevice.writeInode(inode, fileDescriptor);

        System.out.println("Finished writing data to file descriptor " + fileDescriptor + ".");
    }

    /**
     * The main piece of the Pie
     * Allocates the blocks across multiple disks for a file in a RAID 0 setup.
     * The method divides the file's data into blocks and distributes these blocks evenly across the disks in a round-robin manner.
     *
     * @param iNodeNumber The inode number representing the file for which blocks are being allocated.
     * @param numBytes    The total size of the file in bytes.
     * @return An array of block pointers representing the locations of the allocated blocks.
     * @throws IOException If there are not enough free blocks available to allocate the file.
     */
    public int[] allocateBlocksForFile(int iNodeNumber, int numBytes) throws IOException {
        // THis Calculates the number of blocks needed for the given file size
        int numBlocks = (int) Math.ceil((double) numBytes / Disk.BLOCK_SIZE);
        // The Array to store pointers to the blocks allocated for this file
        int[] blockPointers = new int[numBlocks];
        FreeBlockList freeBlockList = new FreeBlockList();
        byte[] freeList = diskDevice.readFreeBlockList();
        freeBlockList.setFreeBlockList(freeList);
        // The stripe size for RAID 0 is the number of disks
        int stripeSize = numDisks;
        int blocksAllocated = 0;

        System.out.printf("Will now Allocate %d blocks for inode %d%n", numBlocks, iNodeNumber);
        // Loops through all the blocks in the disk to find the free blocks and allocate them
        for (int i = 0; i < Disk.NUM_BLOCKS && blocksAllocated < numBlocks; i++) {
            if ((freeList[i / 8] & (1 << (i % 8))) == 0) {
                // This Assigns the current block to a disk in round-robin manner
                int diskIndex = blocksAllocated % numDisks;
                int blockInStripe = blocksAllocated / numDisks;
                freeBlockList.allocateBlock(i);
                blockPointers[blocksAllocated++] = (diskIndex * stripeSize) + blockInStripe;

                System.out.printf("Allocated block %d to disk %d, stripe block %d%n",
                        blocksAllocated - 1, diskIndex, blockInStripe);
            }
        }

        if (blocksAllocated < numBlocks) {
            throw new IOException("Not enough free blocks available");
        }

        diskDevice.writeFreeBlockList(freeBlockList.getFreeBlockList());
        // Update the file's inode with the list of allocated block pointers
        INode inode = diskDevice.readInode(iNodeNumber);
        for (int j = 0; j < blockPointers.length; j++) {
            inode.setBlockPointer(j, blockPointers[j]);
        }
        diskDevice.writeInode(inode, iNodeNumber);

        System.out.println("Block allocation completed successfully.");
        return blockPointers;
    }

    /**
     * Deallocates blocks associated with a file.
     * Frees up the blocks in the free block list and updates the files inode to remove references.
     *
     * @param iNodeNumber The inode number of the file to deallocate.
     * @throws IOException If there is an issue reading or writing the disk data.
     */
    void deallocateBlocksForFile(int iNodeNumber) throws IOException {
        System.out.println("Deallocating blocks for file with inode number: " + iNodeNumber);

        INode inode = diskDevice.readInode(iNodeNumber);
        FreeBlockList freeBlockList = new FreeBlockList();
        byte[] freeList = diskDevice.readFreeBlockList();
        freeBlockList.setFreeBlockList(freeList);

        for (int i = 0; i < INode.NUM_BLOCK_POINTERS; i++) {
            int blockPointer = inode.getBlockPointer(i);
            if (blockPointer == -1) break;

            System.out.printf("Deallocating block %d\n", blockPointer);
            freeBlockList.deallocateBlock(blockPointer);
            inode.setBlockPointer(i, -1);
        }

        diskDevice.writeFreeBlockList(freeBlockList.getFreeBlockList());
        inode.setSize(0);
        diskDevice.writeInode(inode, iNodeNumber);

        System.out.println("Finished deallocating blocks for file with inode number: " + iNodeNumber);
    }
}