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
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.FileWriter;

import java.io.Writer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MPLEPlatformUtil {

	public static void storePlatformGson(String fileName, MPLPlatform platform) {
		System.err.println("Storing platform to file: " + fileName + ".json");

		try {
			// Track visited object instances by memory address to prevent cyclic reference
			// loops
			Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

			Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls()
					.setExclusionStrategies(new ExclusionStrategy() {
						@Override
						public boolean shouldSkipField(FieldAttributes f) {
							String cls = f.getDeclaringClass().getName();
							String n = f.getName();
							// back-reference in the feature tree
							if (n.equals("parent") && cls.startsWith("de.tu_bs.cs.isf.e4cf.featuremodel"))
								return true;
							// engine internals of the platform, not needed downstream
							if (cls.endsWith(".MPLPlatform")
									&& (n.equals("compareEngine") || n.equals("matcher") || n.equals("prefs")))
								return true;
							return false;
						}

						@Override
						public boolean shouldSkipClass(Class<?> c) {
							return false;
						}
					}).create();

			try (Writer writer = new FileWriter(fileName + ".json")) {
				JsonObject export = gson.toJsonTree(platform).getAsJsonObject();
				export.addProperty("schemaVersion", 3);
				// Keep the legacy feature diagram intact. The extension is deliberately
				// JSON-side so old EMF feature-model files and editors remain compatible.
				if (platform.model != null) {
					export.add("artifactIndex", buildArtifactIndex(platform.model));
					String synthesisMode = System.getProperty(StructuralMsplSynthesizer.MODE_PROPERTY,
							StructuralMsplSynthesizer.STRUCTURAL);
					if (StructuralMsplSynthesizer.LEGACY.equalsIgnoreCase(synthesisMode)) {
						JsonObject mspl = buildMspl(platform, export.getAsJsonObject("artifactIndex"));
						export.add("featureConfigurations", buildFeatureConfigurations(platform, mspl));
						JsonObject fd = export.getAsJsonObject("featureDiagram");
						if (fd != null) { fd.addProperty("schemaVersion", 2); if (fd.has("root")) fd.add("legacyRoot",fd.get("root")); fd.add("root",mspl.get("familyRoot")); fd.add("mspl",mspl); }
						export.addProperty("schemaVersion",2);
					} else {
						StructuralMsplSynthesizer.synthesize(export);
					}
				}
				gson.toJson(export, writer);
				System.err.println("JSON successfully written to " + fileName + ".json");
			}

		} catch (Throwable e) {
			System.err.println("ERROR DURING JSON EXPORT:");
			e.printStackTrace();
		}
	}

	private static JsonObject buildArtifactIndex(de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node root) {
		JsonObject index = new JsonObject();
		indexNode(root, "", index);
		return index;
	}

	private static void indexNode(de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node node, String parentPath,
			JsonObject index) {
		String path = parentPath.isEmpty() ? node.getNodeType() : parentPath + "/" + node.getNodeType();
		JsonObject entry = new JsonObject();
		entry.addProperty("kind", "NODE");
		entry.addProperty("nodeType", node.getNodeType());
		entry.addProperty("ownerNodeUUID", node.getUUID().toString());
		entry.addProperty("path", path);
		entry.addProperty("startLine", node.getStartLine());
		entry.addProperty("endLine", node.getEndLine());
		index.add(node.getUUID().toString(), entry);
		for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Attribute a : node.getAttributes()) {
			JsonObject key = new JsonObject();
			key.addProperty("kind", "ATTRIBUTE");
			key.addProperty("nodeType", node.getNodeType());
			key.addProperty("ownerNodeUUID", node.getUUID().toString());
			key.addProperty("attributeKey", a.getAttributeKey());
			key.addProperty("path", path);
			key.addProperty("startLine", node.getStartLine());
			key.addProperty("endLine", node.getEndLine());
			index.add(a.getUUID().toString(), key);
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Value<?> v : a.getAttributeValues()) {
				JsonObject value = new JsonObject();
				value.addProperty("kind", "VALUE");
				value.addProperty("nodeType", node.getNodeType());
				value.addProperty("ownerNodeUUID", node.getUUID().toString());
				value.addProperty("attributeKey", a.getAttributeKey());
				value.add("value", new Gson().toJsonTree(v.getValue()));
				value.addProperty("path", path);
				value.addProperty("startLine", node.getStartLine());
				value.addProperty("endLine", node.getEndLine());
				index.add(v.getUUID().toString(), value);
			}
		}
		for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node child : node.getChildren())
			indexNode(child, path, index);
	}

	private static JsonArray buildFeatureConfigurations(MPLPlatform p, JsonObject mspl) {
		JsonArray result = new JsonArray();
		for (de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.Configuration c : p.configurations) {
			JsonObject variant = new JsonObject();
			variant.addProperty("variantName", c.getName());
			JsonArray family = new JsonArray();
			JsonObject familyRoot = mspl.getAsJsonObject("familyRoot");
			collectSelectedFeatures(familyRoot, c.getUUIDs(), family);
			variant.add("selectedFamilyFeatures", family);
			JsonArray instances = new JsonArray();
			java.util.Map<String, Integer> counts = new java.util.HashMap<>();
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.CloneConfiguration cc : c
					.getCloneConfigurations()) {
				String key = cc.componentUUID + "/" + cc.parentUUID;
				int n = counts.getOrDefault(key, 0);
				counts.put(key, n + 1);
				JsonObject instance = new JsonObject();
				instance.addProperty("componentUUID", cc.componentUUID.toString());
				instance.addProperty("parentUUID", cc.parentUUID.toString());
				instance.addProperty("cloneIndex", n);
				JsonArray ids = new JsonArray();
				for (JsonElement ve : mspl.getAsJsonArray("variationNodes")) {
					JsonObject variation = ve.getAsJsonObject();
					if (variation.get("componentUUID").getAsString().equals(cc.componentUUID.toString())
							&& variation.get("parentUUID").getAsString().equals(cc.parentUUID.toString()))
						for (JsonElement se : variation.getAsJsonArray("subSPLs")) {
							JsonObject sub = se.getAsJsonObject();
							JsonObject ck = sub.getAsJsonObject("cloneKey");
							String expected = cloneAttributeKey(p, cc.componentUUID, cc.getConfiguration().getUUIDs());
							String subKey = ck.get("attributeKey").getAsString() + "=" + ck.get("value").getAsString();
							if (expected.equals(subKey))
								collectSelectedFeatures(sub.getAsJsonObject("root"), cc.getConfiguration().getUUIDs(),
										ids);
						}
				}
				String cloneKey = cloneAttributeKey(p, cc.componentUUID, cc.getConfiguration().getUUIDs());
				instance.addProperty("subSplId", cc.componentUUID.toString() + "/" + cc.parentUUID + "/" + cloneKey);
				instance.add("selectedSubFeatures", ids);
				instances.add(instance);
			}
			variant.add("cloneInstances", instances);
			result.add(variant);
		}
		return result;
	}

	private static void collectSelectedFeatures(JsonObject node, Set<UUID> ids, JsonArray selected) {
		if (node.getAsJsonArray("ownedArtifactUUIDs") != null)
			for (JsonElement id : node.getAsJsonArray("ownedArtifactUUIDs"))
				if (ids.contains(UUID.fromString(id.getAsString()))) {
					selected.add(node.get("uuid").getAsString());
					break;
				}
		for (JsonElement child : node.getAsJsonArray("children"))
			collectSelectedFeatures(child.getAsJsonObject(), ids, selected);
	}

	private static JsonObject buildAsss(String splId, String rootName,
			List<? extends de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.Configuration> configs,
			List<Set<UUID>> uuidSets, JsonObject artifactIndex) {
		JsonObject root = feature(rootName, "FAMILY_ROOT", splId, true);
		java.util.Map<String, Set<UUID>> classes = new java.util.TreeMap<>();
		java.util.Map<String, JsonArray> variants = new java.util.TreeMap<>();
		Set<UUID> all = new java.util.HashSet<>();
		for (int i = 0; i < configs.size(); i++) {
			Set<UUID> set = uuidSets == null ? configs.get(i).getUUIDs() : uuidSets.get(i);
			all.addAll(set);
		}
		for (UUID id : all) {
			StringBuilder membership = new StringBuilder();
			JsonArray names = new JsonArray();
			for (int i = 0; i < configs.size(); i++) {
				Set<UUID> set = uuidSets == null ? configs.get(i).getUUIDs() : uuidSets.get(i);
				if (set.contains(id)) {
					membership.append('1');
					names.add(configs.get(i).getName());
				} else
					membership.append('0');
			}
			classes.computeIfAbsent(membership.toString(), k -> new java.util.TreeSet<>()).add(id);
			variants.put(membership.toString(), names);
		}
		JsonArray children = root.getAsJsonArray("children");
		int i = 0;
		for (String membership : classes.keySet()) {
			String label = variants.get(membership).toString();
			String name = membership.chars().filter(c -> c == '1').count() == configs.size() ? rootName
					: rootName + "#" + label;
			JsonObject f = feature(name, "ASSS_FEATURE", splId, false);
			f.addProperty("variability",
					membership.chars().filter(c -> c == '1').count() == configs.size() ? "MANDATORY" : "OPTIONAL");
			f.addProperty("groupVariability", "DEFAULT");
			JsonArray owned = new JsonArray();
			for (UUID id : classes.get(membership))
				owned.add(id.toString());
			f.add("ownedArtifactUUIDs", owned);
			children.add(f);
			i++;
		}
		// With two products, preserve the shared atomic set as the mandatory core
		// and group the two disjoint remainders as alternatives beneath an abstract
		// node.
		if (configs.size() == 2 && children.size() == 3) {
			JsonObject core = null;
			JsonArray remainders = new JsonArray();
			for (JsonElement child : children) {
				JsonObject feature = child.getAsJsonObject();
				if ("MANDATORY".equals(feature.get("variability").getAsString()))
					core = feature;
				else
					remainders.add(feature);
			}
			if (core != null && remainders.size() == 2) {
				JsonArray arrangedChildren = new JsonArray();
				arrangedChildren.add(core);
				JsonObject alternativeGroup = feature(rootName + " remainders", "ASSS_FEATURE", splId, false);
				alternativeGroup.addProperty("isAbstract", true);
				alternativeGroup.addProperty("groupVariability", "ALTERNATIVE");
				for (JsonElement remainder : remainders) {
					remainder.getAsJsonObject().addProperty("variability", "DEFAULT");
					alternativeGroup.getAsJsonArray("children").add(remainder);
				}
				arrangedChildren.add(alternativeGroup);
				root.add("children", arrangedChildren);
			}
		}
		return root;
	}

	private static JsonObject feature(String name, String kind, String splId, boolean isRoot) {
		JsonObject f = new JsonObject();
		f.addProperty("uuid", UUID.randomUUID().toString());
		f.addProperty("name", name);
		f.addProperty("isRoot", isRoot);
		f.addProperty("variability", isRoot ? "MANDATORY" : "OPTIONAL");
		f.addProperty("groupVariability", "DEFAULT");
		f.addProperty("isComponent", false);
		f.addProperty("kind", kind);
		f.addProperty("splId", splId);
		f.add("ownedArtifactUUIDs", new JsonArray());
		f.add("children", new JsonArray());
		return f;
	}

	private static JsonObject buildMspl(MPLPlatform p, JsonObject artifactIndex) {
		JsonObject mspl = new JsonObject();
		JsonArray variations = new JsonArray();
		mspl.add("familyRoot", buildAsss("FAMILY", "Family", p.configurations, null, artifactIndex));
		java.util.Map<String, JsonObject> byPair = new java.util.LinkedHashMap<>();
		for (int vi = 0; vi < p.configurations.size(); vi++) {
			de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.Configuration c = p.configurations.get(vi);
			java.util.Map<String, Integer> counts = new java.util.HashMap<>();
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.CloneConfiguration cc : c
					.getCloneConfigurations()) {
				String key = cc.componentUUID + "/" + cc.parentUUID;
				JsonObject v = byPair.get(key);
				if (v == null) {
					v = feature("Variation node", "VARIATION_NODE", "FAMILY", false);
					v.addProperty("isComponent", true);
					v.addProperty("componentUUID", cc.componentUUID.toString());
					v.addProperty("parentUUID", cc.parentUUID.toString());
					JsonObject card = new JsonObject();
					card.addProperty("min", 1);
					card.addProperty("max", 1);
					card.add("perVariant", new JsonObject());
					v.add("cardinality", card);
					v.add("subSPLs", new JsonArray());
					byPair.put(key, v);
					variations.add(v);
				}
				counts.put(key, counts.getOrDefault(key, 0) + 1);
			}
			for (String key : counts.keySet())
				byPair.get(key).getAsJsonObject("cardinality").getAsJsonObject("perVariant").addProperty(c.getName(),
						counts.get(key));
		}
		for (JsonElement variationElement : variations) {
			JsonObject v = variationElement.getAsJsonObject();
			JsonObject card = v.getAsJsonObject("cardinality");
			int min = Integer.MAX_VALUE, max = 0;
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.Configuration vc : p.configurations) {
				int count = card.getAsJsonObject("perVariant").has(vc.getName())
						? card.getAsJsonObject("perVariant").get(vc.getName()).getAsInt()
						: 0;
				min = Math.min(min, count);
				max = Math.max(max, count);
			}
			card.addProperty("min", min == Integer.MAX_VALUE ? 0 : min);
			card.addProperty("max", max);
			String id = v.get("componentUUID").getAsString() + "/" + v.get("parentUUID").getAsString();
			List<de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.Configuration> cloneConfigs = new java.util.ArrayList<>();
			List<Set<UUID>> sets = new java.util.ArrayList<>();
			List<String> keys = new java.util.ArrayList<>();
			List<Integer> variantIndexes = new java.util.ArrayList<>();
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.Configuration c : p.configurations) {
				int occurrenceIndex = 0;
				for (de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.CloneConfiguration cc : c
						.getCloneConfigurations()) {
					if (id.equals(cc.componentUUID + "/" + cc.parentUUID)) {
						cloneConfigs
								.add(new de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.ConfigurationImpl(
										c.getName()));
						sets.add(cc.getConfiguration().getUUIDs());
						keys.add(cloneAttributeKey(p, cc.componentUUID, cc.getConfiguration().getUUIDs()));
						variantIndexes.add(occurrenceIndex);
					}
					occurrenceIndex++;
				}
			}
			java.util.Map<String, List<Integer>> groups = new java.util.LinkedHashMap<>();
			for (int i = 0; i < keys.size(); i++)
				groups.computeIfAbsent(keys.get(i), k -> new java.util.ArrayList<>()).add(i);
			JsonArray subs = v.getAsJsonArray("subSPLs");
			for (String key : groups.keySet()) {
				JsonObject sub = new JsonObject();
				String sid = id + "/" + key;
				sub.addProperty("kind", "SUB_SPL");
				sub.addProperty("isComponent", true);
				sub.addProperty("splId", sid);
				sub.add("cloneKey", cloneKeyObject(p, v.get("componentUUID").getAsString(), key));
				JsonObject subCard = new JsonObject();
				subCard.addProperty("min", 1);
				subCard.addProperty("max", 1);
				JsonObject subPerVariant = new JsonObject();
				for (Integer ix : groups.get(key))
					subPerVariant.addProperty(cloneConfigs.get(ix).getName(), 1);
				subCard.add("perVariant", subPerVariant);
				sub.add("cardinality", subCard);
				JsonArray refs = new JsonArray();
				List<Set<UUID>> groupedSets = new java.util.ArrayList<>();
				List<de.tu_bs.cs.isf.e4cf.compare.data_structures.configuration.Configuration> groupedConfigs = new java.util.ArrayList<>();
				for (Integer ix : groups.get(key)) {
					JsonObject ref = new JsonObject();
					ref.addProperty("variant", cloneConfigs.get(ix).getName());
					ref.addProperty("index", variantIndexes.get(ix));
					refs.add(ref);
					groupedSets.add(sets.get(ix));
					groupedConfigs.add(cloneConfigs.get(ix));
				}
				sub.add("cloneConfigRefs", refs);
				sub.add("root", buildAsss(sid, key, groupedConfigs, groupedSets, artifactIndex));
				subs.add(sub);
			}
		}
		attachVariationNodes(mspl.getAsJsonObject("familyRoot"), variations);
		mspl.add("variationNodes", variations);
		return mspl;
	}

	private static void attachVariationNodes(JsonObject feature, JsonArray variations) {
		for (JsonElement e : variations) {
			JsonObject v = e.getAsJsonObject();
			String parent = v.get("parentUUID").getAsString();
			for (JsonElement id : feature.getAsJsonArray("ownedArtifactUUIDs"))
				if (parent.equals(id.getAsString())) {
					feature.getAsJsonArray("children").add(v);
					break;
				}
		}
		for (JsonElement child : feature.getAsJsonArray("children"))
			if (child.isJsonObject() && child.getAsJsonObject().has("children"))
				attachVariationNodes(child.getAsJsonObject(), variations);
	}

	private static de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node findNode(
			de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node root, UUID id) {
		java.util.ArrayDeque<de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node> q = new java.util.ArrayDeque<>();
		q.push(root);
		while (!q.isEmpty()) {
			de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node n = q.pop();
			if (id.equals(n.getUUID()))
				return n;
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node c : n.getChildren())
				q.push(c);
		}
		return null;
	}

	private static String cloneAttributeKey(MPLPlatform p, UUID component, Set<UUID> cloneIds) {
		de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node n = findNode(p.model, component);
		String found = null;
		int matches = 0;
		if (n != null)
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Attribute a : n.getAttributes())
				for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Value<?> val : a.getAttributeValues())
					if (cloneIds.contains(val.getUUID())) {
						found = a.getAttributeKey() + "=" + String.valueOf(val.getValue());
						matches++;
					}
		return matches == 1 ? found : "PAPER";
	}

	private static JsonObject cloneKeyObject(MPLPlatform p, String component, String key) {
		JsonObject out = new JsonObject();
		String[] parts = key.split("=", 2);
		out.addProperty("attributeKey", parts[0]);
		out.addProperty("value", parts.length > 1 ? parts[1] : "");
		String valueUUID = null;
		de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node n = findNode(p.model, UUID.fromString(component));
		if (n != null)
			for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Attribute a : n.getAttributes())
				if (a.getAttributeKey().equals(parts[0]))
					for (de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Value<?> v : a.getAttributeValues())
						if (String.valueOf(v.getValue()).equals(parts.length > 1 ? parts[1] : ""))
							valueUUID = v.getUUID().toString();
		if (valueUUID == null)
			out.add("valueUUID", com.google.gson.JsonNull.INSTANCE);
		else
			out.addProperty("valueUUID", valueUUID);
		return out;
	}

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
				String filename = file.getName().substring(0, file.getName().lastIndexOf("."));
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
