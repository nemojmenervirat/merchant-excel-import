package com.example.application.views;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.function.SerializablePredicate;

@CssImport("./styles/bootstrap.css")
public class TabsFilter<T> extends Tabs {

	private ListDataProvider<T> listDataProvider;
	private Map<Tab, SerializablePredicate<T>> filtersMap;
	private Map<Tab, Span> badgesMap;

	public TabsFilter(ListDataProvider<T> listDataProvider) {
		Objects.requireNonNull(listDataProvider);
		this.listDataProvider = listDataProvider;
		filtersMap = new HashMap<>();
		badgesMap = new HashMap<>();
		addSelectedChangeListener(e -> listDataProvider.setFilter(filtersMap.get(e.getSelectedTab())));
	}

	public void addFilterTab(String label) {
		addFilterTab(label, 0, null);
	}

	public void addFilterTab(String label, int count) {
		addFilterTab(label, count, null);
	}

	public void addFilterTab(String label, SerializablePredicate<T> filter) {
		addFilterTab(label, 0, filter);
	}

	public void addFilterTab(String label, int count, SerializablePredicate<T> filter) {
		Objects.requireNonNull(listDataProvider,
				"Za poziv addFilterTab potrebno je koristiti konstruktor koji prima ListDataProvider!");
		Tab tab = new Tab(label);
		Span badge = new Span(String.valueOf(count));
		badge.addClassNames("badge", count == 0 ? "badge-secondary" : "badge-info", "ml-2");
		tab.add(badge);
		badgesMap.put(tab, badge);
		if (filter != null) {
			filtersMap.put(tab, filter);
		} else {
			filtersMap.put(tab, e -> true);
		}
		add(tab);
	}

	public void refreshCounts() {
		Objects.requireNonNull(listDataProvider);
		for (Tab tab : badgesMap.keySet()) {
			long count = listDataProvider.getItems().stream().filter(filtersMap.get(tab)).count();
			Span badge = badgesMap.get(tab);
			badge.setText(String.valueOf(count));
			if (count > 0) {
				badge.removeClassNames("badge-secondary");
				badge.addClassNames("badge-info");
			} else {
				badge.removeClassNames("badge-info");
				badge.addClassNames("badge-secondary");
			}
		}

		listDataProvider.setFilter(filtersMap.get(getSelectedTab()));
	}

}
