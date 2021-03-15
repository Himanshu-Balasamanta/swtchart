/*******************************************************************************
 * Copyright (c) 2020, 2021 Lablicate GmbH.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 * Philip Wenig - initial API and implementation
 *******************************************************************************/
package org.eclipse.swtchart.extensions.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swtchart.ISeries;
import org.eclipse.swtchart.extensions.internal.marker.EmbeddedLegend;
import org.eclipse.swtchart.extensions.internal.support.PositionValidator;
import org.eclipse.swtchart.extensions.internal.support.SeriesLabelProvider;
import org.eclipse.swtchart.extensions.internal.support.SeriesMapper;
import org.eclipse.swtchart.extensions.preferences.PreferenceConstants;
import org.eclipse.swtchart.extensions.preferences.PreferencePage;
import org.eclipse.swtchart.internal.series.Series;

public class ExtendedLegendUI extends Composite {

	private static final String MENU_TEXT = "Series PopUp Menu";
	//
	private ScrollableChart scrollableChart;
	//
	private Text textX;
	private Text textY;
	private SeriesListUI seriesListUI;
	//
	private EmbeddedLegend embeddedLegend;
	private boolean capturePosition = false;
	//
	private List<Control> controls = new ArrayList<>();
	//
	private IPreferenceStore preferenceStore = ResourceSupport.getPreferenceStore();

	public ExtendedLegendUI(Composite parent, int style) {

		super(parent, style);
		createControl();
	}

	public void setScrollableChart(ScrollableChart scrollableChart) {

		this.scrollableChart = scrollableChart;
		seriesListUI.setScrollableChart(scrollableChart);
		createEmbeddedLegend();
	}

	public void setInput(Object input) {

		/*
		 * First adjust the settings.
		 */
		if(input instanceof ISeries<?>[] && scrollableChart != null) {
			ISeries<?>[] seriesArray = (ISeries<?>[])input;
			BaseChart baseChart = scrollableChart.getBaseChart();
			SeriesMapper seriesMapper = new SeriesMapper(baseChart);
			seriesMapper.adjustSettings(seriesArray);
		}
		/*
		 * Then fill the series list.
		 */
		seriesListUI.setInput(input);
	}

	private void createControl() {

		setLayout(new GridLayout(1, true));
		//
		createToolbarMain(this);
		seriesListUI = createListSection(this);
		//
		updateControls();
		applySettings();
	}

	private void createToolbarMain(Composite parent) {

		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		composite.setLayout(new GridLayout(11, false));
		//
		add(createButtonMove(composite, ResourceSupport.ARROW_LEFT, "Move Legend Left"));
		add(createButtonMove(composite, ResourceSupport.ARROW_UP, "Move Legend Up"));
		add(createButtonMove(composite, ResourceSupport.ARROW_DOWN, "Move Legend Down"));
		add(createButtonMove(composite, ResourceSupport.ARROW_RIGHT, "Move Legend Right"));
		add(textX = createTextPositionX(composite));
		add(textY = createTextPositionY(composite));
		add(createButtonSetPosition(composite));
		createButtonToggleLegend(composite);
		createButtonToggleSort(composite);
		createButtonMappings(composite);
		createButtonSettings(composite);
	}

	private void add(Control control) {

		controls.add(control);
	}

