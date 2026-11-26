package de.tu_bs.cs.isf.e4cf.extractive_mple.structure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Builds tree-connected, artifact-named MSPL features from a serialized 150% tree. */
final class StructuralMsplSynthesizer {
	static final String MODE_PROPERTY = "e4cf.mspl.synthesisMode";
	static final String STRUCTURAL = "STRUCTURAL";
	static final String LEGACY = "LEGACY_ASSS";
	static final int DEFAULT_GROUP_MIN_SIBLINGS = 3;
	static final int DEFAULT_MAX_FAN_OUT = 12;
	private static final UUID UUID_NAMESPACE = UUID.fromString("2acdb77d-4a3b-5b17-a0b8-3d7bb6b12345");
	private static final List<String> IDENTIFIERS = Arrays.asList("name", "identifier", "symbol", "local_id", "condition");
	private final JsonObject platform;
	private final JsonObject model;
	private final JsonObject artifactIndex;
	private final JsonArray variants;
	private final Map<String, JsonObject> featureByArtifact = new HashMap<>();
	private final Map<String, Set<String>> unfeatured = new LinkedHashMap<>();
	private int groupMinSiblings = DEFAULT_GROUP_MIN_SIBLINGS;
	private int maxFanOut = DEFAULT_MAX_FAN_OUT;
	private boolean mergeAtomicSiblings = true;
	private boolean orGroups = false;
	private final Set<String> boundaryNodeTypes = Collections.emptySet();
	private String activeSplId;
	private Set<String> activeProvenance = Collections.emptySet();
	private Map<String, Set<String>> activeMembership = Collections.emptyMap();

	private StructuralMsplSynthesizer(JsonObject platform) {
		this.platform = platform;
		this.model = platform.getAsJsonObject("model");
		this.artifactIndex = platform.getAsJsonObject("artifactIndex");
		this.variants = platform.getAsJsonArray("configurations");
	}

	static void synthesize(JsonObject platform) {
		if (platform == null || !platform.has("model") || !platform.has("configurations")) return;
		new StructuralMsplSynthesizer(platform).run();
	}

