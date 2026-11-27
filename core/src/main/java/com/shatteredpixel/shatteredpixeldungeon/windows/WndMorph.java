/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2026 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Morph;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MorphForms;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Image;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.Reflection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets the hero pick a monster to become, and shows what that form looks like and does.
 *
 * The monster itself is drawn above the list, with its health, damage, accuracy, defense
 * and the ability the form grants, so the choice can be made with the eyes open.
 */
public class WndMorph extends Window {

	private static final int WIDTH_P		= 156;
	private static final int HEIGHT_P		= 194;
	private static final int WIDTH_L		= 206;
	private static final int HEIGHT_L		= 130;
	private static final int BTN_HEIGHT		= 16;
	private static final int GAP			= 2;
	private static final int PREVIEW		= 32;
	private static final int FORM_BTN_H		= 13;

	private final int width;
	private final int winHeight;

	private Class<? extends Mob> selected;
	private Component detail;
	private ScrollPane pane;
	private RenderedTextBlock title;

	private final HashMap<Class<? extends Mob>, RedButton> buttons = new HashMap<>();

	public WndMorph(){

		super();

		Morph morph = morph();
		selected = morph != null ? morph.form() : null;

		width = PixelScene.landscape() ? WIDTH_L : WIDTH_P;

		int height = PixelScene.landscape() ? HEIGHT_L : HEIGHT_P;
		if (PixelScene.uiCamera != null){
			height = Math.min( height, (int)PixelScene.uiCamera.height - 12 );
		}
		winHeight = Math.max( 80, height );

		resize( width, winHeight );

		title = PixelScene.renderTextBlock( Messages.get(this, "title"), 9 );
		title.hardlight( TITLE_COLOR );
		title.setPos( 0, 0 );
		add( title );

		detail = buildDetail();
		add( detail );
		detail.setRect( 0, title.bottom() + 2, width, detail.height() );

		float top = detail.bottom() + 2;

		pane = new ScrollPane( new Component() );
		add( pane );
		pane.setRect( 0, top, width, Math.max( 24, winHeight - top ) );

		fillList();
		updateButtonColors();
	}

	private Morph morph(){
		return Dungeon.hero == null ? null : Dungeon.hero.buff( Morph.class );
	}

	/**
	 * Selects a form and redraws the preview, without changing the hero's body yet.
	 */
	private void select( Class<? extends Mob> form ){
		if (selected == form) return;
		selected = form;

		detail.killAndErase();
		detail = buildDetail();
		add( detail );
		detail.setRect( 0, title.bottom() + 2, width, detail.height() );

		float top = detail.bottom() + 2;
		pane.setRect( 0, top, width, Math.max( 24, winHeight - top ) );

		updateButtonColors();
	}

	private void updateButtonColors(){
		Morph morph = morph();
		Class<? extends Mob> current = morph == null ? null : morph.form();

		for (Map.Entry<Class<? extends Mob>, RedButton> entry : buttons.entrySet()){
			if (entry.getKey() == current){
				entry.getValue().textColor( TITLE_COLOR );
			} else if (entry.getKey() == selected){
				entry.getValue().textColor( 0x66CCFF );
			} else {
				entry.getValue().textColor( WHITE );
			}
		}
	}

	private void fillList(){

		Component content = pane.content();
		float top = 0;

		for (String category : MorphForms.groups().keySet()){

			RenderedTextBlock header = PixelScene.renderTextBlock( category, 7 );
			header.hardlight( TITLE_COLOR );
			header.setPos( 0, top );
			content.add( header );
			top = header.bottom() + 1;

			ArrayList<Class<? extends Mob>> list = MorphForms.groups().get( category );

			for (int i = 0; i < list.size(); i += 2){

				for (int j = 0; j < 2; j++){

					int index = i + j;
					if (index >= list.size()) continue;

					final Class<? extends Mob> form = list.get( index );

					RedButton btn = new RedButton( MorphForms.nameOf( form ), 6 ){
						@Override
						protected void onClick(){
							select( form );
						}
					};
					btn.setRect( j == 0 ? 0 : width/2 + 1, top, width/2 - 1, FORM_BTN_H );
					content.add( btn );
					buttons.put( form, btn );
				}

				top += FORM_BTN_H + 1;
			}

			top += 2;
		}

		content.setRect( 0, 0, width, top );
	}

