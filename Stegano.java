import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

public class Stegano {

	private static final String KEY = "a9w78yf234uhfals";
	private static final long SEED = 824653791;

	Stegano() {}

	public void encode(String carrier, String message, String outputFile) {
		AudioInputStream audioStream = createAudioStream(carrier);
		DataInputStream messageStream = createMessageStream(message);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Get the number of bytes used in message
		long fileSize = getFileLength(message);
		// Number of writes we must make
		long numWrites = fileSize * 8;

		// Get binary representation of our (not wave) header
		StringBuffer writeString = new StringBuffer(Long
				.toBinaryString(numWrites));
		int stringSize = writeString.length();
		for (int i = stringSize; i < 64; i++) {
			writeString.insert(0, "0");
		}

		try {

			byte[] byteArray = new byte[4];
			// Write header to file
			for (int y = 0; y < 64; y++) {
				Integer currentInt = new Integer(writeString
						.substring(y, y + 1));
				int currentAudioByte = audioStream.read(byteArray);

				int[] intArray = new int[4];
				intArray = byteToIntArray(byteArray);
				if (currentAudioByte != -1) {
					// If header bit is not the same as audio bit, flip it
					if (currentInt != getBit(intArray[1], 0)) {
						intArray[1] = flipBit(intArray[1]);
					}
				} else {
					System.out.println("Audio file is too small for message");
					System.exit(1);
				}

				byteArray = intToByteArray(intArray);
				out.write(byteArray);
			}
			// Copy the message into the audio stream
			int currentMessageByte = messageStream.read();
			Random writeRandom = new Random(this.SEED);
			while (currentMessageByte != -1) {

				for (int i = 7; i > -1; i--) {

					// Get a pseudo-random frame
					int newRand = writeRandom.nextInt() % 5;
					newRand = Math.abs(newRand);
					int currentAudioByte = 0;

					// Skip x amount of frames
					for (int y = 0; y < newRand; y++){
						currentAudioByte = audioStream.read(byteArray);

						if (currentAudioByte != -1){
							out.write(byteArray);
						} else {
							System.out.println("Audio file is too small for message");
							System.exit(1);
						}
					}

					// Write out data
					if (currentAudioByte != -1) {

						int[] intArray = byteToIntArray(byteArray);
						if (getBit(intArray[1], 0) != getBit(
								currentMessageByte, i)) {
							// flip the the least significant bit in the 2nd
							// byte
							intArray[1] = flipBit(intArray[1]);
						}

						byte[] newByteArray = intToByteArray(intArray);
						out.write(newByteArray);
					} else {
						System.out
								.println("Audio file is too small for message");
						System.exit(1);
					}

				}
				currentMessageByte = messageStream.read();
			}

			// Copy any remaining bytes
			int currentAudioByte = audioStream.read(byteArray);
			while (currentAudioByte != -1) {
				out.write(byteArray);
				currentAudioByte = audioStream.read(byteArray);
			}

			// Output to an audio file
			ByteArrayInputStream in = new ByteArrayInputStream(out
					.toByteArray());
			AudioInputStream ais = new AudioInputStream(in, audioStream
					.getFormat(), audioStream.getFrameLength());
			AudioSystem.write(ais, AudioFileFormat.Type.WAVE,
					new FileOutputStream(outputFile));

		} catch (Exception e) {
			System.exit(1);
		}

	}

	public void decode(String carrier, String outputFile) throws IOException {
		AudioInputStream audioStream = createAudioStream(carrier);
		FileOutputStream outputStream = createOutputStream(outputFile);
		StringBuffer writeString = new StringBuffer();

		// Get our header (not wave), get lsb of first 64 bytes
		byte[] byteArray = new byte[4];
		for (int i = 0; i < 64; i++) {
			audioStream.read(byteArray);

			int[] intArray = byteToIntArray(byteArray);
			// Get LSB of 2nd byte
			int lsb = getBit(intArray[1], 0);
			writeString.append(lsb);
		}

		int totalWrites = Integer.parseInt(writeString.toString(), 2) / 8;
		Random writeRandom = new Random(this.SEED);
		for (int x = 0; x < totalWrites; x++) {
			String byteString = "";
			for (int i = 0; i < 8; i++) {

				// Skip next n bytes (pseudo-random)
				int nextRandom = writeRandom.nextInt() % 5;
				nextRandom = Math.abs(nextRandom);
				audioStream.skip(nextRandom*4);

				// Decode
				audioStream.read(byteArray);
				int[] intArray = byteToIntArray(byteArray);
				int lsb = getBit(intArray[1], 0);
				byteString = byteString + lsb;
			}

			byte numberByte = (byte) Integer.parseInt(byteString, 2);
			outputStream.write(numberByte);
		}

		outputStream.close();
	}

