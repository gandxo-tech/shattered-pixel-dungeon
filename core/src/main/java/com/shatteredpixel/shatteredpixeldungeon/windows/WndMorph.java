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
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.Reflection;

public class WndMorph extends Window {

	private static final int WIDTH		= 130;
	private static final int HEIGHT		= 160;
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

		RenderedTextBlock current = PixelScene.renderTextBlock( Messages.get(this, "current",
				morph == null ? Messages.get(this, "none") : morph.formName()), 6 );
		current.setPos( 0, top );
		content.add( current );
		top = (int)current.bottom() + 4;

		if (morph != null){

			RedButton ability = new RedButton( Messages.get(this, "ability") ){
				@Override
				protected void onClick(){
					hide();
					morph.useAbility();
				}
			};
			ability.setRect( 0, top, WIDTH, BTN_HEIGHT );
			content.add( ability );
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

		for (Bestiary category : new Bestiary[]{
				Bestiary.REGIONAL, Bestiary.BOSSES, Bestiary.UNIVERSAL, Bestiary.RARE, Bestiary.QUEST}){
			for (Class<?> cls : category.entities()){
				if (!Mob.class.isAssignableFrom(cls)) continue;

				final Class<? extends Mob> form = (Class<? extends Mob>)cls;

				String name;
				try {
					name = Messages.titleCase( Reflection.newInstance(form).name() );
				} catch (Exception e){
					continue;
				}

				RedButton btn = new RedButton( name ){
					@Override
					protected void onClick(){
						hide();
						Morph buff = Buff.affect( Dungeon.hero, Morph.class );
						buff.setForm( form );
						BuffIndicator.refreshHero();
					}
				};
				btn.setRect( 0, top, WIDTH, BTN_HEIGHT );
				content.add( btn );
				top += BTN_HEIGHT + GAP;
			}
		}

		content.setRect( 0, 0, WIDTH, top );
		pane.setRect( 0, 0, WIDTH, HEIGHT );
	}
}