	private void run() {
		JsonObject oldDiagram = platform.getAsJsonObject("featureDiagram");
		JsonObject structural = buildSpl("FAMILY", "Family", variantViews(), true, null);
		JsonObject mspl = new JsonObject();
		JsonArray variationNodes = new JsonArray();
		Map<String, JsonObject> familyNodes = new LinkedHashMap<>();
		Map<String, List<ConfigView>> subConfigs = new LinkedHashMap<>();
		Map<String, List<CloneRef>> subRefs = new LinkedHashMap<>();
		Map<String, Map<String, Integer>> countsByPair = new LinkedHashMap<>();
		for (JsonElement variantElement : variants) {
			JsonObject variant = variantElement.getAsJsonObject();
			String variantName = variant.get("configName").getAsString();
			JsonArray components = variant.getAsJsonArray("componentConfigurations");
			Map<String, Integer> localIndexes = new HashMap<>();
			for (JsonElement componentElement : components) {
				JsonObject cc = componentElement.getAsJsonObject();
				String componentId = cc.get("componentUUID").getAsString();
				String parentId = cc.get("parentUUID").getAsString();
				String pair = componentId + "/" + parentId;
				int index = localIndexes.getOrDefault(pair, 0);
				localIndexes.put(pair, index + 1);
				String subId = pair + "/" + cloneKey(cc, componentId);
				JsonObject cloneConfig = cc.getAsJsonObject("configuration");
				Set<String> cloneUUIDs = uuidSet(cloneConfig.getAsJsonArray("configuration"));
				subConfigs.computeIfAbsent(subId, k -> new ArrayList<>()).add(new ConfigView(variantName + "#" + index, variantName, cloneUUIDs));
				subRefs.computeIfAbsent(subId, k -> new ArrayList<>()).add(new CloneRef(variantName,index));
				countsByPair.computeIfAbsent(pair,k->new LinkedHashMap<>()).put(variantName,index+1);
				JsonObject family = familyNodes.computeIfAbsent(pair,k->{JsonObject f=new JsonObject();f.addProperty("componentUUID",componentId);f.addProperty("parentUUID",parentId);f.add("subSplIds",new JsonArray());f.add("subSPLs",new JsonArray());return f;});
				if (!arrayContains(family.getAsJsonArray("subSplIds"),subId)) family.getAsJsonArray("subSplIds").add(subId);
			}
		}
		Map<String, JsonObject> subRoots = new LinkedHashMap<>();
		for (String subId : subConfigs.keySet()) {
			List<ConfigView> views = subConfigs.get(subId);
			String componentId = subId.substring(0, subId.indexOf('/'));
			String subName = subId.substring(subId.lastIndexOf('/') + 1);
			JsonObject subRoot = buildSpl(subId, subName, views, false, componentId);
			JsonObject sub = new JsonObject();sub.addProperty("subSplId",subId);sub.addProperty("kind","SUB_SPL_ROOT");sub.addProperty("name",subName);sub.add("root",subRoot);
			JsonArray refs=new JsonArray();for(CloneRef r:subRefs.get(subId)){JsonObject rr=new JsonObject();rr.addProperty("variant",r.variant);rr.addProperty("index",r.index);refs.add(rr);}sub.add("cloneConfigRefs",refs);
			JsonObject card=new JsonObject();JsonObject pv=new JsonObject();int cmin=Integer.MAX_VALUE,cmax=0;for(JsonElement variant:variants){String vn=str(variant.getAsJsonObject(),"configName");int count=0;for(CloneRef cr:subRefs.get(subId))if(vn.equals(cr.variant))count++;pv.addProperty(vn,count);cmin=Math.min(cmin,count);cmax=Math.max(cmax,count);}card.addProperty("min",cmin==Integer.MAX_VALUE?0:cmin);card.addProperty("max",cmax);card.add("perVariant",pv);sub.add("cardinality",card);
			subRoots.put(subId,subRoot);
			String pair=subId.substring(0,subId.lastIndexOf('/'));
			familyNodes.get(pair).getAsJsonArray("subSPLs").add(sub);
		}
		for(Map.Entry<String,JsonObject> entry:familyNodes.entrySet()){
			String pair=entry.getKey();JsonObject family=entry.getValue();JsonObject card=new JsonObject();int min=Integer.MAX_VALUE,max=0;JsonObject per=new JsonObject();
			for(JsonElement e:variants){String n=str(e.getAsJsonObject(),"configName");int count=countsByPair.getOrDefault(pair,Collections.emptyMap()).getOrDefault(n,0);per.addProperty(n,count);min=Math.min(min,count);max=Math.max(max,count);}
			card.addProperty("min",min==Integer.MAX_VALUE?0:min);card.addProperty("max",max);card.add("perVariant",per);family.add("cardinality",card);family.addProperty("kind","VARIATION_NODE");family.addProperty("isComponent",true);variationNodes.add(family);
			String parentUuid=str(family,"parentUUID");
			JsonObject owner=findFeatureOwning(structural,parentUuid);
			if(owner==null){owner=structural;family.addProperty("warning","parentUUID owner not found; attached to family root");}
			for(JsonElement se:family.getAsJsonArray("subSplIds")){
				String sid=se.getAsString();
				String subName=sid.substring(sid.lastIndexOf('/')+1);
				JsonObject ref=feature(subName,"SUB_SPL_REF","FAMILY",parentUuid,null,null);
				ref.addProperty("isComponent",true);ref.addProperty("subSplId",sid);
				JsonObject refCard=new JsonObject();JsonObject refPerVariant=new JsonObject();JsonArray present=new JsonArray();Set<String> presentIds=new LinkedHashSet<>();int refMin=Integer.MAX_VALUE,refMax=0;
				for(JsonElement variant:variants){String vn=str(variant.getAsJsonObject(),"configName");int n=0;for(CloneRef cr:subRefs.getOrDefault(sid,Collections.emptyList()))if(vn.equals(cr.variant))n++;refPerVariant.addProperty(vn,n);refMin=Math.min(refMin,n);refMax=Math.max(refMax,n);if(n>0){present.add(vn);presentIds.add(vn);}}
				refCard.addProperty("min",refMin==Integer.MAX_VALUE?0:refMin);refCard.addProperty("max",refMax);refCard.add("perVariant",refPerVariant);ref.add("cardinality",refCard);ref.add("presentIn",present);ref.addProperty("asssClass",membershipKey(presentIds));owner.getAsJsonArray("children").add(ref);
			}
			JsonArray parentVariations=owner.getAsJsonArray("variationNodes");if(parentVariations==null){parentVariations=new JsonArray();owner.add("variationNodes",parentVariations);}
			JsonObject variationMetadata=new JsonObject();variationMetadata.addProperty("componentUUID",str(family,"componentUUID"));variationMetadata.addProperty("parentUUID",parentUuid);variationMetadata.add("cardinality",family.get("cardinality").deepCopy());variationMetadata.add("subSplIds",family.get("subSplIds").deepCopy());parentVariations.add(variationMetadata);
		}
		applyVariability(structural);
		uniquifyNames(structural,new HashSet<String>());
		JsonObject oldRoot = oldDiagram == null ? null : oldDiagram.getAsJsonObject("root");
		if (oldDiagram == null) oldDiagram = new JsonObject();
		if (oldRoot != null) oldDiagram.add("legacyRoot", oldRoot);
		oldDiagram.add("root", structural);
		oldDiagram.addProperty("schemaVersion", 3);
		mspl.add("variationNodes", variationNodes);
		platform.add("featureDiagram", oldDiagram);
		oldDiagram.add("mspl", mspl);
		platform.add("mspl", mspl);
		platform.add("unfeaturedArtifacts", toJsonMap(unfeatured));
		platform.add("asssClasses", collectAsssClasses(structural, variationNodes));
		platform.add("varClassDiagnostics", collectVarClassDiagnostics(structural, subRoots));
		JsonArray selections = buildSelections(structural, familyNodes, subRoots);
		platform.add("featureConfigurations", selections);
		platform.add("selectionValidation", validateSelections(structural, subRoots, selections));
		platform.addProperty("selectionValidationRejectsEmpty", rejectsEmptySelections(structural, subRoots));
		platform.addProperty("schemaVersion", 3);
	}
	private static boolean arrayContains(JsonArray a,String value){if(a==null)return false;for(JsonElement e:a)if(value.equals(e.getAsString()))return true;return false;}

	private List<ConfigView> variantViews() {
		List<ConfigView> result = new ArrayList<>();
		for (JsonElement e : variants) { JsonObject c=e.getAsJsonObject(); result.add(new ConfigView(c.get("configName").getAsString(),c.get("configName").getAsString(),uuidSet(c.getAsJsonArray("configuration")))); }
		return result;
	}

	private JsonObject buildSpl(String splId, String rootName, List<ConfigView> configs, boolean family, String componentId) {
		activeSplId = splId;
		Map<String, Set<String>> membership = new HashMap<>();
		for (ConfigView c : configs) for (String id : c.uuids) membership.computeIfAbsent(id,k->new LinkedHashSet<>()).add(c.id);
		Set<String> universe = new LinkedHashSet<>(membership.keySet());
		activeProvenance = provenanceIds(configs);
		for (String id : activeProvenance) { universe.remove(id); for(ConfigView c:configs)if(c.uuids.contains(id))unfeatured.computeIfAbsent(c.variant,k->new LinkedHashSet<>()).add(id); }
		activeMembership = membership;
		Region rootRegion = new Region(model, membership.getOrDefault(str(model,"uuid"),Collections.emptySet()), true);
		if (componentId != null) rootRegion = new Region(findNode(componentId), membership.getOrDefault(componentId,Collections.emptySet()), true);
		walk(rootRegion.node, rootRegion, membership, universe, configs, new LinkedHashMap<>());
		JsonObject root = emit(rootRegion, splId, rootName, configs, true);
		Set<String> allConfigIds = new LinkedHashSet<>();
		for (ConfigView config : configs) allConfigIds.add(config.id);
		root.add("presentIn", names(allConfigIds, configs));
		root.addProperty("asssClass", membershipKey(allConfigIds));
		if (mergeAtomicSiblings) mergeAtomicRegions(root);
		applyVariability(root);
		applyHierarchyGroups(root, groupMinSiblings, maxFanOut);
		applyVariability(root);
		uniquifyNames(root, new HashSet<String>());
		return root;
	}