	// Gets the length of a file in bytes
	public long getFileLength(String pathToFile) {
		File file = new File(pathToFile);
		return file.length();
	}

	// Transforms a byteArray into an intArray
	public int[] byteToIntArray(byte[] b) {
		int[] newIntArray = new int[b.length];
		for (int y = 0; y < 4; y++) {
			int c = (b[y] & 255);
			newIntArray[y] = c;
		}

		return newIntArray;
	}

	// Transforms an intArray into a byteArray
	public byte[] intToByteArray(int[] i) {
		byte[] newByteArray = new byte[4];
		for (int z = 0; z < 4; z++) {
			newByteArray[z] = (byte) i[z];
		}
		return newByteArray;
	}

	// Returns the bit at a specific index of a byte
	public int getBit(int b, int index) {
		return (b >> index) & 1;
	}

	// Flip the least significant bit of a byte
	public int flipBit(int b) {
		return b ^ 1;
	}

	// Creates a stream to read the passed message data
	public DataInputStream createMessageStream(String fileName) {
		FileInputStream FIS = null;
		try {
			FIS = new FileInputStream(fileName);
			BufferedInputStream BIS = new BufferedInputStream(FIS);
			return new DataInputStream(BIS);
		} catch (FileNotFoundException except) {
			System.out.println("Invalid path provided to Message file");
			System.exit(1);
		} catch (Exception e) {
			System.exit(1);
		}

		return null;
	}

	// Creates a stream to read the passed audio data
	public AudioInputStream createAudioStream(String fileName) {
		try {
			File audioFile = new File(fileName);
			AudioInputStream stream = AudioSystem
					.getAudioInputStream(audioFile);
			return stream;
		} catch (FileNotFoundException except) {
			System.out.println("Invalid path provided to Audio file");
			System.exit(1);
		} catch (Exception e) {
			System.exit(1);
		}

		return null;
	}

	public FileOutputStream createOutputStream(String outputFile) {
		try {
			return new FileOutputStream(outputFile);
		} catch (Exception e) {
			System.out.println("Can't write to file provided");
			System.exit(1);
		}
		return null;
	}

	public static void main(String[] args) {
		String path = System.getProperty("user.dir");
		Stegano steg = new Stegano();
		try {
			if (args[0].toString().equals("--encode")) {
				if (args.length == 4) {
					String carrier = path + "/" + args[1];
					String message = path + "/" + args[2];
					String outputFile = args[3];
					steg.encode(carrier, message, outputFile);
				} else {
					System.out
							.println("./stegan --encode requires 4 arguments\n--encode carrier message outputFile");
					System.exit(1);
				}
			} else if (args[0].toString().equals("--decode")) {
				if (args.length == 3) {
					String carrier = path + "/" + args[1];
					String outputFile = path + "/" + args[2];
					steg.decode(carrier, outputFile);
				} else {
					System.out
							.println("./stegan --decode requires 3 arguments\n--decode carrier outputFile");
					System.exit(1);
				}
			} else {
				System.out
						.println("usage: stegan\n"
								+ "./stegan --encode file1 file2 file3\n"
								+ "./stegan --decode file4 file5\n\n"
								+ "where\n\n"
								+ "file1 is the name of an existing WAVE file that "
								+ "contains an audio recording (the 'carrier').\n\n"
								+ "file2 is the name of an existing file (of arbitrary type) "
								+ "that contains the binary message to be added to file1.\n\n"
								+ "file3 is the name of the WAVE file to be created "
								+ "by adding the binary message in file2 to the recording "
								+ "in file1.\n\n"
								+ "file4 is the name of an existing WAVE file that "
								+ "may have been created by your software (as the file3 in "
								+ "the --encode direction).\n"
								+ "file5 is the name of a file to be created by extracting "
								+ "a binary message from file4 (under the assumption that "
								+ "file4 was created by your software as the file3 in the "
								+ "--encode direction).");
				System.exit(1);
			}
		} catch (Exception e) {
      System.out
						.println("usage: stegan\n"
								+ "./stegan --encode file1 file2 file3\n"
								+ "./stegan --decode file4 file5\n\n"
								+ "where\n\n"
								+ "file1 is the name of an existing WAVE file that "
								+ "contains an audio recording (the 'carrier').\n\n"
								+ "file2 is the name of an existing file (of arbitrary type) "
								+ "that contains the binary message to be added to file1.\n\n"
								+ "file3 is the name of the WAVE file to be created "
								+ "by adding the binary message in file2 to the recording "
								+ "in file1.\n\n"
								+ "file4 is the name of an existing WAVE file that "
								+ "may have been created by your software (as the file3 in "
								+ "the --encode direction).\n"
								+ "file5 is the name of a file to be created by extracting "
								+ "a binary message from file4 (under the assumption that "
								+ "file4 was created by your software as the file3 in the "
								+ "--encode direction).");
				System.exit(1);
		}
	}
}
