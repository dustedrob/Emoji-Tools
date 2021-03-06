package me.MitchT.EmojiTools.Extraction.Extractors;

import me.MitchT.EmojiTools.EmojiTools;
import me.MitchT.EmojiTools.Extraction.ExtractionManager;
import me.MitchT.EmojiTools.Extraction.ExtractionUtilites;
import me.MitchT.EmojiTools.GUI.ExtractionDialog;

import java.io.*;
import java.util.List;

public class AppleExtractionThread extends ExtractionThread {

    private final List<String> tableNames;
    private final List<Integer> tableOffsets;
    private final List<Integer> tableLengths;

    private final ExtractionManager extractionManager;
    private final ExtractionDialog extractionDialog;

    public AppleExtractionThread(File font, File extractionDirectory, List<String> tableNames, List<Integer> tableOffsets, List<Integer> tableLengths, ExtractionManager extractionManager, ExtractionDialog extractionDialog) {
        super(font, extractionDirectory, "AppleExtractionThread");

        this.tableNames = tableNames;
        this.tableOffsets = tableOffsets;
        this.tableLengths = tableLengths;

        this.extractionManager = extractionManager;
        this.extractionDialog = extractionDialog;

    }

    @Override
    public void run() {
        try {
            RandomAccessFile inputStream = new RandomAccessFile(font, "r");

            if (!extractionDirectory.exists()) {
                extractionDirectory.mkdir();
            }

            appendToStatus("Searching for Emojis - Please wait until complete!");

            //Get numGlyphs, ordinal numbers, and glyphNames from post table
            int postIndex = tableNames.indexOf("post");
            if (postIndex > -1) {
                int postOffset = tableOffsets.get(postIndex);
                int postLength = tableLengths.get(postIndex);

                inputStream.seek(postOffset);


                if (!ExtractionUtilites.compareBytes(inputStream, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x00)) {
                    extractionManager.showMessageDialog("Invalid 'post' table format! Contact developer for help.");
                    inputStream.close();
                    extractionDialog.dispose();
                    return;
                }

                inputStream.skipBytes(28);

                short numGlyphs = inputStream.readShort();
                short[] ordinalNumbers = new short[numGlyphs];

                short numNewGlyphs = 0;

                for (int i = 0; i < numGlyphs; i++) {
                    ordinalNumbers[i] = inputStream.readShort();
                    if (ordinalNumbers[i] > 257)
                        numNewGlyphs++;
                }

                String[] extraNames = new String[numNewGlyphs];

                for (int i = 0; i < numNewGlyphs; i++) {

                    short nameLen = (short) inputStream.read();
                    extraNames[i] = ExtractionUtilites.getByteString(inputStream, nameLen);
                }

                //Build list of names for GlyphIDs
                String[] glyphNames = new String[numGlyphs];
                for (int i = 0; i < numGlyphs; i++) {
                    if (ordinalNumbers[i] <= 257) {
                        glyphNames[i] = standardOrderNames[ordinalNumbers[i]];
                    } else {
                        glyphNames[i] = extraNames[ordinalNumbers[i] - 258];
                    }
                }

                //Get number of strikes, and scan for PNG files.
                int sbixIndex = tableNames.indexOf("sbix");
                if (sbixIndex > -1) {
                    int sbixOffset = tableOffsets.get(sbixIndex);
                    int sxixLength = tableLengths.get(sbixIndex);

                    inputStream.seek(sbixOffset);

                    inputStream.skipBytes(4);

                    int numStrikes = inputStream.readInt();
                    int[] strikeOffsets = new int[numStrikes];
                    for (int i = 0; i < numStrikes; i++) {
                        strikeOffsets[i] = inputStream.readInt();
                    }

                    //TODO: Figure out how to convert rgbl to png.. for now, use last strike..
                    inputStream.seek(sbixOffset + strikeOffsets[strikeOffsets.length - 1]);
                    inputStream.skipBytes(4);

                    int[] glyphOffsets = new int[numGlyphs];
                    int[] glyphLengths = new int[numGlyphs];

                    for (int i = 0; i < numGlyphs; i++) {
                        glyphOffsets[i] = inputStream.readInt();
                    }

                    for (int i = 0; i < numGlyphs; i++) {
                        if (i + 1 == numGlyphs)
                            glyphLengths[i] = sxixLength - strikeOffsets[strikeOffsets.length - 1] - glyphOffsets[i];
                        else
                            glyphLengths[i] = glyphOffsets[i + 1] - glyphOffsets[i];
                    }

                    inputStream.seek(sbixOffset + strikeOffsets[strikeOffsets.length - 1]);
                    inputStream.skipBytes(glyphOffsets[0]);

                    for (int i = 0; i < numGlyphs; i++) {
                        if (!running) {
                            inputStream.close();
                            extractionDialog.dispose();
                            return;
                        }
                        updateProgress((int) ((i / (float) (numGlyphs)) * 100));

                        if (glyphLengths[i] > 0) {
                            inputStream.skipBytes(4);
                            if (ExtractionUtilites.getByteString(inputStream, 4).equals("png ")) {
                                System.out.println("Extracting Emoji #" + i + " to '" + glyphNames[i] + ".png'");
                                appendToStatus("Extracting Emoji #" + i + " to '" + glyphNames[i] + ".png'");
                                FileOutputStream outputStream = new FileOutputStream(new File(extractionDirectory, glyphNames[i] + ".png"));
                                byte[] b = new byte[glyphLengths[i] - 8];
                                inputStream.readFully(b);
                                outputStream.write(b);
                                outputStream.close();
                            }
                        }
                    }
                } else {
                    extractionManager.showMessageDialog("Could not find 'sbix' table! Contact developer for help.");
                    inputStream.close();
                    extractionDialog.dispose();
                    return;
                }
            } else {
                extractionManager.showMessageDialog("Could not find 'post' table! Contact developer for help.");
                inputStream.close();
                extractionDialog.dispose();
                return;
            }

            inputStream.close();

            extractionDialog.dispose();
        } catch (FileNotFoundException e) {
            System.out.println(this.font.getName() + " not found!");
            extractionManager.showMessageDialog(this.font.getName() + " not found!");
        } catch (IOException e) {
            EmojiTools.submitError(Thread.currentThread(), e);
        }
    }

    private void updateProgress(int progress) {
        extractionDialog.setProgress(progress);
    }

    private void appendToStatus(String message) {
        extractionDialog.appendToStatus(message);
    }

}
