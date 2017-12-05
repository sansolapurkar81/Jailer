/*
 * Copyright 2007 - 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.jailer.ui.databrowser.metadata;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import net.sf.jailer.ExecutionContext;
import net.sf.jailer.datamodel.DataModel;
import net.sf.jailer.datamodel.Table;
import net.sf.jailer.modelbuilder.ModelBuilder;
import net.sf.jailer.ui.AutoCompletion;
import net.sf.jailer.ui.JComboBox;
import net.sf.jailer.ui.StringSearchPanel;
import net.sf.jailer.util.Pair;

/**
 * Meta Data UI.
 *
 * @author Ralf Wisser
 */
@SuppressWarnings("serial")
public abstract class MetaDataPanel extends javax.swing.JPanel {

	private final MetaDataSource metaDataSource;
	private final JComboBox<String> tablesComboBox;
	private final DataModel dataModel;
	private final MetaDataDetailsPanel metaDataDetailsPanel;
	private final Frame parent;
	private final JButton searchButton;
	
	private abstract class ExpandingMutableTreeNode extends DefaultMutableTreeNode {
		
		public ExpandingMutableTreeNode() {
			super("loading...");
		}
		
		protected abstract void expandImmediatelly();
		protected abstract void expand();
	}
	
    /**
     * Creates new form MetaDataPanel
     * 
     * @param metaDataSource the meta data source
     * @param dataModel the data mmodel
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public MetaDataPanel(Frame parent, MetaDataSource metaDataSource, MetaDataDetailsPanel metaDataDetailsPanel, final DataModel dataModel, ExecutionContext executionContext) {
    	this.metaDataSource = metaDataSource;
    	this.dataModel = dataModel;
    	this.metaDataDetailsPanel = metaDataDetailsPanel;
    	this.parent = parent;
        initComponents();
        
        hideOutline();

        final ListCellRenderer olRenderer = outlineList.getCellRenderer();
        outlineList.setCellRenderer(new ListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				if (value instanceof Pair) {
					value = outlineTableRender((Pair<MDTable, String>) value);
				}
				return olRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			}
		});
        
        outlineList.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				Object value = outlineList.getSelectedValue();
				if (!inSelectOutlineTable && value instanceof Pair) {
					MDTable mdTable = ((Pair<MDTable, String>) value).a;
					inSelectOutlineTable = true;
					select(mdTable);
					inSelectOutlineTable = false;
				}
			}
		});
        
        tablesComboBox = new JComboBox<String>() {
        	@Override
        	public Dimension getMinimumSize() {
				return new Dimension(40, super.getMinimumSize().height);
        	}
        };
        tablesComboBox.setMaximumRowCount(20);
        AutoCompletion.enable(tablesComboBox);
        
		tablesComboBox.grabFocus();
		
        GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1;
        add(tablesComboBox, gridBagConstraints);
        
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.weightx = 0;
        searchButton = StringSearchPanel.createSearchButton(parent, tablesComboBox, "Select Table", new Runnable() {
			@Override
			public void run() {
				onSelectTable();
			}
		}, new StringSearchPanel.Prepare() {
			@Override
			public void prepare(Set<MDSchema> selectedSchemas) {
				updateTablesCombobox(selectedSchemas);
			}
		}, metaDataSource, dataModel);
		jPanel1.add(searchButton, gridBagConstraints);
        
		tablesComboBox.setVisible(false);
		refreshButton1.setVisible(false);
		searchButton.setText("Select Table");
		
		metaDataTree.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == '\n') {
					if (metaDataTree.getSelectionPath() != null) {
						Object last = metaDataTree.getSelectionPath().getLastPathComponent();
						if (last instanceof DefaultMutableTreeNode) {
							final Object uo = ((DefaultMutableTreeNode) last).getUserObject();
							if (uo instanceof MDTable) {
								openTable((MDTable) uo);
							}
						}
					}
				}
			}
			
			@Override
			public void keyReleased(KeyEvent e) {
			}
			
			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
		
        metaDataTree.addMouseListener(new MouseListener() {
			@Override
			public void mouseReleased(MouseEvent e) {
			}
			@Override
			public void mousePressed(MouseEvent e) {
			}
			@Override
			public void mouseExited(MouseEvent e) {
			}
			@Override
			public void mouseEntered(MouseEvent e) {
			}
			@Override
			public void mouseClicked(MouseEvent evt) {
				final MDTable mdTable = findTable(evt);
                if (evt.getButton() == MouseEvent.BUTTON3) {
	                if (mdTable != null) {
						JPopupMenu popup = new JPopupMenu();
						JMenuItem open = new JMenuItem("Open");
						popup.add(open);
						open.addActionListener(new ActionListener() {
		                    @Override
		                    public void actionPerformed(ActionEvent e) {
		                    	openTable(mdTable);
		                    }
						});
						if (MetaDataPanel.this.metaDataSource.toTable(mdTable) == null) {
							popup.addSeparator();
							JMenuItem analyse = new JMenuItem("Analyse schema \""+ mdTable.getSchema().getUnquotedName() + "\"");
							popup.add(analyse);
							analyse.addActionListener(new ActionListener() {
			                    @Override
			                    public void actionPerformed(ActionEvent e) {
			                    	analyseSchema(mdTable.getSchema().getName());
			                    }
							});
						}
						popup.show(evt.getComponent(), evt.getX(), evt.getY());
	                }
				}
				if (evt.getButton() == MouseEvent.BUTTON1) {
				    if (mdTable != null) {
			            if (evt.getClickCount() > 1) {
		                	openTable(mdTable);
		                }
		            }
				}
			}
			private MDTable findTable(MouseEvent evt) {
				MDTable mdTable = null;
				TreePath node = metaDataTree.getPathForLocation(evt.getX(), evt.getY());
				if (node == null) {
				    for (int x = metaDataTree.getWidth(); x > 0; x -= 32) {
				        node = metaDataTree.getPathForLocation(x, evt.getY());
				        if (node != null) {
				            break;
				        }
				    }
				}
				if (node != null) {
				    Object sel = node.getLastPathComponent();
				    if (sel instanceof DefaultMutableTreeNode) {
				        Object selNode = ((DefaultMutableTreeNode) sel).getUserObject();
				        if (selNode instanceof MDTable) {
				        	mdTable = (MDTable) selNode;
				        	metaDataTree.setSelectionPath(node);
				        }
				    }
				}
				return mdTable;
			}
        });
        
        metaDataTree.addTreeWillExpandListener(new TreeWillExpandListener() {
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
				Object node = event.getPath().getLastPathComponent();
				if (node instanceof DefaultMutableTreeNode) {
					DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
					if (treeNode.getChildCount() == 1 && treeNode.getChildAt(0) instanceof ExpandingMutableTreeNode)
					((ExpandingMutableTreeNode) treeNode.getChildAt(0)).expand();
				}
			}
			
			@Override
			public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
			}
		});
        
        final ImageIcon finalScaledWarnIcon = getScaledIcon(this, warnIcon); 
        final ImageIcon finalScaledViewIcon = getScaledIcon(this, viewIcon); 
        final ImageIcon finalScaledSynonymIcon = getScaledIcon(this, synonymIcon); 
        
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            Map<MDTable, Boolean> dirtyTables = new HashMap<MDTable, Boolean>();
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
					boolean leaf, int row, boolean hasFocus) {
				boolean unknownTable = false;
				boolean isJailerTable = false;
				boolean isView = false;
				boolean isSynonym = false;
				Boolean isDirty = false;
				if (value instanceof DefaultMutableTreeNode) {
					Object uo = ((DefaultMutableTreeNode) value).getUserObject();
					if (uo instanceof MDTable) {
						Table table = MetaDataPanel.this.metaDataSource.toTable((MDTable) uo);
						if (table == null) {
							unknownTable = true;
						} else {
							if (((MDTable) uo).isLoaded()) {
								isDirty = dirtyTables.get((MDTable) uo);
								if (isDirty == null) {
									isDirty = !((MDTable) uo).isUptodate(table);
									dirtyTables.put(((MDTable) uo), isDirty);
								}
							}
						}
						if (ModelBuilder.isJailerTable(((MDTable) uo).getUnquotedName())) {
							isJailerTable = true;
						}
						isView = ((MDTable) uo).isView();
						isSynonym = ((MDTable) uo).isSynonym();
					}
				}
				Component comp = super.getTreeCellRendererComponent(tree, value + (unknownTable? "" : (isDirty? " !" : "  ")), sel, expanded, leaf, row, hasFocus);
				Font font = comp.getFont();
				if (font != null) {
					Font bold = new Font(font.getName(), unknownTable || isDirty? (font.getStyle() | Font.ITALIC) : (font.getStyle() & ~Font.ITALIC), font.getSize());
					comp.setFont(bold);
				}
				if (isJailerTable) {
					comp.setEnabled(false);
				}
				if (isView || isSynonym) {
					JPanel panel = new JPanel(new FlowLayout(0, 0, 0));
					panel.add(comp);
					JLabel label = new JLabel(isView? finalScaledViewIcon : finalScaledSynonymIcon);
					label.setText(" ");
					label.setOpaque(false);
					panel.add(label);
					panel.setOpaque(false);
					comp = panel;
				}
				JPanel panel = new JPanel(new FlowLayout(0, 0, 0));
				panel.add(comp);
				JLabel label = new JLabel("");
				if (unknownTable && !isJailerTable) {
					label.setIcon(finalScaledWarnIcon);
				}
				label.setOpaque(false);
				panel.add(label);
				panel.setOpaque(false);
				comp = panel;
				return comp;
			}
        };
        renderer.setOpenIcon(null);
        renderer.setLeafIcon(null);
        renderer.setClosedIcon(null);
        metaDataTree.setCellRenderer(renderer);
        metaDataTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        
        metaDataTree.addTreeSelectionListener(new TreeSelectionListener() {
			@Override
			public void valueChanged(TreeSelectionEvent e) {
				TreePath path = e.getNewLeadSelectionPath();
				if (path != null) {
					final Object last = path.getLastPathComponent();
					if (metaDataTree.getModel().getRoot() == last) {
//						 searchButton.doClick();
					}
					if (last instanceof DefaultMutableTreeNode) {
						final Object uo = ((DefaultMutableTreeNode) last).getUserObject();
						if (uo instanceof MDTable) {
							selectOutlineTable((MDTable) uo);
				            Table table = MetaDataPanel.this.metaDataSource.toTable((MDTable) uo);
				            if (table != null) {
				            	updateDataModelView(table);
				            }
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									if (metaDataTree.getSelectionPath() != null && metaDataTree.getSelectionPath().getLastPathComponent() == last) {
										if (uo instanceof MDSchema) {
											onSchemaSelect((MDSchema) uo);
										} else if (uo instanceof MDTable) {
											onTableSelect((MDTable) uo);
										}
									}
								}
							});
						}
					}
				}
			}
		});
        
        updateTreeModel(metaDataSource);

        Font font = outlineLabel.getFont();
		if (font != null) {
			Font bold = new Font(font.getName(), font.getStyle() | Font.BOLD, font.getSize());
			outlineLabel.setFont(bold);
		}
    }
    
	private Map<String, MDTable> tablesComboboxMDTablePerName = new HashMap<String, MDTable>();
    
	private void updateTablesCombobox(Set<MDSchema> selectedSchemas) {
		Set<String> tableSet = new HashSet<String>();
		
		for (Table table: dataModel.getTables()) {
			if (metaDataSource.toMDTable(table) == null) {
				String schemaName = table.getSchema("");
				MDSchema schema;
				if (schemaName.isEmpty()) {
					schema = metaDataSource.getDefaultSchema();
				} else {
					schema = metaDataSource.find(schemaName);
				}
				if (schema != null && selectedSchemas.contains(schema)) {
					String displayName = dataModel.getDisplayName(table);
					tableSet.add(displayName);
				}
			}
		}
		for (MDSchema schema: selectedSchemas) {
			if (schema.isLoaded()) {
				for (MDTable table: schema.getTables()) {
					if (!ModelBuilder.isJailerTable(table.getName())) {
						String name;
						if (!schema.isDefaultSchema) {
							name = schema.getName() + "." + table.getName();
						} else {
							name = table.getName();
						}
						tableSet.add(name);
						tablesComboboxMDTablePerName.put(name, table);
					}
				}
			}
		}
		List<String> tables = new ArrayList<String>(tableSet);
		Collections.sort(tables, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				return o1.compareToIgnoreCase(o2);
			}
		});
		ComboBoxModel model = new DefaultComboBoxModel(new Vector(tables));
			
		tablesComboBox.setModel(model);
	}

	protected void openTable(MDTable mdTable) {
		Table table = metaDataSource.toTable(mdTable);
		if (table != null) {
			open(table);
		} else {
			open(mdTable);
		}
	}

	public void reset() {
		refreshButton.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		setOutline(new ArrayList<Pair<MDTable, String>>());
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					MDTable selectedTable = null;
					if (metaDataTree.getSelectionPath() != null) {
						Object last = metaDataTree.getSelectionPath().getLastPathComponent();
						if (last instanceof DefaultMutableTreeNode) {
							final Object uo = ((DefaultMutableTreeNode) last).getUserObject();
							if (uo instanceof MDTable) {
								selectedTable = (MDTable) uo;
							}
						}
					}
					metaDataSource.clear();
					metaDataDetailsPanel.reset();
					updateTreeModel(metaDataSource);
					if (selectedTable != null) {
						MDSchema schema = metaDataSource.find(selectedTable.getSchema().getName());
						if (schema != null) {
							MDTable table = schema.find(selectedTable.getName());
							if (table != null) {
								select(table);
							}
						}
					}
				} finally {
					refreshButton.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				}
			}
		});
	}

	public void select(Table table) {
		select(metaDataSource.toMDTable(table));
	}
	
	public void select(MDTable mdTable) {
		if (mdTable != null) {
			TreePath path = find(metaDataTree.getModel().getRoot(), mdTable);
			if (path != null) {
				selectSchema(mdTable.getSchema(), false);
				metaDataTree.expandPath(path);
				metaDataTree.getSelectionModel().setSelectionPath(path);
				scrollToNode(path);
			} else {
				selectSchema(mdTable.getSchema());
			}
		}
	}

	private void scrollToNode(TreePath path) {
		Rectangle bounds = metaDataTree.getPathBounds(path);
		metaDataTree.scrollRectToVisible(new Rectangle(bounds.x, bounds.y, 1, bounds.height));
	}


	private TreePath find(Object root, MDTable mdTable) {
		if (root instanceof DefaultMutableTreeNode) {
			Object userObject = ((DefaultMutableTreeNode) root).getUserObject();
			if (userObject instanceof MDSchema) {
				if (mdTable.getSchema().equals(userObject)) {
					if (((DefaultMutableTreeNode) root).getChildCount() > 0) {
						TreeNode firstChild = ((DefaultMutableTreeNode) root).getFirstChild();
						if (firstChild instanceof ExpandingMutableTreeNode) {
							((ExpandingMutableTreeNode) firstChild).expandImmediatelly();
						}
					}
				}
			} else if (userObject instanceof MDTable) {
            	if (userObject == mdTable) {
            		return new TreePath(((DefaultMutableTreeNode) root).getPath());
                }
            }
            int cc = ((DefaultMutableTreeNode) root).getChildCount();
            for (int i = 0; i < cc; ++i) {
            	TreePath path = find(((DefaultMutableTreeNode) root).getChildAt(i), mdTable);
            	if (path != null) {
            		return path;
            	}
            }
		}
		return null;
	}
	
	private DefaultMutableTreeNode root;
	private Map<MDSchema, DefaultMutableTreeNode> treeNodePerSchema = new HashMap<MDSchema, DefaultMutableTreeNode>();

	private void updateTreeModel(MetaDataSource metaDataSource) {
		root = new DefaultMutableTreeNode(metaDataSource.dataSourceName);
        for (final MDSchema schema: metaDataSource.getSchemas()) {
        	final DefaultMutableTreeNode schemaChild = new DefaultMutableTreeNode(schema);
			root.add(schemaChild);
			treeNodePerSchema.put(schema, schemaChild);
			MutableTreeNode expandSchema = new ExpandingMutableTreeNode() {
				private boolean expanded = false;
				@Override
				protected void expandImmediatelly() {
					if (!expanded) {
						for (MDTable table: schema.getTables()) {
							DefaultMutableTreeNode tableChild = new DefaultMutableTreeNode(table);
							schemaChild.add(tableChild);
						}
						schemaChild.remove(this);
						TreeModel model = metaDataTree.getModel();
						((DefaultTreeModel) model).nodeStructureChanged(schemaChild);
					}
					expanded = true;
				}
				@Override
				protected void expand() {
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							try {
								setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
								expandImmediatelly();
				            } finally {
				                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
				            }
						}
					});
				}
			};
			schemaChild.add(expandSchema);
        }
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        metaDataTree.setModel(treeModel);
        selectSchema(metaDataSource.getDefaultSchema());
	}

    public void selectSchema(MDSchema mdSchema) {
    	selectSchema(mdSchema, true);
    }

    public void selectSchema(MDSchema mdSchema, boolean scrollToNode) {
    	if (mdSchema != null) {
    		DefaultMutableTreeNode node = treeNodePerSchema.get(mdSchema);
    		if (node != null) {
	        	TreePath path = new TreePath(new Object[] { root, node });
				metaDataTree.expandPath(path);
		        metaDataTree.getSelectionModel().setSelectionPath(path);
		        if (scrollToNode) {
		        	scrollToNode(path);
		        }
    		}
        }
	}

	/**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        metaDataTree = new javax.swing.JTree();
        refreshButton = new javax.swing.JButton();
        refreshButton1 = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        outlineList = new javax.swing.JList();
        outlineLabel = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        jPanel3 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();

        setLayout(new java.awt.GridBagLayout());

        jPanel1.setLayout(new java.awt.GridBagLayout());

        jScrollPane1.setViewportView(metaDataTree);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        jPanel1.add(jScrollPane1, gridBagConstraints);

        refreshButton.setText("Refresh");
        refreshButton.setToolTipText("Refresh Database Meta Data Cache");
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 5;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        jPanel1.add(refreshButton, gridBagConstraints);

        refreshButton1.setText("Select");
        refreshButton1.setToolTipText("Choose the selecetd table in the tables tree");
        refreshButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButton1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 8);
        jPanel1.add(refreshButton1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(jPanel1, gridBagConstraints);

        jPanel2.setLayout(new java.awt.GridBagLayout());

        outlineList.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        outlineList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane2.setViewportView(outlineList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        jPanel2.add(jScrollPane2, gridBagConstraints);

        outlineLabel.setText(" Outline");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
        jPanel2.add(outlineLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        jPanel2.add(jSeparator1, gridBagConstraints);

        jPanel3.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        jPanel3.add(jLabel1, gridBagConstraints);

        jLabel2.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        jPanel3.add(jLabel2, gridBagConstraints);

        jLabel3.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        jPanel3.add(jLabel3, gridBagConstraints);

        jLabel4.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        jPanel3.add(jLabel4, gridBagConstraints);

        jLabel5.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        jPanel3.add(jLabel5, gridBagConstraints);

        jLabel6.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        jPanel3.add(jLabel6, gridBagConstraints);

        jLabel7.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        jPanel3.add(jLabel7, gridBagConstraints);

        jLabel8.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        jPanel3.add(jLabel8, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 2;
        jPanel2.add(jPanel3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
        add(jPanel2, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
    	reset();
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void refreshButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButton1ActionPerformed
		onSelectTable();
    }//GEN-LAST:event_refreshButton1ActionPerformed

	private List<Pair<MDTable, String>> outlineTables = new ArrayList<Pair<MDTable, String>>();
	private boolean inSelectOutlineTable = false;
	
    private void showOutline() {
    	jPanel2.setVisible(true);
    	SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
		    	TreePath path = metaDataTree.getSelectionPath();
		    	if (path != null) {
					Rectangle bounds = metaDataTree.getPathBounds(path);
					metaDataTree.scrollRectToVisible(new Rectangle(bounds.x, bounds.y, 1, bounds.height));
		    	}
			}
		});
	}

	private void hideOutline() {
    	jPanel2.setVisible(false);
	}

	private String outlineTableRender(Pair<MDTable, String> mdTable) {
		String render;
		if (mdTable.a.getSchema().isDefaultSchema) {
			render = mdTable.a.getName();
		} else {
			render = mdTable.a.getSchema().getName() + "." + mdTable.a.getName();
		}
		String alias = mdTable.b;
		if (alias != null) {
			return "<html>" + render + " <font color=\"#0000ff\">as</font> " + alias;
		}
		return render;
	}
	
	public void setOutline(List<Pair<MDTable, String>> outlineTables) {
		this.outlineTables = new ArrayList<Pair<MDTable, String>>(outlineTables);
		DefaultListModel model = new DefaultListModel();
		for (Pair<MDTable, String> mdTable: outlineTables) {
			model.addElement(mdTable);
		}
		outlineList.setModel(model);
		if (outlineTables.isEmpty()) {
			hideOutline();
		} else {
			showOutline();
		}
	}

	private void selectOutlineTable(MDTable mdTable) {
		if (!inSelectOutlineTable) {
			inSelectOutlineTable = true;
			boolean found = false;
			for (Pair<MDTable, String> value: outlineTables) {
				if (mdTable.equals(value.a)) {
					outlineList.setSelectedValue(value, true);
					found = true;
					break;
				}
			}
			if (!found) {
				outlineList.clearSelection();
			}
			inSelectOutlineTable = false;
		}
	}


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JTree metaDataTree;
    private javax.swing.JLabel outlineLabel;
    private javax.swing.JList outlineList;
    private javax.swing.JButton refreshButton;
    private javax.swing.JButton refreshButton1;
    // End of variables declaration//GEN-END:variables

    protected abstract void open(Table table);
    protected abstract void open(MDTable mdTable);
    protected abstract void analyseSchema(String schemaName);
    protected abstract void onTableSelect(MDTable mdTable);
    protected abstract void onSchemaSelect(MDSchema mdSchema);
	protected abstract void openNewTableBrowser();
	protected abstract void updateDataModelView(Table table);

    public void onSelectTable() {
		Object item = tablesComboBox.getSelectedItem();
		if (item != null) {
			Table table = dataModel.getTableByDisplayName(item.toString());
			if (table != null) {
				MDTable mdTable = metaDataSource.toMDTable(table);
				if (mdTable != null) {
					select(mdTable);
				} else {
					JOptionPane.showMessageDialog(parent, "Table \"" + dataModel.getDisplayName(table) + "\" does not exist in the database");
				}
			} else {
				MDTable mdTable = tablesComboboxMDTablePerName.get(item);
				if (mdTable != null) {
					select(mdTable);
				}
			}
		}
	}

	static ImageIcon warnIcon;
	static ImageIcon viewIcon;
	static ImageIcon synonymIcon;
	
    static ImageIcon getScaledIcon(JComponent component, ImageIcon icon) {
    	if (icon != null) {
            ImageIcon scaledIcon = icon;
            if (scaledIcon != null) {
            	int heigth = component.getFontMetrics(new JLabel("M").getFont()).getHeight();
            	double s = heigth / (double) scaledIcon.getIconHeight();
            	if (icon == viewIcon) {
            		s *= 0.8;
            	}
            	try {
            		return new ImageIcon(scaledIcon.getImage().getScaledInstance((int)(scaledIcon.getIconWidth() * s), (int)(scaledIcon.getIconHeight() * s), Image.SCALE_SMOOTH));
            	} catch (Exception e) {
            		return null;
            	}
            }
    	}
    	return null;
    }
    static {
		String dir = "/net/sf/jailer/ui/resource";
		
		// load images
		try {
			warnIcon = new ImageIcon(MetaDataPanel.class.getResource(dir + "/wanr.png"));
			viewIcon = new ImageIcon(MetaDataPanel.class.getResource(dir + "/view.png"));
			synonymIcon = new ImageIcon(MetaDataPanel.class.getResource(dir + "/right.png"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
