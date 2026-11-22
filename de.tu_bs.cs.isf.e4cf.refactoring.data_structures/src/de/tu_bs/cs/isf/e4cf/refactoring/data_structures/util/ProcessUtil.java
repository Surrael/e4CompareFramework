package de.tu_bs.cs.isf.e4cf.refactoring.data_structures.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.File;

public class ProcessUtil {

	private boolean isProcessReady;
	private BufferedReader reader;
	private BufferedWriter writer;
	private Process process;
	

	public synchronized void startProcess(String path, boolean isPython) throws IOException {
		if (path == null || !new File(path).isFile()) {
			throw new IOException("Clustering executable or script does not exist: " + path);
		}

		ProcessBuilder builder = isPython ? new ProcessBuilder("py", path) : new ProcessBuilder(path);
		builder.redirectErrorStream(true);
		process = builder.start();
		InputStream inputStream = process.getInputStream();
		reader = new BufferedReader(new InputStreamReader(inputStream));
		OutputStream outputStream = process.getOutputStream();
		writer = new BufferedWriter(new OutputStreamWriter(outputStream));
		isProcessReady = true;
	}

	public BufferedReader getReader() {
		return reader;
	}

	public BufferedWriter getWriter() {
		return writer;
	}

	public boolean isReady() {
		return isProcessReady && process != null && process.isAlive();
	}

	public void stop() {
		if (process != null) {
			process.destroyForcibly();
		}
	}

}