	/**
	 * Builds the preview panel: the monster's sprite, its stats, its ability and the actions.
	 */
	private Component buildDetail(){

		Component panel = new Component();

		final MorphForms.Form info = selected == null ? null : MorphForms.form( selected );
		final MorphForms.Stats stats = info == null ? MorphForms.Stats.EMPTY : info.stats();

		//only the monster's picture is built here: it is a static image taken from a
		// sprite, so nothing in the window is an actor or ever needs a turn
		if (stats.sprite != null){
			try {
				CharSprite sprite = Reflection.newInstance( stats.sprite );
				sprite.idle();
				Image img = new Image( sprite );

				float scale = Math.min( 1f, PREVIEW / Math.max( img.width(), img.height() ) );
				img.scale.set( scale );
				img.x = (PREVIEW - img.width()*scale) / 2f;
				img.y = (PREVIEW - img.height()*scale) / 2f;
				panel.add( img );
			} catch (Exception ignored){ }
		}

		float left = PREVIEW + 2;

		RenderedTextBlock name = PixelScene.renderTextBlock(
				selected == null ? Messages.get(this, "none") : MorphForms.nameOf( selected ), 9 );
		name.hardlight( TITLE_COLOR );
		name.maxWidth( (int)(width - left) );
		name.setPos( left, 0 );
		panel.add( name );

		float textBottom = name.bottom();

		if (selected != null){

			RenderedTextBlock statsBlock = PixelScene.renderTextBlock( statsLine( stats ), 6 );
			statsBlock.maxWidth( (int)(width - left) );
			statsBlock.setPos( left, name.bottom() + 1 );
			panel.add( statsBlock );
			textBottom = statsBlock.bottom();

			float y = Math.max( PREVIEW, textBottom ) + 2;

			RenderedTextBlock ability = PixelScene.renderTextBlock(
					Messages.get(this, "ability_of", info.abilityName()), 6 );
			ability.hardlight( 0xFFFF44 );
			ability.maxWidth( width );
			ability.setPos( 0, y );
			panel.add( ability );

			RenderedTextBlock desc = PixelScene.renderTextBlock( info.abilityDesc(), 6 );
			desc.maxWidth( width );
			desc.setPos( 0, ability.bottom() + 1 );
			panel.add( desc );

			Morph current = morph();
			y = desc.bottom() + 3;
			buildActions( panel, y, current != null && current.form() != null );
			return finish( panel, y + BTN_HEIGHT );
		}

		RenderedTextBlock hint = PixelScene.renderTextBlock( Messages.get(this, "choose_hint"), 6 );
		hint.maxWidth( width );
		hint.setPos( 0, Math.max( PREVIEW, textBottom ) + 2 );
		panel.add( hint );
		return finish( panel, hint.bottom() + 2 );
	}

	private Component finish( Component panel, float height ){
		panel.setRect( 0, 0, width, height );
		return panel;
	}

	private void buildActions( Component panel, float top, boolean hasForm ){

		int count = hasForm ? 3 : 1;
		float btnWidth = (width - GAP*(count - 1)) / count;

		RedButton adopt = new RedButton( Messages.get(this, "adopt"), 7 ){
			@Override
			protected void onClick(){
				if (selected == null || Dungeon.hero == null) return;
				Buff.affect( Dungeon.hero, Morph.class ).setForm( selected );
				BuffIndicator.refreshHero();
				hide();
			}
		};
		adopt.setRect( 0, top, btnWidth, BTN_HEIGHT );
		panel.add( adopt );

		if (!hasForm) return;

		RedButton use = new RedButton( Messages.get(this, "use"), 7 ){
			@Override
			protected void onClick(){
				Morph morph = morph();
				hide();
				if (morph != null) morph.useAbility();
			}
		};
		use.setRect( btnWidth + GAP, top, btnWidth, BTN_HEIGHT );
		panel.add( use );

		RedButton human = new RedButton( Messages.get(this, "human"), 7 ){
			@Override
			protected void onClick(){
				Morph morph = morph();
				hide();
				if (morph != null){
					morph.detach();
					BuffIndicator.refreshHero();
				}
			}
		};
		human.setRect( (btnWidth + GAP)*2, top, btnWidth, BTN_HEIGHT );
		panel.add( human );
	}

	/**
	 * Builds the stat line from the frozen numbers of the form, never from a live monster.
	 */
	private String statsLine( MorphForms.Stats stats ){

		if (stats == null || !stats.known()){
			return Messages.get( this, "stats_unknown" );
		}

		StringBuilder line = new StringBuilder();
		if (stats.hp > 0){
			line.append( Messages.get( this, "stat_hp", stats.hp ) );
		}
		if (stats.dmgMax > 0){
			append( line, Messages.get( this, "stat_atk", stats.dmgMin, stats.dmgMax ) );
		}
		if (stats.acc > 0){
			append( line, Messages.get( this, "stat_acc", stats.acc ) );
		}
		if (stats.def > 0){
			append( line, Messages.get( this, "stat_def", stats.def ) );
		}

		return line.toString();
	}

	private static void append( StringBuilder line, String part ){
		if (line.length() > 0){
			line.append( "  " );
		}
		line.append( part );
	}
}
