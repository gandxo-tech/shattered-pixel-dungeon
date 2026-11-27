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
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ui.Component;

import java.util.ArrayList;

public class WndMorph extends Window {

	private static final int WIDTH		= 135;
	private static final int HEIGHT		= 180;
	private static final int BTN_HEIGHT	= 16;
	private static final int GAP		= 2;

	public WndMorph(){

		super();

		resize( WIDTH, HEIGHT );

		ScrollPane pane = new ScrollPane( new Component() );
		add( pane );
		pane.setRect( 0, 0, WIDTH, HEIGHT );

		Component content = pane.content();

		RenderedTextBlock title = PixelScene.renderTextBlock( Messages.get(this, "title"), 9 );
		title.hardlight( TITLE_COLOR );
		title.setPos( 0, 0 );
		content.add( title );

		int top = (int)title.bottom() + 3;

		final Morph morph = Dungeon.hero != null ? Dungeon.hero.buff( Morph.class ) : null;
		final Class<? extends Mob> current = morph == null ? null : morph.form();

		RenderedTextBlock currentForm = PixelScene.renderTextBlock( Messages.get(this, "current",
				morph == null ? Messages.get(this, "none") : morph.formName()), 6 );
		currentForm.setPos( 0, top );
		content.add( currentForm );
		top = (int)currentForm.bottom() + 2;

		if (morph != null && current != null){

			RenderedTextBlock ability = PixelScene.renderTextBlock( Messages.get(this, "ability_of",
					morph.abilityName()), 6 );
			ability.hardlight( 0xFFFF44 );
			ability.setPos( 0, top );
			content.add( ability );
			top = (int)ability.bottom() + 2;

			RenderedTextBlock desc = PixelScene.renderTextBlock( morph.abilityDesc(), 6 );
			desc.maxWidth( WIDTH );
			desc.setPos( 0, top );
			content.add( desc );
			top = (int)desc.bottom() + 4;

			RedButton use = new RedButton( Messages.get(this, "ability") ){
				@Override
				protected void onClick(){
					hide();
					morph.useAbility();
				}
			};
			use.setRect( 0, top, WIDTH, BTN_HEIGHT );
			content.add( use );
			top += BTN_HEIGHT + GAP;

			RedButton revert = new RedButton( Messages.get(this, "revert") ){
				@Override
				protected void onClick(){
					hide();
					morph.detach();
					BuffIndicator.refreshHero();
				}
			};
			revert.setRect( 0, top, WIDTH, BTN_HEIGHT );
			content.add( revert );
			top += BTN_HEIGHT + GAP + 2;
		}

		RenderedTextBlock forms = PixelScene.renderTextBlock( Messages.get(this, "forms"), 7 );
		forms.hardlight( TITLE_COLOR );
		forms.setPos( 0, top );
		content.add( forms );
		top = (int)forms.bottom() + 2;

		for (String category : MorphForms.groups().keySet()){

			RenderedTextBlock header = PixelScene.renderTextBlock( category, 6 );
			header.setPos( 0, top );
			content.add( header );
			top = (int)header.bottom() + 1;

			ArrayList<Class<? extends Mob>> list = MorphForms.groups().get( category );

			for (int i = 0; i < list.size(); i += 2){

				for (int j = 0; j < 2; j++){

					int index = i + j;
					if (index >= list.size()) continue;

					final Class<? extends Mob> form = list.get( index );

					RedButton btn = new RedButton( MorphForms.nameOf( form ) ){
						@Override
						protected void onClick(){
							hide();
							Buff.affect( Dungeon.hero, Morph.class ).setForm( form );
							BuffIndicator.refreshHero();
						}
					};
					btn.setRect( j == 0 ? 0 : WIDTH/2 + 1, top, WIDTH/2 - 1, BTN_HEIGHT );
					if (form == current){
						btn.textColor( 0xFFFF44 );
					}
					content.add( btn );
				}

				top += BTN_HEIGHT + GAP;
			}

			top += 2;
		}

		content.setRect( 0, 0, WIDTH, top );
		pane.setRect( 0, 0, WIDTH, HEIGHT );
	}
}