	private void walk(JsonObject node, Region current, Map<String,Set<String>> membership, Set<String> universe, List<ConfigView> configs, Map<String,Region> nodeRegions) {
		String id=str(node,"uuid"); Set<String> nodeMembership=membership.getOrDefault(id,Collections.emptySet());
		Region region=current;
		if (!nodeMembership.isEmpty() && !nodeMembership.equals(current.membership) && !boundaryNodeTypes.contains(str(node,"nodeType"))) {
			region=new Region(node,nodeMembership,false); region.parent=current; current.children.add(region);
		}
		if (universe.contains(id)) { region.owned.add(id); region.nodeMembers.add(id); }
		nodeRegions.put(id,region);
		JsonArray attrs=node.getAsJsonArray("attributes");
		if(attrs!=null) for(JsonElement ae:attrs){JsonObject a=ae.getAsJsonObject();String keyId=str(a,"uuid");Set<String> km=membership.getOrDefault(keyId,nodeMembership);if(universe.contains(keyId)){if(km.equals(region.membership))region.owned.add(keyId);else{JsonObject keyFeature=feature(a.get("attributeKey").getAsString()+"_key","ATTRIBUTE_GROUP",activeSplId,id,a.get("attributeKey").getAsString(),null);keyFeature.addProperty("uuid",uuid5(activeSplId+"|attribute-key|"+keyId));keyFeature.addProperty("visibility","DETAIL");keyFeature.add("ownedArtifactUUIDs",singleton(keyId));keyFeature.add("presentIn",names(km,configs));keyFeature.addProperty("asssClass",membershipKey(km));region.details.add(keyFeature);region.diagnostics.add("attribute-key-membership:"+keyId);}}
			JsonArray vals=a.getAsJsonArray("attributeValues");if(vals==null)continue;JsonArray differing=new JsonArray();for(JsonElement ve:vals){JsonObject value=ve.getAsJsonObject();String valueId=str(value,"uuid");if(!universe.contains(valueId))continue;Set<String> vm=membership.getOrDefault(valueId,Collections.emptySet());if(vm.equals(region.membership))region.owned.add(valueId);else differing.add(value);}
			if(differing.size()>0){JsonObject group=feature(a.get("attributeKey").getAsString(),"ATTRIBUTE_GROUP",null,id,a.get("attributeKey").getAsString(),null);group.addProperty("visibility","DETAIL");group.addProperty("splId",activeSplId);group.addProperty("isAbstract",true);group.add("children",new JsonArray());Set<String> alternativeUnion=new LinkedHashSet<>();boolean exactAlternative=differing.size()>1;for(JsonElement ve:differing){JsonObject val=ve.getAsJsonObject();Set<String> vm=membership.getOrDefault(str(val,"uuid"),Collections.emptySet());JsonObject vf=feature(a.get("attributeKey").getAsString()+"="+str(val,"value"),"ATTRIBUTE_VALUE",null,id,a.get("attributeKey").getAsString(),new String[]{a.get("attributeKey").getAsString(),str(val,"value")});vf.addProperty("uuid",uuid5(activeSplId+"|attribute-value|"+str(val,"uuid")));vf.addProperty("visibility","DETAIL");vf.add("ownedArtifactUUIDs",singleton(str(val,"uuid")));vf.add("presentIn",names(vm,configs));vf.addProperty("asssClass",membershipKey(vm));vf.addProperty("splId",activeSplId);for(String member:vm)if(!alternativeUnion.add(member))exactAlternative=false;group.getAsJsonArray("children").add(vf);}group.add("presentIn",names(alternativeUnion,configs));group.addProperty("asssClass",membershipKey(alternativeUnion));if(exactAlternative&&alternativeUnion.equals(region.membership)){group.addProperty("groupVariability","ALTERNATIVE");for(JsonElement child:group.getAsJsonArray("children"))child.getAsJsonObject().addProperty("variability","DEFAULT");}region.details.add(group);}
		}
		JsonArray children=node.getAsJsonArray("children");if(children!=null)for(JsonElement c:children)walk(c.getAsJsonObject(),region,membership,universe,configs,nodeRegions);
	}

	private JsonObject emit(Region region,String splId,String fallback,List<ConfigView> configs,boolean root) {
		String[] identifying=root?identify(region.node):identifyRegion(region);
		String name=root?fallback:(identifying==null?safeName(fallback+"."+str(region.node,"nodeType")):truncate(identifying[1],40));
		String kind=root?(splId.equals("FAMILY")?"FAMILY_ROOT":"SUB_SPL_ROOT"):"REGION";
		JsonObject f=feature(name,kind,splId,str(region.node,"uuid"),str(region.node,"nodeType"),identifying);
		f.add("ownedArtifactUUIDs",toArray(region.owned));f.add("presentIn",names(region.membership,configs));f.add("members",toArray(region.nodeMembers));f.addProperty("asssClass",membershipKey(region.membership));
		f.addProperty("variability",root||region.parent==null||region.parent.membership.containsAll(region.membership)?"MANDATORY":"OPTIONAL");
		f.addProperty("uuid",uuid5(splId+"|"+str(region.node,"uuid")+"|"+membershipKey(region.membership)+"|"+kind));
		JsonArray aliases=f.getAsJsonArray("aliases");if(identifying!=null){JsonArray attrs=region.node.getAsJsonArray("attributes");if(attrs!=null)for(JsonElement ae:attrs){JsonObject a=ae.getAsJsonObject();if(identifying[0].equals(str(a,"attributeKey")))for(JsonElement ve:a.getAsJsonArray("attributeValues")){JsonObject value=ve.getAsJsonObject();String val=str(value,"value");if(!activeProvenance.contains(str(value,"uuid"))&&!val.equals(identifying[1])&&!arrayContains(aliases,val))aliases.add(val);}}}
		if(!region.details.isEmpty())for(JsonObject d:region.details)f.getAsJsonArray("children").add(d);
		List<Region> ordered=new ArrayList<>(region.children);ordered.sort(Comparator.comparingInt(r->treeOrder(r.node)));
		for(Region child:ordered)f.getAsJsonArray("children").add(emit(child,splId,name,configs,false));
		if(!region.diagnostics.isEmpty()){JsonArray ds=new JsonArray();for(String d:region.diagnostics)ds.add(d);f.add("diagnostics",ds);}
		return f;
	}

