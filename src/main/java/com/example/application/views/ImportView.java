package com.example.application.views;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.example.application.ImportItem;
import com.example.application.ProductTypes;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.Grid.Column;
import com.vaadin.flow.component.grid.Grid.SelectionMode;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@PageTitle("Import")
@Route(value = "import", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
public class ImportView extends VerticalLayout {

	private Upload upload = new Upload();
	private List<ImportItem> importItems = new ArrayList<>();
	private ListDataProvider<ImportItem> listDataProvider = new ListDataProvider<>(importItems);
	private TabsFilter<ImportItem> tabsFilter = new TabsFilter<>(listDataProvider);
	private Grid<ImportItem> grid = new Grid<>();
	private Label labelStatus = new Label();
	private HeaderRow headerRow;
	private List<String> headers;

	public ImportView() {

		MemoryBuffer buffer = new MemoryBuffer();
		upload.setReceiver(buffer);
		upload.setWidthFull();
		upload.setMaxFileSize(1024 * 1024 * 100);
		upload.addSucceededListener(e -> {
			importFile(buffer.getInputStream());
		});
		add(upload);

		tabsFilter.addFilterTab("Svi artikli");
		tabsFilter.addFilterTab("Prepoznat PT", e -> e.getProductType() != null);
		tabsFilter.addFilterTab("Nije prepoznat PT", e -> e.getProductType() == null);
		add(tabsFilter);

		grid.setDataProvider(listDataProvider);
		Column<ImportItem> c = grid.addComponentColumn(e -> {
			ComboBox<String> comboBoxPT = new ComboBox<>();
			comboBoxPT.setItems(ProductTypes.VALUES);
			comboBoxPT.setValue(e.getProductType());
			comboBoxPT.setWidthFull();
			comboBoxPT.addValueChangeListener(e1 -> {
				e.setProductType(e1.getValue());
				tabsFilter.refreshCounts();
			});
			return comboBoxPT;
		}).setHeader("Product Type").setResizable(true).setAutoWidth(true).setFrozen(true);
		grid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
		grid.setSelectionMode(SelectionMode.MULTI);
		headerRow = grid.appendHeaderRow();

		ComboBox<String> comboBoxPT = new ComboBox<>();
		comboBoxPT.setItems(ProductTypes.VALUES);
		comboBoxPT.setWidthFull();
		comboBoxPT.setPlaceholder("Podesi za izabrane");
		comboBoxPT.addValueChangeListener(e -> {
			grid.asMultiSelect().getSelectedItems().forEach(e1 -> e1.setProductType(e.getValue()));
			grid.getDataProvider().refreshAll();
			tabsFilter.refreshCounts();
			grid.asMultiSelect().deselectAll();
			comboBoxPT.clear();
		});
		headerRow.getCell(c).setComponent(comboBoxPT);
		add(grid);

		add(labelStatus);

		Button button = new Button("Predji na mapiranje kolona za Product Tipove");
		button.addClickListener(e -> {
			Set<String> pts = importItems.stream().map(pt -> pt.getProductType()).distinct()
					.filter(pt -> StringUtils.isNotBlank(pt)).collect(Collectors.toSet());

			for (String pt : pts) {
				add(new Hr());
				long count = importItems.stream()
						.filter(ii -> ii.getProductType() != null && ii.getProductType().equals(pt)).count();
				H4 h4 = new H4(pt + " (broj proizvoda: " + count + ")");
				add(h4);

				Grid<String> grid = new Grid<>();
				grid.setHeightByRows(true);
				for (String col : ProductTypes.getColumns(pt)) {
					grid.addComponentColumn(e1 -> {
						ComboBox<String> cbH = new ComboBox<>();
						cbH.setPlaceholder("Mapirajte trgovcevu kolonu u nasu");
						cbH.setItems(headers);
						cbH.setWidthFull();
						return cbH;
					}).setHeader(col).setResizable(true).setAutoWidth(true);
				}
				grid.setItems(List.of(""));
				add(grid);

				Button im = new Button("1. Importuj proizvode u pimcore");
				Button mi = new Button("2. Importuj u merchant inventory za izabranog trgovca");
				add(new HorizontalLayout(im, mi));
			}

		});
		add(button);

	}

	private void setStatusRow() {
		int size = listDataProvider == null ? 0 : listDataProvider.size(new Query<>());
		labelStatus.setText("Broj redova: " + size);
	}

	private TextField textFilter(ValueProvider<ImportItem, String> valueProvider) {
		TextField textField = new TextField();
		textField.setPlaceholder("Pretraga...");
		textField.setClearButtonVisible(true);
		textField.addThemeVariants(TextFieldVariant.LUMO_SMALL);
		textField.setSizeFull();
		textField.setValueChangeMode(ValueChangeMode.EAGER);
		textField.addValueChangeListener(e -> {
			listDataProvider.addFilter(valueProvider,
					value -> StringUtils.containsIgnoreCase(value, textField.getValue()));
			setStatusRow();
		});
		return textField;
	}

	private void importFile(InputStream inputStream) {
		try (Workbook workbook = WorkbookFactory.create(inputStream)) {
			importItems.clear();
			Sheet sheet = workbook.getSheetAt(0);
			headers = new ArrayList<>();
			for (Row row : sheet) {
				if (isRowEmpty(row)) {
					continue;
				}
				if (headers.isEmpty()) {
					Iterator<Cell> cells = row.cellIterator();
					while (cells.hasNext()) {
						Cell cell = cells.next();
						headers.add(cell.getStringCellValue());
						Column<ImportItem> c = grid.addColumn(e -> e.getValues().get(cell.getStringCellValue()))
								.setHeader(cell.getAddress().formatAsString() + " - " + cell.getStringCellValue())
								.setResizable(true).setAutoWidth(true);
						headerRow.getCell(c)
								.setComponent(textFilter(e -> e.getValues().get(cell.getStringCellValue())));
					}
				} else {
					ImportItem ii = new ImportItem();
					importItems.add(ii);
					for (int i = 0; i < headers.size(); i++) {
						Cell cell = row.getCell(i);
						String value = getStringFromCell(cell);
						ii.getValues().put(headers.get(i), value);
						if (ii.getProductType() == null && value != null) {
							ProductTypes.VALUES.stream().filter(e -> value.toLowerCase().equals(e.toLowerCase()))
									.findAny().ifPresent(e -> ii.setProductType(e));
						}
					}
				}
			}
			grid.getDataProvider().refreshAll();
			tabsFilter.refreshCounts();
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex);
		}
	}

	private boolean isRowEmpty(Row row) {
		for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
			Cell cell = row.getCell(c);
			if (cell != null && cell.getCellType() != CellType.BLANK) {
				return false;
			}
		}
		return true;
	}

	private String getStringFromCell(Cell cell) {
		String value = null;
		if (cell != null) {
			switch (cell.getCellType()) {
			case STRING:
				value = cell.getStringCellValue().trim();
				break;
			case BOOLEAN:
				boolean bool = cell.getBooleanCellValue();
				value = String.valueOf(bool);
				break;
			case NUMERIC:
				value = String.format("%.2f", cell.getNumericCellValue());
				break;
			case FORMULA:
				DataFormatter df = new DataFormatter();
				value = df.formatCellValue(cell);
				break;
			case BLANK:
				value = null;
				break;
			default:
				value = null;
				break;
			}
		}
		return value;
	}

}
