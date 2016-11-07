/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.find;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.intellij.MacIntelliJIconCache;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.InplaceActionButtonLook;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class SearchTextArea extends NonOpaquePanel implements PropertyChangeListener, FocusListener {
  private final JTextArea myTextArea;
  private final boolean myInfoMode;
  private final JLabel myInfoLabel;
  private JPanel myIconsPanel = null;
  private ActionButton myNewLineButton;
  private ActionButton myClearButton;
  private JBScrollPane myScrollPane;

  public SearchTextArea(boolean search) {
    this(new JTextArea(), search, false);
  }

  public SearchTextArea(@NotNull JTextArea textArea, boolean search, boolean infoMode) {
    myTextArea = textArea;
    myInfoMode = infoMode;
    myTextArea.addPropertyChangeListener("background", this);
    myTextArea.addFocusListener(this);
    myTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateIconsLayout();
      }
    });
    myTextArea.setBorder(null);
    myTextArea.setOpaque(false);
    myScrollPane = new JBScrollPane(myTextArea,
                                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
      @Override
      public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.min(d.height, myTextArea.getUI().getPreferredSize(myTextArea).height);
        return d;
      }
    };
    myScrollPane.getVerticalScrollBar().setBackground(UIUtil.TRANSPARENT_COLOR);
    myScrollPane.getViewport().setBorder(null);
    myScrollPane.getViewport().setOpaque(false);
    myScrollPane.setBorder(JBUI.Borders.emptyRight(2));
    myScrollPane.setOpaque(false);

    myInfoLabel = new JBLabel(UIUtil.ComponentStyle.SMALL);
    myInfoLabel.setForeground(JBColor.GRAY);

    setBorder(JBUI.Borders.empty(7, 6, 3, 6));
    setLayout(new MigLayout("flowx, ins 0 1 0 0, gapx " + JBUI.scale(4)));
    add(createButton(new ShowHistoryAction(search)), "ay top");
    add(myScrollPane, "growx, pushx, shrinky, gaptop " + JBUI.scale(1));
    if (myInfoMode) {
      add(myInfoLabel, "ay top, gaptop " + JBUI.scale(2));
    }
    else {
      myClearButton = createButton(new ClearAction());
      myNewLineButton = createButton(new NewLineAction());
      myIconsPanel = new NonOpaquePanel();
      add(myIconsPanel, "ay top, gaptop " + JBUI.scale(0));
      updateIconsLayout();
    }
  }

  protected boolean isNewLineAvailable() {
    return Registry.is("ide.find.show.add.newline.hint");
  }

  private void updateIconsLayout() {
    if (myIconsPanel == null) {
      return;
    }

    boolean showClearIcon = !StringUtil.isEmpty(myTextArea.getText());
    boolean showNewLine = isNewLineAvailable();
    boolean wrongVisibility =
      ((myClearButton.getParent() != null) != showClearIcon) || ((myNewLineButton.getParent() != null) != showNewLine);

    LayoutManager layout = myIconsPanel.getLayout();
    boolean wrongLayout = !(layout instanceof GridLayout);
    boolean multiline = StringUtil.getLineBreakCount(myTextArea.getText()) > 0;
    boolean wrongPositioning = !wrongLayout && (((GridLayout)layout).getRows() > 1) != multiline;
    if (wrongLayout || wrongVisibility || wrongPositioning) {
      myIconsPanel.removeAll();
      int rows = multiline && showClearIcon && showNewLine ? 2 : 1;
      int columns = !multiline && showClearIcon && showNewLine ? 2 : 1;
      myIconsPanel.setLayout(new GridLayout(rows, columns, 8, 8));
      if (!multiline && showNewLine) {
        myIconsPanel.add(myNewLineButton);
      }
      if (showClearIcon) {
        myIconsPanel.add(myClearButton);
      }
      if (multiline && showNewLine) {
        myIconsPanel.add(myNewLineButton);
      }
      myScrollPane.revalidate();
      doLayout();
    }
  }


  @NotNull
  public JTextArea getTextArea() {
    return myTextArea;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();//myToWrap.getPreferredScrollableViewportSize();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    repaint();
  }

  @Override
  public void focusGained(FocusEvent e) {
    repaint();
  }

  @Override
  public void focusLost(FocusEvent e) {
    repaint();
  }

  public void setInfoText(String info) {
    myInfoLabel.setText(info);
  }

  private static Color enabledBorderColor = new JBColor(Gray._196, Gray._100);
  private static Color disabledBorderColor = Gray._83;

  @Override
  public void paint(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      Rectangle r = new Rectangle(getSize());
      if (r.height % 2 == 1) r.height--;
      int arcSize = Math.min(25, r.height-1);
      boolean hasFocus = myTextArea.hasFocus();
      Color borderColor = myTextArea.isEnabled() ? enabledBorderColor : disabledBorderColor;
      if (SystemInfo.isMac && (UIUtil.isUnderIntelliJLaF() || UIUtil.isUnderAquaLookAndFeel())) {
        g.setColor(borderColor);
        MacIntelliJTextFieldUI.paintAquaSearchFocusRing(g, r, myTextArea);
      }
      else {
        JBInsets.removeFrom(r, new JBInsets(3, 3, 3, 3));
        if (hasFocus && (UIUtil.isUnderIntelliJLaF() || UIUtil.isUnderDarcula())) {
          DarculaUIUtil.paintSearchFocusRing(g, r, myTextArea, arcSize+6);
        }
        else {
          Shape shape = UIUtil.isUnderWindowsLookAndFeel()
                        ? new Rectangle(r.x, r.y, r.width, r.height)
                        : new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, arcSize, arcSize);
          g.setColor(borderColor);
          g.setColor(myTextArea.getBackground());
          g.fill(shape);
          g.setColor(borderColor);
          g.draw(shape);
        }
      }
    }
    finally {
      g.dispose();
    }
    super.paint(graphics);
  }

  private class ShowHistoryAction extends DumbAwareAction {
    private final boolean myShowSearchHistory;

    public ShowHistoryAction(boolean search) {
      super((search ? "Search" : "Replace") + " History",
            (search ? "Search" : "Replace") + " history",
            MacIntelliJIconCache.getIcon("searchFieldWithHistory"));

      myShowSearchHistory = search;

      KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK);
      registerCustomShortcutSet(new CustomShortcutSet(new KeyboardShortcut(stroke, null)), myTextArea);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("find.recent.search");
      FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(e.getProject());
      String[] recent = myShowSearchHistory ? findInProjectSettings.getRecentFindStrings()
                                            : findInProjectSettings.getRecentReplaceStrings();
      String title = "Recent " + (myShowSearchHistory ? "Searches" : "Replaces");
      JBList historyList = new JBList((Object[])ArrayUtil.reverseArray(recent));
      Utils.showCompletionPopup(SearchTextArea.this, historyList, title, myTextArea, null);
    }
  }

  private static ActionButton createButton(AnAction action) {
    Presentation presentation = action.getTemplatePresentation();
    Dimension d = new JBDimension(16, 16);
    ActionButton button = new ActionButton(action, presentation, ActionPlaces.UNKNOWN, d) {
      @Override
      protected DataContext getDataContext() {
        return DataManager.getInstance().getDataContext(this);
      }
    };
    button.setLook(new InplaceActionButtonLook());
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return button;
  }

  private class ClearAction extends DumbAwareAction {
    public ClearAction() {
      super(null, null, AllIcons.Actions.Clear);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myTextArea.setText("");
    }
  }

  private class NewLineAction extends DumbAwareAction {
    public NewLineAction() {
      super(null, "New line (" + KeymapUtil.getKeystrokeText(SearchReplaceComponent.NEW_LINE_KEYSTROKE) + ")", AllIcons.Actions.SearchNewLine);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      new DefaultEditorKit.InsertBreakAction().actionPerformed(new ActionEvent(myTextArea, 0, "action"));
    }
  }
}
