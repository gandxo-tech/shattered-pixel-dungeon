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

package com.shatteredpixel.shatteredpixeldungeon.actors.buffs;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;
import com.watabou.utils.Reflection;

/**
 * Lets the hero take the form of any monster.
 *
 * While this buff is active the hero uses that monster's combat stats (damage, accuracy)
 * and its on-hit effects, and can fire the monster's attack at any visible target,
 * as many times as they like.
 */
public class Morph extends Buff {

	private Class<? extends Mob> form;
	//not serialized, rebuilt on demand, used to borrow the monster's combat logic
	private Mob prototype;

	{
		type = buffType.POSITIVE;
		announced = true;
	}

	public Class<? extends Mob> form(){
		return form;
	}

	public void setForm( Class<? extends Mob> mobClass ){
		form = mobClass;
		prototype = null;
		if (target != null && target.sprite != null){
			fx( false );
			fx( true );
		}
	}

	public Mob proto(){
		if (form == null) return null;
		if (prototype == null){
			prototype = Reflection.newInstance(form);
			prototype.sprite = target != null ? target.sprite : null;
		}
		return prototype;
	}

	public String formName(){
		if (form == null) return Messages.get(this, "none");
		return Messages.titleCase(Reflection.newInstance(form).name());
	}

	@Override
	public int icon(){
		return BuffIndicator.DISGUISE;
	}

	@Override
	public void fx( boolean on ){
		if (!(target instanceof Hero) || !(target.sprite instanceof HeroSprite)){
			return;
		}
		if (on && form != null){
			((HeroSprite)target.sprite).morph( proto() );
		} else {
			((HeroSprite)target.sprite).unmorph();
		}
		GameScene.updateAvatar();
	}

	@Override
	public boolean act(){
		spend( TICK );
		return true;
	}

	/**
	 * Applies the current form's on-hit effect, as if the monster itself had landed the blow.
	 */
	public int onHit( Char enemy, int damage ){
		if (!(target instanceof Hero) || form == null){
			return damage;
		}

		Mob mob = proto();
		//fighting from the hero's tile, so that any effect the monster applies to itself
		// (aggro on being hit, an area effect, and so on) lands where the hero stands
		mob.pos = target.pos;
		mob.sprite = target.sprite;

		return mob.morphAttackProc( enemy, damage );
	}

	/**
	 * Uses the current form's attack on a chosen target, at any range and without cooldown.
	 */
	public void useAbility(){
		if (!(target instanceof Hero) || form == null){
			return;
		}

		final Hero hero = (Hero)target;

		GameScene.selectCell(new CellSelector.Listener() {
			@Override
			public void onSelect( Integer cell ){
				if (cell == null) return;

				Char ch = Actor.findChar(cell);
				if (ch == null || ch == hero){
					GLog.w( Messages.get(Morph.class, "no_target") );
					return;
				}
				if (ch.alignment == hero.alignment){
					GLog.w( Messages.get(Morph.class, "ally_target") );
					return;
				}

				//the form can only strike what the hero can actually see:
				// nothing invisible, and nothing hidden behind a wall
				if (ch.invisible > 0 || !Dungeon.level.heroFOV[ch.pos]){
					GLog.w( Messages.get(Morph.class, "no_sight") );
					return;
				}

				Sample.INSTANCE.play( Assets.Sounds.ZAP );

				//the strike is still the hero's, so kills, drops and experience are credited normally,
				// but accuracy, damage and on-hit effects come from the form
				hero.busy();
				hero.sprite.attack( ch.pos, new Callback() {
					@Override
					public void call(){
						hero.attack( ch );
						hero.spendAndNext( 1f );
					}
				});
			}

			@Override
			public String prompt(){
				return Messages.get(Morph.class, "prompt");
			}
		});
	}

	private static final String FORM = "form";

	@Override
	public void storeInBundle( Bundle bundle ){
		super.storeInBundle( bundle );
		bundle.put( FORM, form == null ? "" : form.getName() );
	}

	@SuppressWarnings("unchecked")
	@Override
	public void restoreFromBundle( Bundle bundle ){
		super.restoreFromBundle( bundle );
		String name = bundle.getString( FORM );
		if (name == null || name.isEmpty()){
			form = null;
		} else {
			form = (Class<? extends Mob>)Reflection.forName( name );
		}
		prototype = null;
	}
}
