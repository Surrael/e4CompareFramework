
# e4CompareFramework
The e4CompareFramework provides methods for the reverse engineering of clone-and-own software variants.
Each part of the framework provides interfaces allowing the extension of the framework with further readers and writers and additional comparators for the variability mining method.

# features
- Supports following programming languages: Java, UML, ARC,Text, C,C++,C# 
- Feature Model Editor
- Constraint Editor
- Intra-Variability Mining
- Inter-Variability Minning
- Solution Space Feature Location and Idetification support
- Solution Space Feature Model Extraction and Refinement

# requirements
- Java: Oracle JDK 8u202 (64-bit), version 1.8.0_202
- Eclipse: Eclipse IDE for RCP and RAP Developers 2019-12 (4.14), 64-bit

The framework is built against JavaSE-1.8 and Eclipse e4 bundle versions from the 2019-12 train.
Using newer Eclipse/Java combinations may work, but this baseline is the most reliable setup.

# dependencies to install in Eclipse
Install the following dependencies in Eclipse if they are not already available in your package:

- e(fx)clipse / Eclipse FX (required):
	- org.eclipse.fx.core
	- org.eclipse.fx.ui.controls
	- org.eclipse.fx.osgi.util
- Eclipse GEF 5.x and Zest FX (required):
	- org.eclipse.gef.common
	- org.eclipse.gef.fx
	- org.eclipse.gef.mvc.fx
	- org.eclipse.gef.graph
	- org.eclipse.gef.zest.fx
- SAT4J 2.3.5 (required)
- Xtext/Xtend runtime bundles (required by evaluation plug-in):
	- org.eclipse.xtext.xbase.lib
	- org.eclipse.xtend.lib
	- org.eclipse.xtend.lib.macro

Notes:
- Many third-party libraries are already bundled in this repository (for example Gson, OpenCSV, ANTLR, JavaParser, Guava, and others via plug-in lib folders).
- If Eclipse reports missing bundles after import, use Help -> Install New Software and add/update the missing IU from the release update site.

# setup
Step 1: Clone the e4CompareFramework repository.<br/>

Step 2: Import all plug-ins into the workspace.<br/>
- File -> Import -> Existing Projects into Workspace<br/>
- Select root directory: e4CompareFramework<br/>
- Enable Search for nested projects<br/>
- Select all projects and finish import<br/>

Step 3: Configure Java 8 execution environment.<br/>
- Window -> Preferences -> Java -> Installed JREs: add/select Oracle JDK 1.8.0_202 (64-bit)<br/>
- Window -> Preferences -> Java -> Installed JREs -> Execution Environments: map JavaSE-1.8 to that JDK<br/>

Step 4: Install required Eclipse dependencies.<br/>
- Help -> Install New Software -> install GEF/Draw2d/Zest bundles if missing<br/>
- Help -> Eclipse Marketplace (or Install New Software) -> install e(fx)clipse bundles if missing<br/>
- Ensure SAT4J and Xtext/Xtend runtime bundles are present<br/>

Step 5: (Optional but recommended) set API baseline.<br/>
- Window -> Preferences -> Plug-in Development -> API Baselines -> Add Baseline -> Refresh -> Finish<br/>

Step 6: Start the application.<br/>
- Open project de.tu_bs.cs.isf.e4cf.core<br/>
- Right-click de.tu_bs.cs.isf.e4cf.core.product -> Run As -> Eclipse Application<br/>

# troubleshooting missing bundles
If Eclipse shows missing bundles after import, install dependencies in this order:

1. Install from the Eclipse 2019-12 release repository first:
	- https://download.eclipse.org/releases/2019-12
	- This usually resolves most e4/platform, SAT4J, and Orbit-based dependencies.

2. If GEF/Zest bundles are still missing, add GEF update site:
	- https://download.eclipse.org/tools/gef/updates/releases/

3. If Eclipse FX bundles are still missing, add e(fx)clipse update site:
	- https://download.eclipse.org/efxclipse/updates-released/2.5.0/site

4. If Xtext/Xtend runtime bundles are still missing, add Xtext release site:
	- https://download.eclipse.org/modeling/tmf/xtext/updates/releases/

5. Re-run dependency resolution:
	- Project -> Clean...
	- Then restart Eclipse with -clean once if required.

Common symptom mapping:
- org.eclipse.fx.* missing: install from e(fx)clipse site.
- org.eclipse.gef.* or org.eclipse.gef.zest.* missing: install from GEF site.
- org.eclipse.xtext.xbase.lib or org.eclipse.xtend.lib* missing: install from Xtext site.
- org.sat4j.* missing: install from Eclipse 2019-12 release repository.


YouTubeChanel with Tutorials:\
https://www.youtube.com/channel/UC1BJ0giECWnRGM-Ck7TET5w/?guided_help_flow=5\