	private void applyHierarchyGroups(JsonObject root,int minSiblings,int maxFan){
		JsonArray children=root.getAsJsonArray("children");if(children==null||children.size()==0)return;if(maxFan<1)maxFan=DEFAULT_MAX_FAN_OUT;
		if(!"GROUP".equals(str(root,"kind"))&&!"ALTERNATIVE".equals(str(root,"groupVariability"))){
			Map<String,List<JsonElement>> byType=new LinkedHashMap<>();for(JsonElement e:children){JsonObject c=e.getAsJsonObject();if("REGION".equals(str(c,"kind")))byType.computeIfAbsent(str(c,"nodeType"),k->new ArrayList<>()).add(e);}
			Set<JsonElement> grouped=new HashSet<>();JsonArray rewritten=new JsonArray();int ordinal=0;
			for(Map.Entry<String,List<JsonElement>> entry:byType.entrySet())if(entry.getValue().size()>=minSiblings){grouped.addAll(entry.getValue());for(JsonArray chunk:membershipChunks(entry.getValue(),maxFan)){String signature=membershipKey(memberSetForElements(chunk));rewritten.add(createHierarchyGroup(root,plural(entry.getKey()),entry.getKey(),chunk,"type|"+entry.getKey()+"|"+signature+"|"+(ordinal++)));}}
			for(JsonElement child:children)if(!grouped.contains(child))rewritten.add(child);root.add("children",rewritten);children=rewritten;
		}
		if(children.size()>maxFan){JsonArray limited=new JsonArray();int ordinal=0;for(JsonArray chunk:membershipChunks(children,maxFan))limited.add(createHierarchyGroup(root,"Variant_group_"+(++ordinal),"",chunk,"bucket|"+ordinal+"|"+membershipKey(memberSetForElements(chunk))));if(limited.size()>maxFan){JsonArray packed=new JsonArray();int pack=0;for(int start=0;start<limited.size();start+=maxFan){JsonArray chunk=new JsonArray();for(int i=start;i<Math.min(start+maxFan,limited.size());i++)chunk.add(limited.get(i));packed.add(createHierarchyGroup(root,"Variant_group_pack_"+(++pack),"",chunk,"pack|"+pack));}limited=packed;}root.add("children",limited);children=limited;}
		for(JsonElement child:children)if(child.getAsJsonObject().has("children"))applyHierarchyGroups(child.getAsJsonObject(),minSiblings,maxFan);
	}
	private JsonObject createHierarchyGroup(JsonObject parent,String name,String nodeType,JsonArray members,String discriminator){
		JsonObject group=feature(name,"GROUP",str(parent,"splId"),null,nodeType,null);group.addProperty("uuid",uuid5(str(parent,"uuid")+"|group|"+discriminator));group.addProperty("isAbstract",true);group.add("children",members);group.add("presentIn",unionPresent(members));Set<String> configurations=new LinkedHashSet<>();Set<String> nodeMembers=new LinkedHashSet<>();for(JsonElement child:members){JsonObject f=child.getAsJsonObject();configurations.addAll(memberSet(f));JsonArray ownedNodes=f.getAsJsonArray("members");if(ownedNodes!=null)for(JsonElement id:ownedNodes)nodeMembers.add(id.getAsString());}group.addProperty("asssClass",membershipKey(configurations));group.add("members",toArray(nodeMembers));return group;
	}
	private List<JsonArray> membershipChunks(Iterable<JsonElement> elements,int maxSize){Map<String,List<JsonElement>> byMembership=new LinkedHashMap<>();for(JsonElement e:elements){String key=str(e.getAsJsonObject(),"asssClass");byMembership.computeIfAbsent(key,k->new ArrayList<>()).add(e);}List<JsonArray> chunks=new ArrayList<>();for(Map.Entry<String,List<JsonElement>> entry:byMembership.entrySet()){List<JsonElement> values=entry.getValue();for(int start=0;start<values.size();start+=maxSize){JsonArray chunk=new JsonArray();for(int i=start;i<Math.min(start+maxSize,values.size());i++)chunk.add(values.get(i));chunks.add(chunk);}}return chunks;}
	private Set<String> memberSetForElements(Iterable<JsonElement> elements){Set<String> result=new LinkedHashSet<>();for(JsonElement e:elements)result.addAll(memberSet(e.getAsJsonObject()));return result;}
	private void mergeAtomicRegions(JsonObject parent){JsonArray children=parent.getAsJsonArray("children");if(children==null)return;for(JsonElement e:children)mergeAtomicRegions(e.getAsJsonObject());Map<String,JsonObject> first=new LinkedHashMap<>();Set<String> remove=new HashSet<>();for(JsonElement e:children){JsonObject f=e.getAsJsonObject();if(!"REGION".equals(str(f,"kind"))||hasRegionChild(f))continue;String key=str(f,"nodeType")+"|"+str(f,"asssClass");JsonObject base=first.get(key);if(base==null){first.put(key,f);continue;}mergeArray(base,"ownedArtifactUUIDs",f.getAsJsonArray("ownedArtifactUUIDs"));mergeArray(base,"members",f.getAsJsonArray("members"));mergeArray(base,"aliases",f.getAsJsonArray("aliases"));mergeArray(base,"children",f.getAsJsonArray("children"));if(!arrayContains(base.getAsJsonArray("members"),str(f,"regionRootUUID")))base.getAsJsonArray("members").add(str(f,"regionRootUUID"));remove.add(str(f,"uuid"));}if(!remove.isEmpty()){JsonArray kept=new JsonArray();for(JsonElement e:children)if(!remove.contains(str(e.getAsJsonObject(),"uuid")))kept.add(e);parent.add("children",kept);}}
	private boolean hasRegionChild(JsonObject f){JsonArray c=f.getAsJsonArray("children");if(c==null)return false;for(JsonElement e:c)if("REGION".equals(str(e.getAsJsonObject(),"kind")))return true;return false;}
	private void mergeArray(JsonObject target,String field,JsonArray values){if(values==null)return;JsonArray into=target.getAsJsonArray(field);for(JsonElement e:values){boolean exists=false;for(JsonElement old:into)if(old.equals(e)){exists=true;break;}if(!exists)into.add(e);}}
	private void applyVariability(JsonObject parent){
		JsonArray children=parent.getAsJsonArray("children");if(children==null||children.size()==0)return;
		Set<String> parentMembers=memberSet(parent);Map<String,List<JsonObject>> byType=new LinkedHashMap<>();
		for(JsonElement ce:children){JsonObject child=ce.getAsJsonObject();String type=str(child,"nodeType");if("REGION".equals(str(child,"kind"))&&!type.isEmpty())byType.computeIfAbsent(type,k->new ArrayList<>()).add(child);}
		List<JsonObject> alternativeGroups=new ArrayList<>();
		for(Map.Entry<String,List<JsonObject>> entry:byType.entrySet()){
			List<JsonObject> siblings=entry.getValue();if(siblings.size()<2)continue;
			Set<String> union=new LinkedHashSet<>();boolean disjoint=true;
			for(JsonObject sibling:siblings)for(String id:memberSet(sibling))if(!union.add(id))disjoint=false;
			if(!disjoint||!union.equals(parentMembers))continue;
			boolean allChildrenAreSlot=true;
			for(JsonElement child:children){JsonObject c=child.getAsJsonObject();if(!siblings.contains(c)){allChildrenAreSlot=false;break;}}
			if(allChildrenAreSlot){parent.addProperty("groupVariability","ALTERNATIVE");}
			else{JsonArray slotChildren=new JsonArray();for(JsonObject sibling:siblings)slotChildren.add(sibling);JsonObject slot=createHierarchyGroup(parent,plural(entry.getKey()),entry.getKey(),slotChildren,"alternative|"+entry.getKey()+"|"+membershipKey(union));slot.addProperty("groupVariability","ALTERNATIVE");alternativeGroups.add(slot);}
		}
		if(!alternativeGroups.isEmpty()){
			JsonArray rewritten=new JsonArray();Set<JsonObject> consumed=new HashSet<>();Map<JsonObject,JsonObject> groupAtFirstSibling=new HashMap<>();
			for(JsonObject group:alternativeGroups){JsonArray slot=group.getAsJsonArray("children");for(JsonElement child:slot)consumed.add(child.getAsJsonObject());if(slot.size()>0)groupAtFirstSibling.put(slot.get(0).getAsJsonObject(),group);}
			for(JsonElement child:children){JsonObject c=child.getAsJsonObject();JsonObject group=groupAtFirstSibling.get(c);if(group!=null)rewritten.add(group);if(!consumed.contains(c))rewritten.add(c);}parent.add("children",rewritten);children=rewritten;
		}
		boolean parentIsAlternative="ALTERNATIVE".equals(str(parent,"groupVariability"));
		for(JsonElement ce:children){JsonObject child=ce.getAsJsonObject();Set<String> childMembers=memberSet(child);boolean mandatory=childMembers.containsAll(parentMembers);child.addProperty("variability",parentIsAlternative?"DEFAULT":mandatory?"MANDATORY":"OPTIONAL");}
		for(JsonElement ce:children)applyVariability(ce.getAsJsonObject());
	}
	private Set<String> memberSet(JsonObject f){Set<String> result=new LinkedHashSet<>();String m=str(f,"asssClass");if(!m.isEmpty())result.addAll(Arrays.asList(m.split("\\|")));return result;}
	private void uniquifyNames(JsonObject root,Set<String> used){String base=str(root,"name");String name=base;if(!used.add(name)){name=safeName(base+"_"+str(root,"uuid").substring(0,8));root.addProperty("name",name);used.add(name);}JsonArray c=root.getAsJsonArray("children");if(c!=null)for(JsonElement e:c)uniquifyNames(e.getAsJsonObject(),used);}

