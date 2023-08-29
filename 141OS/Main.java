package src;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Scanner;

// GUI imports
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

class Disk
{
   static final int NUM_SECTORS = 1024;
   StringBuffer sectors[] = new StringBuffer[NUM_SECTORS];
   int nextFreeBuffer;
   Disk(int cap)
   {
      for (int i = 0; i < sectors.length; ++i)
         sectors[i] = new StringBuffer(cap);
      nextFreeBuffer = 0;
   }
   
   void write(int sector, StringBuffer data) throws InterruptedException
   {
      int speed = 200;
      if (Main.guiEnabled)
         speed = (int) (200 * Main.speedMultiplier);
      Thread.sleep(speed);
      sectors[sector].append(data);
   }

   void read(int sector, StringBuffer data) throws InterruptedException
   {
      int speed = 200;
      if (Main.guiEnabled)
         speed = (int) (200 * Main.speedMultiplier);
      Thread.sleep(speed);
      data.append(sectors[sector]).append("\n");
   }
}

class Printer
{
   int printerID;
   Printer(int id)
   {
      printerID = id;
   }
   
   void print(StringBuffer b) throws InterruptedException
   {
      try
      {
         int speed = 2750;
         if (Main.guiEnabled)
            speed = (int) (2750 * Main.speedMultiplier);
         Thread.sleep(speed);
         try (FileWriter fileWrite = new FileWriter("PRINTER" + printerID, true))
            {
               String text = b.toString();
               fileWrite.write(text);
               fileWrite.flush();
            }
      }
      catch (IOException e)
      {
         System.out.println(e);
      }
   }
}

class ResourceManager
{
   boolean isFree[];
   ResourceManager(int numberOfItems)
   {
      isFree = new boolean[numberOfItems];
      for (int i = 0; i < isFree.length; ++i)
      {
         isFree[i] = true;
      }
   }

   synchronized int request() throws InterruptedException
   {
      while (true)
      {
         for (int i = 0; i < isFree.length; ++i)
         {
            if (isFree[i])
            {
               isFree[i] = false;
               return i;
            }
         }
         this.wait(); // Block until someone releases a Resource
      }
   }
   synchronized void release(int index)
   {
      isFree[index] = true;
      this.notify(); // Let blocked thread run
   }
}

class FileInfo
{
   int diskNumber;
   int startingSector;
   int fileLength;
   
   FileInfo(int diskNum, int startSector, int fileLen)
   {
      diskNumber = diskNum;
      startingSector = startSector;
      fileLength = fileLen;
   }
}

class DirectoryManager
{
   private Hashtable<String, FileInfo> T = new Hashtable<String, FileInfo>();
   void enter(StringBuffer fileName, FileInfo file)
   {
      T.put(fileName.toString(), file);
   }
   
   FileInfo lookup(StringBuffer fileName)
   {
      return T.get(fileName.toString());
   }
}

class DiskManager
   extends ResourceManager
{
   Disk [] diskArray;
   DirectoryManager directoryManager;
   int nextFreeSector;
   DiskManager(int numberOfItems)
   {
      super(numberOfItems);
      directoryManager = new DirectoryManager();
      diskArray = new Disk[numberOfItems];
      for (int i = 0; i < numberOfItems; ++i)
         diskArray[i] = new Disk(1024);
      nextFreeSector = 0;
   }

   synchronized void writeToDisk(int diskID, int offset, StringBuffer line) throws InterruptedException
   {
      diskArray[diskID].write(offset, line);
      if (Main.guiEnabled)
         Main.diskContents[diskID].append("[" + offset + "] : " + line.toString() + "\n"); 
   }

   void setNextFreeSector(int diskID, int offset)
   {
      diskArray[diskID].nextFreeBuffer = offset;
   }
   
   int getNextFreeSector(int diskID)
   {
      return diskArray[diskID].nextFreeBuffer;
   }
}

