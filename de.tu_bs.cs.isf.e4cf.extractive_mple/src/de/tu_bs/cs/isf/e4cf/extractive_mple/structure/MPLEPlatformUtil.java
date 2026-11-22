package de.tu_bs.cs.isf.e4cf.extractive_mple.structure;

import java.io.File;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;


import java.io.Writer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class MPLEPlatformUtil {

//	public static void storePlatform(String fileName, MPLPlatform platform) {
//	    System.err.println("Storing platform to file: " + fileName + ".json");
//
//	    try {
//	        // Track visited object instances by memory address to prevent cyclic reference loops
//	        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
//
//	        Gson gson = new GsonBuilder()
//	                .setPrettyPrinting()
//	                .serializeNulls()
//	                .setExclusionStrategies(new ExclusionStrategy() {
//	                    @Override
//	                    public boolean shouldSkipField(FieldAttributes f) {
//	                        String cls = f.getDeclaringClass().getName();
//	                        String n = f.getName();
//	                        // back-reference in the feature tree
//	                        if (n.equals("parent") && cls.startsWith("de.tu_bs.cs.isf.e4cf.featuremodel")) return true;
//	                        // engine internals of the platform, not needed downstream
//	                        if (cls.endsWith(".MPLPlatform")
//	                                && (n.equals("compareEngine") || n.equals("matcher") || n.equals("prefs"))) return true;
//	                        return false;
//	                    }
//	                    @Override
//	                    public boolean shouldSkipClass(Class<?> c) { return false; }
//	                })
//	                .create();
//
//	        try (Writer writer = new FileWriter(fileName + ".json")) {
//	            gson.toJson(platform, writer);
//	            System.err.println("JSON successfully written to " + fileName + ".json");
//	        }
//
//	    } catch (Throwable e) {
//	        System.err.println("ERROR DURING JSON EXPORT:");
//	        e.printStackTrace();
//	    }
//	}

	/**
	 * Store multi product line platform to file
	 */
	public static MPLPlatform loadPlatform(File file) {
		FileInputStream in;
		try {
			in = new FileInputStream(file);
			ObjectInputStream oin = new ObjectInputStream(in);
			Object obj = oin.readObject();
			oin.close();
			in.close();
			if (obj instanceof MPLPlatform) {
				MPLPlatform platform = (MPLPlatform) obj;
				String filename = file.getName().substring(0,
						file.getName().lastIndexOf("."));
				platform.fileName = filename;
				return platform;
			}
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}


// /**
// * Store multi product line platform to file
// */
 public static void storePlatform(String fileName, MPLPlatform platform) {
 try {
 FileOutputStream file = new FileOutputStream(fileName);
 ObjectOutputStream out = new ObjectOutputStream(file);
 out.writeObject(platform);
 out.close();
 file.close();
 } catch (IOException e) {
 e.printStackTrace();
 }
 
 }
 }

