package de.tu_bs.cs.isf.e4cf.compare.comparator_view.view;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.inject.Inject;

import de.tu_bs.cs.isf.e4cf.compare.comparator.impl.node.StringComparator;
import de.tu_bs.cs.isf.e4cf.compare.comparator.interfaces.Comparator;
import de.tu_bs.cs.isf.e4cf.compare.comparator.util.ComparisonUtil;
import de.tu_bs.cs.isf.e4cf.compare.comparator_view.ComparatorViewController;
import de.tu_bs.cs.isf.e4cf.compare.comparator_view.components.ComparatorCell;
import de.tu_bs.cs.isf.e4cf.compare.metric_view.components.FXComparatorElement;
import de.tu_bs.cs.isf.e4cf.compare.metric_view.stringtable.MetricST;
import de.tu_bs.cs.isf.e4cf.core.util.ServiceContainer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;

public class ComparatorView implements Initializable {

	private ComparatorViewController cvc;
	
	@Inject
	private ServiceContainer serviceContainer;

	@FXML
	TreeView<Comparator> treeView;
	@FXML
	private TreeTableView<FXComparatorElement> treeTable;
	@FXML
	private TreeTableColumn<FXComparatorElement, String> comparatorColumn;
	
	@FXML
	private TextField filterField;

	private List<FXComparatorElement> allComparatorElements = new ArrayList<>();

	/**
	 * this method loads on click the ComparatorTree
	 */
	@FXML
	private void loadComparators(ActionEvent event) {
		event.consume();
		sendComparators(null); // default value, sends every element in the list to metricview
	}

	@FXML
	private void addComparator(Event event) {
		event.consume();
		System.out.println("Sysout selection" + treeTable.getSelectionModel().getSelectedItem().getValue());
		ObservableList<FXComparatorElement> comparators = FXCollections.observableArrayList();
		for (TreeItem<FXComparatorElement> elem : treeTable.getSelectionModel().getSelectedItems()) {
			if (!treeTable.getRoot().getChildren().contains(elem)) {
				comparators.add(elem.getValue());
			}
		}
		sendComparators(comparators);
	}


	

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		initButtons();
		initTable();
	}

	public void initButtons() {

	}

	/**
	 * method to send selected comparators to the metricView
	 * 
	 * @param comparators list of comparators if null sends each comparator
	 */
	private void sendComparators(ObservableList<FXComparatorElement> comparators) {
		serviceContainer.partService.showPart(MetricST.BUNDLE_NAME);
		if (comparators == null) {
			ObservableList<FXComparatorElement> temp = FXCollections.observableArrayList();
			treeTable.getSelectionModel().selectAll();
			treeTable.getSelectionModel().clearSelection(0);
			for (TreeItem<FXComparatorElement> elem : treeTable.getSelectionModel().getSelectedItems()) {
				temp.add(elem.getValue());
			}
			treeTable.getSelectionModel().clearSelection();
			
			serviceContainer.eventBroker.send("comparatorListEvent", temp);
		} else {
			System.out.println("comparators: " + comparators);
			serviceContainer.eventBroker.send("comparatorListEvent", comparators);
		}

	}

	/**
	 * Method to get a list of all elements that should be added to the treeview
	 * possible that it will later not be needed
	 * 
	 * @return list of NodeComparators
	 */
	private List<FXComparatorElement> getFxComparatorElements() {
		ObservableList<Comparator> comparatorList = FXCollections
				.observableArrayList(ComparisonUtil.getComparator());
		List<FXComparatorElement> fxComparatorElementsList = new ArrayList<>();
		comparatorList.stream().forEach(elem -> fxComparatorElementsList.add(new FXComparatorElement(elem)));
		return fxComparatorElementsList;
	}	
	
	private void initTable() {
		comparatorColumn.setCellFactory(new Callback<TreeTableColumn<FXComparatorElement, String>, TreeTableCell<FXComparatorElement, String>>() {
			@Override
			public TreeTableCell<FXComparatorElement, String> call(TreeTableColumn<FXComparatorElement, String> e) {
				return new ComparatorCell(serviceContainer);
			}
		});
		comparatorColumn.setCellValueFactory(new TreeItemPropertyValueFactory<>("name"));
		
		
 		
		treeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		allComparatorElements = getFxComparatorElements();
		treeTable.setRoot(buildTree(filterField.getText()));
		filterField.textProperty().addListener((obs, oldValue, newValue) -> {
			treeTable.setRoot(buildTree(newValue));
		});
		treeTable.setShowRoot(false);
		
	}

	private TreeItem<FXComparatorElement> buildTree(String filterText) {
		TreeItem<FXComparatorElement> root = new TreeItem<>(new FXComparatorElement("root"));
		Map<String, TreeItem<FXComparatorElement>> typeNodes = new java.util.HashMap<>();
		Set<String> availableTypes = new HashSet<>();
		String normalizedFilter = filterText == null ? "" : filterText.trim().toLowerCase();

		allComparatorElements.forEach(elem -> availableTypes.add(elem.getComparatorType()));
		availableTypes.forEach(type -> {
			TreeItem<FXComparatorElement> subRootNode = new TreeItem<>(new FXComparatorElement(type));
			subRootNode.setExpanded(true);
			typeNodes.put(type, subRootNode);
			root.getChildren().add(subRootNode);
		});

		allComparatorElements.forEach(elem -> {
			if (normalizedFilter.isEmpty() || elem.getName().toLowerCase().contains(normalizedFilter)) {
				TreeItem<FXComparatorElement> parent = typeNodes.get(elem.getComparatorType());
				if (parent != null) {
					parent.getChildren().add(new TreeItem<>(elem));
				}
			}
		});

		root.getChildren().removeIf(typeNode -> typeNode.getChildren().isEmpty());
		root.setExpanded(true);
		return root;
	}
	
	private FXComparatorElement createElement(Comparator comparator) {
		return  new FXComparatorElement(comparator);
		
	}

}