	private Button createButtonMove(Composite parent, String icon, String tooltip) {

		Button button = new Button(parent, SWT.PUSH);
		button.setText("");
		button.setToolTipText(tooltip);
		button.setImage(ResourceSupport.getImage(icon));
		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				if(embeddedLegend != null) {
					/*
					 * Current position
					 */
					int moveX = preferenceStore != null ? preferenceStore.getInt(PreferenceConstants.P_MOVE_LEGEND_X) : PreferenceConstants.DEF_MOVE_LEGEND_X;
					int moveY = preferenceStore != null ? preferenceStore.getInt(PreferenceConstants.P_MOVE_LEGEND_Y) : PreferenceConstants.DEF_MOVE_LEGEND_Y;
					int x = embeddedLegend.getX();
					int y = embeddedLegend.getY();
					/*
					 * Modify the position
					 */
					switch(icon) {
						case ResourceSupport.ARROW_LEFT:
							x -= moveX;
							break;
						case ResourceSupport.ARROW_UP:
							y -= moveY;
							break;
						case ResourceSupport.ARROW_DOWN:
							y += moveY;
							break;
						case ResourceSupport.ARROW_RIGHT:
							x += moveX;
							break;
					}
					/*
					 * Update the position
					 */
					updateLegendPosition(x, y, true);
				}
			}
		});
		//
		return button;
	}

	private Text createTextPositionX(Composite parent) {

		Text text = new Text(parent, SWT.BORDER);
		text.setText("");
		text.setToolTipText("Legend Position X");
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.grabExcessHorizontalSpace = true;
		gridData.minimumWidth = 80;
		text.setLayoutData(gridData);
		//
		PositionValidator validator = new PositionValidator();
		ControlDecoration controlDecoration = new ControlDecoration(text, SWT.LEFT | SWT.TOP);
		//
		text.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent e) {

				if(validate(validator, controlDecoration, text)) {
					if(preferenceStore != null) {
						preferenceStore.setValue(PreferenceConstants.P_LEGEND_POSITION_X, validator.getPosition());
						updateLegendPosition(true);
					}
				}
			}
		});
		//
		return text;
	}

	private Text createTextPositionY(Composite parent) {

		Text text = new Text(parent, SWT.BORDER);
		text.setText("");
		text.setToolTipText("Legend Position Y");
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.grabExcessHorizontalSpace = true;
		gridData.minimumWidth = 80;
		text.setLayoutData(gridData);
		//
		PositionValidator validator = new PositionValidator();
		ControlDecoration controlDecoration = new ControlDecoration(text, SWT.LEFT | SWT.TOP);
		//
		text.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent e) {

				if(validate(validator, controlDecoration, text)) {
					if(preferenceStore != null) {
						preferenceStore.setValue(PreferenceConstants.P_LEGEND_POSITION_Y, validator.getPosition());
						updateLegendPosition(true);
					}
				}
			}
		});
		//
		return text;
	}

	private Button createButtonSetPosition(Composite parent) {

		Button button = new Button(parent, SWT.PUSH);
		button.setText("");
		button.setToolTipText("Set the position of the legend.");
		button.setImage(ResourceSupport.getImage(ResourceSupport.ICON_POSITION));
		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				capturePosition = MessageDialog.openConfirm(e.display.getActiveShell(), "Legend Position", "Set the position manually by using left mouse button double-click in the chart.");
			}
		});
		//
		return button;
	}

	private Button createButtonToggleLegend(Composite parent) {

		Button button = new Button(parent, SWT.PUSH);
		button.setText("");
		button.setToolTipText("Toggle the visibility of the embedded legend.");
		button.setImage(ResourceSupport.getImage(ResourceSupport.ICON_LEGEND));
		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				if(embeddedLegend != null) {
					/*
					 * Show/Hide
					 */
					boolean draw = embeddedLegend.isDraw();
					embeddedLegend.setDraw(!draw);
					//
					if(scrollableChart != null) {
						scrollableChart.redraw();
					}
					//
					updateControls();
				}
			}
		});
		//
		return button;
	}

	private Button createButtonToggleSort(Composite parent) {

		Button button = new Button(parent, SWT.CHECK);
		button.setText("");
		button.setToolTipText("Sort the table.");
		button.setSelection(preferenceStore.getBoolean(PreferenceConstants.P_SORT_LEGEND_TABLE));
		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				boolean enable = button.getSelection();
				seriesListUI.setTableSortable(enable);
				preferenceStore.setValue(PreferenceConstants.P_SORT_LEGEND_TABLE, enable);
				seriesListUI.getTable().redraw();
			}
		});
		//
		return button;
	}

	private Button createButtonMappings(Composite parent) {

		Button button = new Button(parent, SWT.PUSH);
		button.setText("");
		button.setToolTipText("Displays the mappings.");
		button.setImage(ResourceSupport.getImage(ResourceSupport.ICON_MAPPINGS));
		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				MappingsDialog mappingsDialog = new MappingsDialog(e.display.getActiveShell());
				mappingsDialog.open();
			}
		});
		//
		return button;
	}

	private Button createButtonSettings(Composite parent) {

		Button button = new Button(parent, SWT.PUSH);
		button.setText("");
		button.setToolTipText("Open the settings page.");
		button.setImage(ResourceSupport.getImage(ResourceSupport.ICON_SETTINGS));
		button.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				PreferenceManager preferenceManager = new PreferenceManager();
				preferenceManager.addToRoot(new PreferenceNode("1", new PreferencePage()));
				PreferenceDialog preferenceDialog = new PreferenceDialog(e.display.getActiveShell(), preferenceManager);
				preferenceDialog.create();
				preferenceDialog.setMessage("Settings");
				if(preferenceDialog.open() == Window.OK) {
					try {
						applySettings();
					} catch(Exception e1) {
						MessageDialog.openError(e.display.getActiveShell(), "Settings", "Something has gone wrong to apply the settings.");
					}
				}
			}
		});
		//
		return button;
	}

	private void applySettings() {

		updateLegendPosition(true);
	}

	private SeriesListUI createListSection(Composite parent) {

		SeriesListUI seriesListUI = new SeriesListUI(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		seriesListUI.setTableSortable(preferenceStore.getBoolean(PreferenceConstants.P_SORT_LEGEND_TABLE));
		Table table = seriesListUI.getTable();
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		/*
		 * Pop-Up Menu
		 */
		String menuId = getClass().getCanonicalName();
		MenuManager menuManager = new MenuManager(MENU_TEXT, menuId);
		menuManager.setRemoveAllWhenShown(true);
		addMenuSetColor(menuManager);
		Menu menu = menuManager.createContextMenu(table);
		table.setMenu(menu);
		//
		return seriesListUI;
	}

	private void addMenuSetColor(MenuManager menuManager) {

		menuManager.addMenuListener(new IMenuListener() {

			@Override
			public void menuAboutToShow(IMenuManager menuManager) {

				menuManager.add(new Action() {

					@Override
					public String getText() {

						return "Set Color";
					}

					@Override
					public String getToolTipText() {

						return "Adjust the color of the selected series.";
					}

					@Override
					public void run() {

						Table table = seriesListUI.getTable();
						List<ISeries<?>> selectedSeries = getSelectedSeries();
						//
						if(selectedSeries.size() > 0) {
							Color colorActive = SeriesLabelProvider.getColor(selectedSeries.get(0));
							RGB rgbActive = (colorActive == null) ? new RGB(255, 0, 0) : colorActive.getRGB();
							ColorDialog colorDialog = new ColorDialog(table.getShell());
							colorDialog.setText("Set Series Color");
							colorDialog.setRGB(rgbActive);
							RGB rgbNew = colorDialog.open();
							if(rgbNew != null) {
								Color colorNew = ResourceSupport.getColor(rgbNew);
								for(ISeries<?> series : selectedSeries) {
									SeriesLabelProvider.setColor(series, colorNew);
								}
								seriesListUI.refresh();
							}
						}
					}
				});
			}
		});
	}

	@SuppressWarnings("unchecked")
	private List<ISeries<?>> getSelectedSeries() {

		List<ISeries<?>> selectedSeries = new ArrayList<>();
		Iterator<Object> iterator = seriesListUI.getStructuredSelection().iterator();
		while(iterator.hasNext()) {
			Object object = iterator.next();
			if(object instanceof Series<?>) {
				selectedSeries.add((ISeries<?>)object);
			}
		}
		//
		return selectedSeries;
	}

	private void createEmbeddedLegend() {

		if(scrollableChart != null) {
			BaseChart baseChart = scrollableChart.getBaseChart();
			embeddedLegend = new EmbeddedLegend(baseChart);
			embeddedLegend.setDraw(false);
			updateLegendPosition(false);
			baseChart.getPlotArea().addCustomPaintListener(embeddedLegend);
			/*
			 * Left mouse double-click to get the position to place the legend.
			 */
			baseChart.addCustomPointSelectionHandler(new ICustomSelectionHandler() {

				@Override
				public void handleUserSelection(Event event) {

					if(embeddedLegend.isDraw()) {
						if(capturePosition) {
							updateLegendPosition(event.x, event.y, true);
							updateControls();
							capturePosition = false;
						}
					}
				}
			});
		}
		updateControls();
	}

	private void updateLegendPosition(boolean redraw) {

		if(preferenceStore != null) {
			updateLegendPosition(preferenceStore.getInt(PreferenceConstants.P_LEGEND_POSITION_X), preferenceStore.getInt(PreferenceConstants.P_LEGEND_POSITION_Y), redraw);
		}
	}

	private void updateLegendPosition(int x, int y, boolean redraw) {

		if(embeddedLegend != null) {
			/*
			 * Legend
			 */
			embeddedLegend.setX(x);
			embeddedLegend.setY(y);
			if(preferenceStore != null) {
				preferenceStore.setValue(PreferenceConstants.P_LEGEND_POSITION_X, x);
				preferenceStore.setValue(PreferenceConstants.P_LEGEND_POSITION_Y, y);
			}
			/*
			 * Text
			 */
			textX.setText(Integer.toString(embeddedLegend.getX()));
			textY.setText(Integer.toString(embeddedLegend.getY()));
			/*
			 * Update
			 */
			if(scrollableChart != null && redraw) {
				scrollableChart.redraw();
			}
		}
	}

	private void updateControls() {

		if(embeddedLegend != null) {
			boolean enabled = embeddedLegend.isDraw();
			for(Control control : controls) {
				control.setEnabled(enabled);
			}
		}
	}

	private boolean validate(IValidator validator, ControlDecoration controlDecoration, Text text) {

		IStatus status = validator.validate(text.getText().trim());
		if(status.isOK()) {
			controlDecoration.hide();
			return true;
		} else {
			controlDecoration.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_CONTENT_PROPOSAL).getImage());
			controlDecoration.showHoverText(status.getMessage());
			controlDecoration.show();
			return false;
		}
	}
}