class PrinterManager
   extends ResourceManager
{
   Printer [] printerArray;
   PrinterManager(int numberOfItems)
   {
      super(numberOfItems);
      printerArray = new Printer[numberOfItems];
      for (int i = 0; i < numberOfItems; ++i)
         printerArray[i] = new Printer(i + 1);
   }
   
   void printLine(int threadID, int userID, int diskID, int printerID, int sector, StringBuffer sectorData) throws InterruptedException
   {
      if (Main.guiEnabled)
         Main.userLogs[userID - 1].append(threadID + ": Read disk " + (diskID + 1) + " sector " + "\n");
      Main.diskManager.diskArray[diskID].read(sector, sectorData);
      if (Main.guiEnabled)
         Main.userLogs[userID - 1].append(threadID + ": Print line at printer " + (printerID + 1) + "\n");
      Main.printManager.printerArray[printerID].print(sectorData);
      if (Main.guiEnabled)
         Main.printerOutputs[printerID].append(sectorData.toString());
   }
}

class PrintJobThread
   extends Thread
{
   int userID;
   int threadID;
   StringBuffer line = new StringBuffer(); // Only allowed one line to reuse for read from disk and print
   PrintJobThread(StringBuffer file, int id, int thread)
   {
      line = file;
      userID = id;
      threadID = thread;
   }
   
   @Override
   public void run()
   {
      try
      {
         FileInfo info = Main.directManager.lookup(line);
         int currentSector = info.startingSector;
         int diskID = info.diskNumber;
         int length = info.fileLength;
         int printerID = Main.printManager.request();
         if (Main.guiEnabled)
         {
            Main.printerIcons[printerID].setBackground(Color.green);
            Main.userLogs[userID - 1].append("Create printer thread " + threadID + "\n");
         }
         line.append("\n");

         for (int i = 0; i < length; ++i)
         {
            Main.printManager.printLine(threadID, userID, diskID, printerID, currentSector, line);
            line.delete(0, line.length());
            currentSector++;
         }
         if (Main.guiEnabled)
         {
            Main.userLogs[userID - 1].append("Printer thread " + threadID + " terminated\n");
            Main.printerIcons[printerID].setBackground(Color.red);
         }
         Main.printManager.release(printerID);
      }
      catch(Exception e)
      {
         System.out.println(e);
      }
   }
}

class UserThread
   extends Thread
{
   String fileName;
   StringBuffer currentLine;
   int userID;

   UserThread(int id)
   {
      fileName = "./inputs/USER" + Integer.toString(id);
      currentLine = new StringBuffer();
      userID = id;
   }

   @Override
   public void run()
   {
      try
      {
         File file = new File(fileName);
         Scanner readFile = new Scanner(file);
         processLines(readFile);
      }
      catch (FileNotFoundException | InterruptedException e)
      {
         System.out.println(e);
      }
   }

   void processLines(Scanner readFile) throws FileNotFoundException, InterruptedException
   {
      int thread = 1;
      while (readFile.hasNextLine())
      {
         String line = readFile.nextLine();
         if (line.startsWith(".save"))
         {
            String name = line.substring(6);
            int diskID = Main.diskManager.request();
            if (Main.guiEnabled)
               Main.userLogs[userID - 1].append("Save " + name + " at disk " + (diskID + 1) + "\n");
            int offset = Main.diskManager.getNextFreeSector(diskID);
            int fileLines = 0;
            if (Main.guiEnabled)
               Main.diskIcons[diskID].setBackground(Color.green);
            while (readFile.hasNextLine())
            {
               line = readFile.nextLine();
               if (!line.startsWith(".end"))
               {
                  currentLine = new StringBuffer(line);
                  if (Main.guiEnabled)
                     Main.userLogs[userID - 1].append("Write line at disk " + (diskID + 1) + " sector " + (offset + fileLines) + "\n");
                  Main.diskManager.writeToDisk(diskID, offset + fileLines, currentLine);
                  fileLines++;
               }
               else
               {
                  FileInfo fileInfo = new FileInfo(diskID, offset, fileLines);
                  currentLine = new StringBuffer(name);
                  Main.directManager.enter(currentLine, fileInfo);
                  break;
               }
            }
            Main.diskManager.setNextFreeSector(diskID, offset + fileLines);
            Main.diskManager.release(diskID);
            if (Main.guiEnabled)
               Main.diskIcons[diskID].setBackground(Color.red);
         }
         else if (line.startsWith(".print"))
         {
            currentLine = new StringBuffer(line.substring(7));
            PrintJobThread printerThread = new PrintJobThread(currentLine, userID, thread);
            printerThread.start();
            thread++;
         }
         else
         {
            System.out.println("Error! Unknown command!");
         }
      }
      readFile.close();
   }
}

