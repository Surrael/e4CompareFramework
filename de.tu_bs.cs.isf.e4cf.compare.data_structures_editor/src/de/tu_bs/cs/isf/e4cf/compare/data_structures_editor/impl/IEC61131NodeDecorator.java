package de.tu_bs.cs.isf.e4cf.compare.data_structures_editor.impl;

import de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Node;
import de.tu_bs.cs.isf.e4cf.compare.data_structures.interfaces.Tree;
import de.tu_bs.cs.isf.e4cf.compare.data_structures_editor.interfaces.NodeDecorator;
import javafx.scene.control.TreeItem;

public class IEC61131NodeDecorator implements NodeDecorator {
	private final FamilyModelNodeDecorator delegate = new FamilyModelNodeDecorator();

	@Override
	public boolean isSupportedTree(Tree tree) {
		return tree != null && ("project".equalsIgnoreCase(tree.getFileExtension())
				|| tree.getRoot() != null && "CONFIGURATION".equalsIgnoreCase(tree.getRoot().getNodeType()));
	}

	@Override
	public TreeItem<Node> decorateNode(TreeItem<Node> item) {
		return delegate.decorateNode(item);
	}

	@Override
	public String toString() {
		return "IEC 61131-3";
	}
}
