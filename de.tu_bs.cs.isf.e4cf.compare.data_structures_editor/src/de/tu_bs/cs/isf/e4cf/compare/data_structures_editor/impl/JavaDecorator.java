package de.tu_bs.cs.isf.e4cf.compare.data_structures_editor.impl;

import de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node;
import de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Tree;
import de.tu_bs.cs.isf.e4cf.compare.data_structures_editor.interfaces.NodeDecorator;
import de.tu_bs.cs.isf.e4cf.compare.data_structures_editor.stringtable.FileTable;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class JavaDecorator implements NodeDecorator {
	private static final Image JAVA_ICON = new Image("icons/java.png");

	@Override
	public boolean isSupportedTree(Tree tree) {
		return tree != null && "java".equalsIgnoreCase(tree.getFileExtension());
	}

	@Override
	public TreeItem<Node> decorateNode(TreeItem<Node> item) {
		String type = item.getValue().getNodeType();
		item.setGraphic(new ImageView("JAVA".equalsIgnoreCase(type) ? JAVA_ICON : FileTable.defaultImage));
		item.getValue().setRepresentation(type);
		return item;
	}

	@Override
	public String toString() {
		return "Java Code";
	}
}