public class Main
{
   static DiskManager diskManager;
   static PrinterManager printManager;
   static DirectoryManager directManager;
   static double speedMultiplier = 1.0;

   // GUI
   static JPanel [] printerIcons;
   static JTextArea [] printerOutputs;
   static JPanel [] diskIcons;
   static JTextArea [] userLogs;
   static JLabel [] userLabels;
   static JTextArea [] diskContents;
   static boolean guiEnabled = true;

   public static void changeSpeed(double speed)
   {
      speedMultiplier = speed;
   }

   public static void main(String[] args) throws IOException
   {
      final int numUserThreads = Math.abs(Integer.valueOf(args[0]));
      int numDisks = Math.abs(Integer.valueOf(args[numUserThreads + 1]));
      int numPrinters = Math.abs(Integer.valueOf(args[numUserThreads + 2]));

      // Check for -ng flag
      for (int i = 0; i < args.length; ++i)
      {
         if (args[i].equals("-ng"))
            guiEnabled = false;
      }

      // Printers
      printManager = new PrinterManager(numPrinters);
      if (guiEnabled)
      {
         printerIcons = new JPanel[numPrinters];
         printerOutputs = new JTextArea[numPrinters];
      }

      // Disks
      diskManager = new DiskManager(numDisks);
      if (guiEnabled)
      {
         diskIcons = new JPanel[numDisks];
         diskContents = new JTextArea[numDisks];
      }
      // Directory Manager
      directManager = new DirectoryManager();

      // User threads
      final UserThread [] userThreadArray = new UserThread[numUserThreads];
      if (guiEnabled)
      {
         userLabels = new JLabel[numUserThreads];
         userLogs = new JTextArea[numUserThreads];
      }

      // Creating the user threads
      for (int i = 0; i < numUserThreads; ++i)
      {
         userThreadArray[i] = new UserThread(i + 1);
      }

      // Creating the printers
      for (int i = 0; i < numPrinters; ++i)
      {
         File file = new File("PRINTER" + (i + 1));
         if (file.exists())
            file.delete();
         file.createNewFile();
      }

      if (guiEnabled)
      {
         // Creating the gui
         final JFrame gui = new JFrame("Will's GUI");
        
         // Welcome Label
         JLabel welcome = new JLabel("Welcome to Will's GUI!");
         welcome.setBounds(185, 0, 150, 30);

         // Name
         JLabel name = new JLabel("William Barsaloux");
         name.setBounds(10, 0, 125, 30);

         // UCNetID
         JLabel UCnetID = new JLabel("wbarsalo");
         UCnetID.setBounds(10, 20, 125, 30);

         // Start button
         final JButton startButton = new JButton("Start Process");
         startButton.setBounds(175, 50, 150, 50);
      
         // Slow speed
         final JRadioButton slowSpeed = new JRadioButton("0.5 speed", false);
         slowSpeed.setBounds(375, 0, 100, 25);

         // Normal speed (is used by default)
         final JRadioButton normalSpeed = new JRadioButton("normal speed", true);
         normalSpeed.setBounds(375, 25, 125, 25);
         normalSpeed.setEnabled(false);

         // Fast speed
         final JRadioButton fastSpeed = new JRadioButton("2.0 speed", false);
         fastSpeed.setBounds(375, 50, 100, 25);

         // Slow speed item
         slowSpeed.addItemListener(new ItemListener()
         {
            @Override
            public void itemStateChanged(ItemEvent e)
            {
               if (slowSpeed.isSelected())
               {
                  fastSpeed.setSelected(false);
                  normalSpeed.setSelected(false);
                  changeSpeed(2.0);
               }
               else
               {
                  normalSpeed.setSelected(true);
                  changeSpeed(1.0);
               }
            }
         });

         fastSpeed.addItemListener(new ItemListener()
         {
            @Override
            public void itemStateChanged(ItemEvent e)
            {
               if (fastSpeed.isSelected())
               {
                  slowSpeed.setSelected(false);
                  normalSpeed.setSelected(false);
                  changeSpeed(0.5);
               }
               else
               {
                  normalSpeed.setSelected(true);
                  changeSpeed(1.0);
               }
            }
         });
   
         // Creating the printerIcons (red means idle, green means in use)
         JLabel printerShapesLabel = new JLabel("Status of printers");
         printerShapesLabel.setBounds(10, 125, 100, 30);
         gui.add(printerShapesLabel);

         for (int i = 0; i < numPrinters; ++i)
         {
            printerIcons[i] = new JPanel();
            printerIcons[i].setBounds(10 + i * 60, 150, 50, 50);
            printerIcons[i].setBackground(Color.red);
            gui.add(printerIcons[i]);
         }

         // Creating the disk icons (again, red means idle, green means in-use)
         JLabel disksStatusLabel = new JLabel("Status of disks");
         disksStatusLabel.setBounds(400, 150, 100, 30);
         gui.add(disksStatusLabel);

         // Show contents of disks
         for (int i = 0; i < numDisks; ++i)
         {
            diskIcons[i] = new JPanel();
            diskIcons[i].setBounds(400 + i * 35, 175, 25, 25);
            diskIcons[i].setBackground(Color.red);
            gui.add(diskIcons[i]);
         }

         // Create printer labels and outputs
         for (int i = 0; i < numPrinters; ++i)
         {
            JLabel printerLabel = new JLabel("Printer " + (i + 1));
            printerLabel.setBounds(10 + i * 160, 210, 50, 20);
            gui.add(printerLabel);
            printerOutputs[i] = new JTextArea();
            JScrollPane text = new JScrollPane(printerOutputs[i]);
            text.setBounds(10 + i * 160, 230, 150, 450);
            gui.add(text);
         }

         // Create disk labels and contents
         for (int i = 0; i < numDisks; ++i)
         {
            JLabel diskLabel = new JLabel("Disk " + (i + 1));
            diskLabel.setBounds(550 + i * 160, 350, 50, 20);
            gui.add(diskLabel);
            diskContents[i] = new JTextArea();
            JScrollPane text = new JScrollPane(diskContents[i]);
            text.setBounds(550 + i * 160, 370, 150, 310);
            gui.add(text);
         }

         // Create user labels and logs
         for (int i = 0; i < numUserThreads; ++i)
         {
            userLabels[i] = new JLabel("User " + (i + 1));
            userLabels[i].setBounds(550 + i * 185, 0, 50, 20);
            gui.add(userLabels[i]);
            userLogs[i] = new JTextArea();
            JScrollPane text = new JScrollPane(userLogs[i]);
            text.setBounds(550 + i * 185, 20, 175, 300);
            gui.add(text);
         }
     
         gui.add(slowSpeed);
         gui.add(normalSpeed);
         gui.add(fastSpeed);
         gui.add(welcome);
         gui.add(startButton);
         gui.add(name);
         gui.add(UCnetID);
         gui.setSize(1400, 750);
         gui.setLayout(null);
         gui.setVisible(true);
         gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

         startButton.addActionListener(new ActionListener()
         {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                // Start user threads
               for (int i = 0; i < numUserThreads; ++i)
                   userThreadArray[i].start();
               startButton.setEnabled(false);
            }
         });
      }
      else
      {
         for (int i = 0; i < numUserThreads; ++i)
            userThreadArray[i].start();
      }
   }
}

