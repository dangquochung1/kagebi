package com.kagebi.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/**
 * A strip of tabs over a stack of pages.
 *
 * <p>scene2d has no tab widget. A {@link ButtonGroup} with a minimum check
 * count of one makes "exactly one tab is selected" structural rather than
 * something every call site has to maintain.
 *
 * <p>Pages are shown and hidden rather than added and removed, so each keeps
 * its own scroll position and widget state when the player tabs away and back.
 */
public final class TabBar extends Table {

    private final Skin skin;
    private final ButtonGroup<TextButton> group = new ButtonGroup<>();
    private final Table strip = new Table();
    private final Stack pages = new Stack();

    public TabBar(Skin skin) {
        this.skin = skin;
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);
        group.setUncheckLast(true);

        top();
        add(strip).left().padBottom(2).row();
        add(pages).grow();
    }

    /** Selects a tab by index, clamped to the tabs that exist. */
    public void select(int index) {
        if (group.getButtons().size == 0) {
            return;
        }
        int i = Math.max(0, Math.min(index, group.getButtons().size - 1));
        group.getButtons().get(i).setChecked(true);
        for (int p = 0; p < pages.getChildren().size; p++) {
            pages.getChildren().get(p).setVisible(p == i);
        }
    }

    public void addTab(String title, Actor page) {
        final TextButton button = new TextButton(title, skin, "tab");
        group.add(button);
        strip.add(button).padRight(1);

        page.setVisible(pages.getChildren().size == 0);
        pages.add(page);

        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int index = group.getButtons().indexOf(button, true);
                for (int i = 0; i < pages.getChildren().size; i++) {
                    pages.getChildren().get(i).setVisible(i == index);
                }
            }
        });
    }
}