	private JsonObject findFeatureOwning(JsonObject f,String id){JsonArray owned=f.getAsJsonArray("ownedArtifactUUIDs");if(owned!=null)for(JsonElement e:owned)if(id.equals(e.getAsString()))return f;JsonArray c=f.getAsJsonArray("children");if(c!=null)for(JsonElement e:c){JsonObject found=findFeatureOwning(e.getAsJsonObject(),id);if(found!=null)return found;}return null;}
	private JsonArray selectedFeatures(JsonObject root,Set<String> ids){Set<String> selected=new LinkedHashSet<>();collectSelected(root,ids,selected);JsonArray out=new JsonArray();for(String s:selected)out.add(s);return out;}
	private boolean collectSelected(JsonObject f,Set<String> ids,Set<String> selected){boolean has=false;JsonArray own=f.getAsJsonArray("ownedArtifactUUIDs");if(own!=null)for(JsonElement e:own)if(ids.contains(e.getAsString()))has=true;JsonArray cs=f.getAsJsonArray("children");if(cs!=null)for(JsonElement e:cs)if(collectSelected(e.getAsJsonObject(),ids,selected))has=true;if(has)selected.add(str(f,"uuid"));return has;}
	private JsonArray buildSelections(JsonObject family,Map<String,JsonObject> families,Map<String,JsonObject> subRoots){JsonArray out=new JsonArray();for(JsonElement ve:variants){JsonObject c=ve.getAsJsonObject();String name=str(c,"configName");JsonObject item=new JsonObject();item.addProperty("variantName",name);JsonArray familySelected=selectedFeatures(family,uuidSet(c.getAsJsonArray("configuration")));selectRefs(family,name,familySelected);item.add("selectedFamilyFeatures",familySelected);JsonArray instances=new JsonArray();Map<String,Integer> indexes=new HashMap<>();for(JsonElement ce:c.getAsJsonArray("componentConfigurations")){JsonObject cc=ce.getAsJsonObject();String pair=str(cc,"componentUUID")+"/"+str(cc,"parentUUID");int index=indexes.getOrDefault(pair,0);indexes.put(pair,index+1);String sid=pair+"/"+cloneKey(cc,str(cc,"componentUUID"));JsonObject inst=new JsonObject();inst.addProperty("subSplId",sid);inst.addProperty("cloneIndex",index);JsonObject root=subRoots.get(sid);Set<String> ids=uuidSet(cc.getAsJsonObject("configuration").getAsJsonArray("configuration"));inst.add("selectedSubFeatures",root==null?new JsonArray():selectedFeatures(root,ids));instances.add(inst);}item.add("cloneInstances",instances);item.add("unfeaturedArtifacts",toArray(unfeatured.getOrDefault(name,Collections.emptySet())));out.add(item);}return out;}
	private boolean selectRefs(JsonObject f,String variant,JsonArray selected){boolean active=false;if("SUB_SPL_REF".equals(str(f,"kind"))){JsonArray p=f.getAsJsonArray("presentIn");if(p!=null)for(JsonElement e:p)if(variant.equals(e.getAsString())){addUnique(selected,str(f,"uuid"));active=true;}}JsonArray c=f.getAsJsonArray("children");if(c!=null)for(JsonElement e:c)if(selectRefs(e.getAsJsonObject(),variant,selected))active=true;if(active)addUnique(selected,str(f,"uuid"));return active;}
	private void addUnique(JsonArray values,String value){for(JsonElement e:values)if(value.equals(e.getAsString()))return;values.add(value);}
	private JsonArray validateSelections(JsonObject family,Map<String,JsonObject> subRoots,JsonArray selections){
		JsonArray results=new JsonArray();Map<String,Set<String>> ownership=new LinkedHashMap<>();collectFeatureOwnership(family,ownership);for(JsonObject root:subRoots.values())collectFeatureOwnership(root,ownership);
		for(JsonElement e:selections){JsonObject selection=e.getAsJsonObject();String variant=str(selection,"variantName");List<String> errors=new ArrayList<>();
			Set<String> expected=new LinkedHashSet<>();for(JsonElement v:variants){JsonObject candidate=v.getAsJsonObject();if(variant.equals(str(candidate,"configName"))){expected.addAll(uuidSet(candidate.getAsJsonArray("configuration")));JsonArray clones=candidate.getAsJsonArray("componentConfigurations");if(clones!=null)for(JsonElement clone:clones)expected.addAll(uuidSet(clone.getAsJsonObject().getAsJsonObject("configuration").getAsJsonArray("configuration")));break;}}
			Set<String> selectedFeatures=uuidSet(selection.getAsJsonArray("selectedFamilyFeatures"));JsonArray instances=selection.getAsJsonArray("cloneInstances");if(instances!=null)for(JsonElement instance:instances){JsonObject i=instance.getAsJsonObject();if(!subRoots.containsKey(str(i,"subSplId")))errors.add("unknown subSplId: "+str(i,"subSplId"));selectedFeatures.addAll(uuidSet(i.getAsJsonArray("selectedSubFeatures")));}
			Set<String> unfeaturedIds=uuidSet(selection.getAsJsonArray("unfeaturedArtifacts"));if(!isValidSelection(expected,selectedFeatures,ownership,unfeaturedIds))errors.add("selected features do not reconstruct the expected artifact set");
			JsonObject result=new JsonObject();result.addProperty("variantName",variant);result.addProperty("valid",errors.isEmpty());JsonArray errorList=new JsonArray();for(String error:errors)errorList.add(error);result.add("errors",errorList);results.add(result);
		}
		return results;
	}
	static boolean isValidSelection(Set<String> expectedArtifacts,Set<String> selectedFeatureIds,Map<String,Set<String>> ownership,Set<String> unfeaturedArtifacts){
		if(expectedArtifacts==null||selectedFeatureIds==null||ownership==null||unfeaturedArtifacts==null)return false;
		if(!expectedArtifacts.isEmpty()&&selectedFeatureIds.isEmpty())return false;
		Set<String> covered=new LinkedHashSet<>(unfeaturedArtifacts);for(String featureId:selectedFeatureIds){Set<String> featureArtifacts=ownership.get(featureId);if(featureArtifacts==null)return false;covered.addAll(featureArtifacts);}return covered.equals(expectedArtifacts);
	}
	private boolean rejectsEmptySelections(JsonObject family,Map<String,JsonObject> subRoots){Map<String,Set<String>> ownership=new LinkedHashMap<>();collectFeatureOwnership(family,ownership);for(JsonObject root:subRoots.values())collectFeatureOwnership(root,ownership);for(JsonElement element:variants){JsonObject variant=element.getAsJsonObject();Set<String> expected=uuidSet(variant.getAsJsonArray("configuration"));JsonArray clones=variant.getAsJsonArray("componentConfigurations");if(clones!=null)for(JsonElement clone:clones)expected.addAll(uuidSet(clone.getAsJsonObject().getAsJsonObject("configuration").getAsJsonArray("configuration")));if(isValidSelection(expected,Collections.<String>emptySet(),ownership,Collections.<String>emptySet()))return false;}return true;}
	private void collectFeatureOwnership(JsonObject feature,Map<String,Set<String>> ownership){ownership.put(str(feature,"uuid"),uuidSet(feature.getAsJsonArray("ownedArtifactUUIDs")));JsonArray children=feature.getAsJsonArray("children");if(children!=null)for(JsonElement child:children)collectFeatureOwnership(child.getAsJsonObject(),ownership);}
	private void collectOwnedForSelection(JsonObject feature,Set<String> selected,Set<String> out){if(selected.contains(str(feature,"uuid"))){JsonArray own=feature.getAsJsonArray("ownedArtifactUUIDs");if(own!=null)for(JsonElement id:own)out.add(id.getAsString());}JsonArray children=feature.getAsJsonArray("children");if(children!=null)for(JsonElement child:children)collectOwnedForSelection(child.getAsJsonObject(),selected,out);}
private JsonArray collectAsssClasses(JsonObject family,JsonArray variations){Map<String,JsonObject> classes=new LinkedHashMap<>();collectClasses(family,classes);for(JsonElement e:variations){JsonObject v=e.getAsJsonObject();JsonArray subs=v.getAsJsonArray("subSPLs");if(subs!=null)for(JsonElement s:subs)collectClasses(s.getAsJsonObject().getAsJsonObject("root"),classes);}JsonArray out=new JsonArray();for(JsonObject c:classes.values())out.add(c);return out;}
	private JsonArray collectVarClassDiagnostics(JsonObject family,Map<String,JsonObject> subRoots){JsonArray out=new JsonArray();collectVarClassDiagnostics(family,null,out);for(JsonObject root:subRoots.values())collectVarClassDiagnostics(root,null,out);return out;}
	private void collectVarClassDiagnostics(JsonObject feature,JsonObject parent,JsonArray out){String nodeId=str(feature,"regionRootUUID");JsonObject node=findNode(nodeId);String source=node==null?"":str(node,"varClass");if(parent!=null&&"REGION".equals(str(feature,"kind"))&&!source.isEmpty()){String recomputed=str(feature,"variability");if(!source.equalsIgnoreCase(recomputed)){JsonObject d=new JsonObject();d.addProperty("splId",str(feature,"splId"));d.addProperty("featureUUID",str(feature,"uuid"));d.addProperty("nodeUUID",nodeId);d.addProperty("nodeType",str(feature,"nodeType"));d.addProperty("sourceVarClass",source);d.addProperty("recomputedVariability",recomputed);out.add(d);}}JsonArray children=feature.getAsJsonArray("children");if(children!=null)for(JsonElement child:children)collectVarClassDiagnostics(child.getAsJsonObject(),feature,out);}
	private void collectClasses(JsonObject f,Map<String,JsonObject> classes){if(f.has("asssClass")&&f.has("presentIn")){String id=str(f,"splId")+"|"+str(f,"asssClass");JsonObject c=classes.get(id);if(c==null){c=new JsonObject();c.addProperty("id",id);c.add("presentIn",f.get("presentIn").deepCopy());c.add("featureUUIDs",new JsonArray());classes.put(id,c);}c.getAsJsonArray("featureUUIDs").add(str(f,"uuid"));}JsonArray children=f.getAsJsonArray("children");if(children!=null)for(JsonElement e:children)collectClasses(e.getAsJsonObject(),classes);}
	private Set<String> provenanceIds(List<ConfigView> configs){Set<String> labels=new HashSet<>();for(ConfigView c:configs){labels.add(c.variant);labels.add(stripProject(c.variant));}Set<String> out=new java.util.TreeSet<>();for(Map.Entry<String,JsonElement> e:artifactIndex.entrySet()){JsonObject a=e.getValue().getAsJsonObject();if(!"VALUE".equals(str(a,"kind")))continue;String value=a.has("value")?a.get("value").getAsString():"";String type=str(a,"nodeType"),key=str(a,"attributeKey");boolean exactName=labels.contains(value);boolean provenanceField=(("CONFIGURATION".equals(type)&&"identifier".equals(key))||("RESOURCE".equals(type)&&"name".equals(key)))&&labels.contains(value);if(exactName||provenanceField)out.add(e.getKey());}return out;}
	private String cloneKey(JsonObject cc,String component){Set<String> ids=uuidSet(cc.getAsJsonObject("configuration").getAsJsonArray("configuration"));JsonObject n=findNode(component);if(n!=null){for(String key:IDENTIFIERS){JsonArray attrs=n.getAsJsonArray("attributes");if(attrs!=null)for(JsonElement ae:attrs){JsonObject a=ae.getAsJsonObject();if(key.equals(str(a,"attributeKey")))for(JsonElement ve:a.getAsJsonArray("attributeValues")){JsonObject v=ve.getAsJsonObject();if(ids.contains(str(v,"uuid")))return str(v,"value");}}}}return component;}
	private JsonObject findNode(String uuid){return findNodeRecursive(model,uuid);}private JsonObject findNodeRecursive(JsonObject n,String uuid){if(uuid.equals(str(n,"uuid")))return n;JsonArray c=n.getAsJsonArray("children");if(c!=null)for(JsonElement e:c){JsonObject found=findNodeRecursive(e.getAsJsonObject(),uuid);if(found!=null)return found;}return null;}
	private JsonObject feature(String name,String kind,String splId,String regionRoot,String nodeType,String[] identifying){String ownerSpl=splId==null?activeSplId:splId;JsonObject f=new JsonObject();f.addProperty("uuid",uuid5((ownerSpl==null?"":ownerSpl)+"|"+(regionRoot==null?"":regionRoot)+"|"+name+"|"+kind));f.addProperty("name",safeName(name));f.addProperty("label",name);f.addProperty("kind",kind);f.addProperty("splId",ownerSpl==null?"":ownerSpl);f.addProperty("nodeType",nodeType==null?"":nodeType);f.addProperty("regionRootUUID",regionRoot==null?"":regionRoot);if(identifying!=null){JsonObject i=new JsonObject();i.addProperty("key",identifying[0]);i.addProperty("value",identifying[1]);f.add("identifyingAttribute",i);}else f.add("identifyingAttribute",JsonNull.INSTANCE);f.add("aliases",new JsonArray());f.add("members",new JsonArray());f.addProperty("asssClass","");f.addProperty("visibility","MAIN");f.add("presentIn",new JsonArray());f.add("ownedArtifactUUIDs",new JsonArray());f.addProperty("variability","OPTIONAL");f.add("groupVariability",JsonNull.INSTANCE);f.addProperty("isAbstract",false);f.addProperty("isComponent",false);f.add("children",new JsonArray());return f;}
	private String[] identify(JsonObject node){JsonArray attrs=node.getAsJsonArray("attributes");if(attrs==null)return null;for(String k:IDENTIFIERS){String value=identifierValue(node,k);if(value!=null)return new String[]{k,value};}return null;}
	private String[] identifyRegion(Region region){List<Region> siblings=new ArrayList<>();if(region.parent!=null)for(Region sibling:region.parent.children)if(str(sibling.node,"nodeType").equals(str(region.node,"nodeType")))siblings.add(sibling);if(siblings.isEmpty())siblings.add(region);for(String key:IDENTIFIERS){Map<Region,String> labels=new LinkedHashMap<>();Set<String> distinct=new HashSet<>();for(Region sibling:siblings){String value=identifierValue(sibling.node,key);if(value!=null){labels.put(sibling,value);distinct.add(value);}}int minimumDistinct=(siblings.size()*2+2)/3;if(distinct.size()>=minimumDistinct&&labels.containsKey(region))return new String[]{key,labels.get(region)};}return null;}
	private String identifierValue(JsonObject node,String key){JsonArray attrs=node.getAsJsonArray("attributes");if(attrs==null)return null;for(JsonElement ae:attrs){JsonObject attr=ae.getAsJsonObject();if(!key.equals(str(attr,"attributeKey")))continue;List<String> values=new ArrayList<>();JsonArray attributeValues=attr.getAsJsonArray("attributeValues");if(attributeValues==null)continue;for(JsonElement ve:attributeValues){JsonObject value=ve.getAsJsonObject();String valueId=str(value,"uuid");if(!activeProvenance.contains(valueId)&&!activeMembership.getOrDefault(valueId,Collections.emptySet()).isEmpty())values.add(str(value,"value"));}if(values.isEmpty())continue;values.sort((x,y)->{int cx=membershipCount(attr,key,x),cy=membershipCount(attr,key,y);return cx==cy?x.compareTo(y):Integer.compare(cy,cx);});return values.get(0);}return null;}
	private int membershipCount(JsonObject attr,String key,String value){int count=0;for(JsonElement e:attr.getAsJsonArray("attributeValues")){JsonObject v=e.getAsJsonObject();if(value.equals(str(v,"value")))count+=activeMembership.getOrDefault(str(v,"uuid"),Collections.emptySet()).size();}return count;}
	private static JsonArray unionPresent(JsonArray children){Set<String>s=new LinkedHashSet<>();for(JsonElement e:children){JsonArray a=e.getAsJsonObject().getAsJsonArray("presentIn");if(a!=null)for(JsonElement x:a)s.add(x.getAsString());}return toArray(s);}
	private static JsonArray unionPresent(List<JsonElement> children){Set<String>s=new LinkedHashSet<>();for(JsonElement e:children){JsonArray a=e.getAsJsonObject().getAsJsonArray("presentIn");if(a!=null)for(JsonElement x:a)s.add(x.getAsString());}return toArray(s);}
	private static JsonArray names(Set<String> ids,List<ConfigView> configs){JsonArray a=new JsonArray();Set<String> seen=new LinkedHashSet<>();for(ConfigView c:configs)if(ids.contains(c.id))seen.add(c.variant);for(String n:seen)a.add(n);return a;}
	private static String membershipKey(Set<String>s){List<String>a=new ArrayList<>(s);Collections.sort(a);return String.join("|",a);}
	private static Set<String> uuidSet(JsonArray a){Set<String>s=new LinkedHashSet<>();if(a!=null)for(JsonElement e:a)s.add(e.getAsString());return s;}
	private static JsonArray singleton(String s){JsonArray a=new JsonArray();a.add(s);return a;}
	private static JsonArray toArray(Iterable<String>s){JsonArray a=new JsonArray();for(String x:s)a.add(x);return a;}
	private static JsonObject toJsonMap(Map<String,Set<String>>m){JsonObject o=new JsonObject();for(Map.Entry<String,Set<String>>e:m.entrySet())o.add(e.getKey(),toArray(e.getValue()));return o;}
	private static String str(JsonObject o,String k){return o!=null&&o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
	private static String stripProject(String s){return s.endsWith(".project")?s.substring(0,s.length()-8):s;}
	private static String safeName(String s){String n=s.replaceAll("[^A-Za-z0-9_]","_");if(n.isEmpty()||Character.isDigit(n.charAt(0)))n="Feature_"+n;return n;}
	private static String truncate(String s,int n){return s.length()<=n?s:s.substring(0,n);}
	private static String plural(String s){if(s.endsWith("S"))return s;return s+"s";}
	private int treeOrder(JsonObject n){return n.has("startLine")?n.get("startLine").getAsInt():Integer.MAX_VALUE;}
	private static String uuid5(String name){try{MessageDigest md=MessageDigest.getInstance("SHA-1");ByteBuffer b=ByteBuffer.allocate(16);b.putLong(UUID_NAMESPACE.getMostSignificantBits()).putLong(UUID_NAMESPACE.getLeastSignificantBits());md.update(b.array());byte[] h=md.digest(name.getBytes(StandardCharsets.UTF_8));h[6]=(byte)((h[6]&0x0f)|0x50);h[8]=(byte)((h[8]&0x3f)|0x80);ByteBuffer out=ByteBuffer.wrap(h);return new UUID(out.getLong(),out.getLong()).toString();}catch(Exception e){throw new IllegalStateException(e);}}
	private static final class ConfigView{final String id,variant;final Set<String>uuids;ConfigView(String id,String variant,Set<String>uuids){this.id=id;this.variant=variant;this.uuids=uuids;}}
	private static final class Region{final JsonObject node;final Set<String>membership;final boolean root;Region parent;final Set<String>owned=new LinkedHashSet<>();final Set<String>nodeMembers=new LinkedHashSet<>();final List<Region>children=new ArrayList<>();final List<JsonObject>details=new ArrayList<>();final List<String>diagnostics=new ArrayList<>();Region(JsonObject n,Set<String>m,boolean r){node=n;membership=new LinkedHashSet<>(m);root=r;}}
	private static final class CloneRef{final String variant;final int index;CloneRef(String v,int i){variant=v;index=i;}}
}
