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
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Web;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.effects.Beam;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.effects.Splash;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.HeroSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.ArrayList;

/**
 * Lets the hero take the form of any monster.
 *
 * While this buff is active the hero uses that monster's combat stats (damage, accuracy)
 * and its on-hit effects, and can use the form's own ability: a magic bolt and its
 * signature debuff for casters, a web for spinners, a minion for necromancers and
 * spawners, a charm for succubi, a death gaze for eyes, a boulder for geomancers, a
 * vampiric bite for bats and ghouls, and the form's melee strike for everything else.
 */
public class Morph extends Buff {

	//how many minions a single hero may keep around, so summoning cannot flood the level
	private static final int MAX_MINIONS = 3;

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
			prototype = Reflection.newInstance( form );
			prototype.sprite = target != null ? target.sprite : null;
		}
		return prototype;
	}

	public String formName(){
		if (form == null) return Messages.get(this, "none");
		return MorphForms.nameOf( form );
	}

	public MorphForms.Form formInfo(){
		return MorphForms.form( form );
	}

	/**
	 * Name of the ability the current form grants, for the UI.
	 */
	public String abilityName(){
		return formInfo().abilityName();
	}

	/**
	 * Description of the ability the current form grants, for the UI.
	 */
	public String abilityDesc(){
		return formInfo().abilityDesc();
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
	 * Uses the current form's ability on a chosen target.
	 */
	public void useAbility(){
		if (!(target instanceof Hero) || form == null){
			GLog.w( Messages.get(Morph.class, "no_form") );
			return;
		}


		final Hero hero = (Hero)target;
		final MorphForms.Form info = formInfo();

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
				//the form can only reach what the hero can actually see:
				// nothing invisible, and nothing hidden behind a wall
				if (ch.invisible > 0 || !Dungeon.level.heroFOV[ch.pos]){
					GLog.w( Messages.get(Morph.class, "no_sight") );
					return;
				}

				switch (info.ability){
					case BOLT:
						hero.busy();
						hero.sprite.turnTo( hero.pos, ch.pos );
						hero.sprite.zap( ch.pos );
						Sample.INSTANCE.play( Assets.Sounds.ZAP );
						damage( hero, ch, info, true, 1f, false );
						hero.spendAndNext( 1f );
						break;
					case GAZE:
						hero.busy();
						hero.sprite.turnTo( hero.pos, ch.pos );
						if (ch.sprite != null){
							hero.sprite.parent.add( new Beam.DeathRay( hero.sprite.center(), ch.sprite.center() ) );
						}
						Sample.INSTANCE.play( Assets.Sounds.ZAP );
						damage( hero, ch, info, true, 1.5f, false );
						hero.spendAndNext( 1f );
						break;
					case WEB:
						hero.sprite.turnTo( hero.pos, ch.pos );
						Blob.seed( ch.pos, 1, Web.class );
						Web.affectChar( ch );
						Sample.INSTANCE.play( Assets.Sounds.TRAP );
						GLog.i( Messages.get(Morph.class, "web_hit") );
						hero.spendAndNext( 1f );
						break;
					case SUMMON:
						summon( hero, info );
						break;
					case CHARM:
						hero.sprite.turnTo( hero.pos, ch.pos );
						Charm charm = Buff.prolong( ch, Charm.class, Charm.DURATION / 2f );
						if (charm != null){
							charm.object = hero.id();
						}
						Sample.INSTANCE.play( Assets.Sounds.CHARMS );
						hero.spendAndNext( 1f );
						break;
					case DRAIN:
						hero.busy();
						hero.sprite.attack( ch.pos, new Callback() {
							@Override
							public void call(){
								damage( hero, ch, info, false, 1f, true );
								hero.spendAndNext( 1f );
							}
						});
						break;
					case ROCK:
						hero.busy();
						hero.sprite.turnTo( hero.pos, ch.pos );
						((MissileSprite)hero.sprite.parent.recycle( MissileSprite.class ))
								.reset( hero.sprite, ch.pos, new GnollGeomancer.Boulder(), new Callback() {
									@Override
									public void call(){
										Splash.at( ch.pos, 0x555555, 15 );
										Sample.INSTANCE.play( Assets.Sounds.ROCKS );
										damage( hero, ch, info, false, 1f, false );
										hero.spendAndNext( 1f );
									}
								});
						break;
					case STRIKE:
					default:
						//the strike is still the hero's, so kills, drops and experience are
						// credited normally, but accuracy, damage and procs come from the form
						hero.busy();
						hero.sprite.attack( ch.pos, new Callback() {
							@Override
							public void call(){
								if (Char.hit( hero, ch, false )){
									ch.damage( Math.max( 1, hero.damageRoll() ), proto() );
								} else if (ch.sprite != null){
									ch.sprite.showStatus( CharSprite.NEUTRAL, ch.defenseVerb() );
								}
								hero.spendAndNext( 1f );
							}
						});
						break;
				}
			}

			@Override
			public String prompt(){
				return Messages.get(Morph.class, "prompt", formName(), info.abilityName());
			}
		});
	}

	private void damage( Hero hero, Char ch, MorphForms.Form info,
						 boolean magic, float damageMulti, boolean lifesteal ){

		if (!Char.hit( hero, ch, magic )){
			if (ch.sprite != null){
				ch.sprite.showStatus( CharSprite.NEUTRAL, ch.defenseVerb() );
			}
			return;
		}

		int dmg = Math.max( 1, Math.round( hero.damageRoll() * damageMulti ) );
		ch.damage( dmg, proto() );

		if (lifesteal && dmg > 0){
			int heal = Math.max( 1, dmg / 2 );
			hero.HP = Math.min( hero.HT, hero.HP + heal );
			if (hero.sprite != null){
				hero.sprite.showStatusWithIcon( CharSprite.POSITIVE, Integer.toString( heal ), FloatingText.HEALING );
			}
		}

		if (info.debuff != null && ch.isAlive()){
			applyDebuff( ch, info.debuff, info.debuffDuration );
			Sample.INSTANCE.play( Assets.Sounds.DEBUFF );
		}
	}

	@SuppressWarnings("unchecked")
	private static void applyDebuff( Char ch, Class<? extends Buff> debuff, float duration ){
		if (debuff == Burning.class){
			Buff.affect( ch, Burning.class ).reignite( ch );
		} else if (debuff == Corrosion.class){
			Buff.affect( ch, Corrosion.class ).set( duration, 1 );
		} else if (FlavourBuff.class.isAssignableFrom( debuff )){
			Buff.prolong( ch, (Class<? extends FlavourBuff>)debuff, duration );
		}
	}

	private void summon( Hero hero, MorphForms.Form info ){

		if (info.minion == null){
			return;
		}

		if (minions() >= MAX_MINIONS){
			GLog.w( Messages.get(Morph.class, "too_many") );
			return;
		}

		int cell = freeCell( hero.pos );
		if (cell == -1){
			GLog.w( Messages.get(Morph.class, "no_space") );
			return;
		}

		Mob minion;
		try {
			minion = Reflection.newInstance( info.minion );
		} catch (Exception e){
			GLog.w( Messages.get(Morph.class, "summon_failed") );
			return;
		}

		minion.alignment = Char.Alignment.ALLY;
		minion.state = minion.HUNTING;
		minion.pos = cell;
		//marks the minion as one of ours, so the summon limit can be counted
		Buff.affect( minion, MorphMinion.class );

		GameScene.add( minion );
		ScrollOfTeleportation.appear( minion, cell );

		GLog.i( Messages.get(Morph.class, "summoned", MorphForms.nameOf( info.minion ) ) );
		hero.spendAndNext( 1f );
	}

	private static int minions(){
		int count = 0;
		for (Mob mob : Dungeon.level.mobs){
			if (mob.buff( MorphMinion.class ) != null){
				count++;
			}
		}
		return count;
	}

	private static int freeCell( int origin ){
		ArrayList<Integer> candidates = new ArrayList<>();
		for (int n : PathFinder.NEIGHBOURS8){
			int cell = origin + n;
			if (!Dungeon.level.insideMap( cell )) continue;
			if (Dungeon.level.solid[cell] || !Dungeon.level.passable[cell]) continue;
			if (Actor.findChar( cell ) != null) continue;
			candidates.add( cell );
		}
		return candidates.isEmpty() ? -1 : Random.element( candidates );
	}

	/**
	 * Marker buff carried by every minion a morph form has called, used to cap their number.
	 */
	public static class MorphMinion extends Buff {
		{
			type = buffType.POSITIVE;
		}

		@Override
		public boolean act(){
			spend( TICK );
			return true;
		}
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
