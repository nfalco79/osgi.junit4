/*
 * Copyright 2017 Nikolas Falco
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.nfalco79.junit4osgi.gui;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import com.github.nfalco79.junit4osgi.gui.ResultTableModel.TestResultValue;

/**
 * Test result cell renderer.
 */
public class ResultCellRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	/**
	 * Renderer method.
	 *
	 * @param table
	 *            the table
	 * @param value
	 *            the value
	 * @param isSelected
	 *            is the cell selected
	 * @param hasFocus
	 *            has the cell the focus
	 * @param row
	 *            the cell row
	 * @param column
	 *            the cell column
	 * @return the resulting component
	 * @see javax.swing.table.DefaultTableCellRenderer#getTableCellRendererComponent(javax.swing.JTable,
	 *      java.lang.Object, boolean, boolean, int, int)
	 */
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
			int row, int column) {
		ResultCellRenderer c = (ResultCellRenderer) super.getTableCellRendererComponent(table, value, isSelected,
				hasFocus, row, column);

		ResultTableModel results = (ResultTableModel) table.getModel();
		switch (TestResultValue.valueOf(c.getText())) {
		case SUCCESS:
			c.setForeground(Color.GREEN);
			c.setToolTipText(results.getToolTip(row, column));
			break;
		case FAILURE:
			c.setForeground(Color.ORANGE);
			c.setToolTipText(results.getToolTip(row, column));
			break;
		case ERROR:
			c.setForeground(Color.RED);
			c.setToolTipText(results.getToolTip(row, column));
			break;
		case SKIPPED:
			c.setForeground(Color.GRAY);
			c.setToolTipText(results.getToolTip(row, column));
			break;
		}

		return c;
	}
}