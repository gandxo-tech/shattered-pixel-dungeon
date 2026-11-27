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

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Acidic;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Albino;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalGuardian;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalSpire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalWisp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DemonSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalCore;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalSentry;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalSpinner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Pylon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.RipperDemon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.RotHeart;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.RotLasher;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.SpectralNecromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Spinner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Succubus;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Wraith;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.watabou.utils.Reflection;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Catalogue of every monster the hero can turn into with the Morph buff,
 * and the usable ability each of those forms grants.
 *
 * Monsters are taken from the bestiary categories, so the catalogue stays
 * in sync with the game. Opening the window never instantiates a monster:
 * display names come from the message bundles and are cached.
 */
public final class MorphForms {

	private MorphForms(){}

	public enum Ability {
		//the form's own melee attack, usable on any visible target
		STRIKE,
		//a magic bolt, optionally applying the form's signature debuff
		BOLT,
		//the eye's piercing death gaze
		GAZE,
		//a web thrown at the target
		WEB,
		//call the form's minion to fight for the hero
		SUMMON,
		//charm the target
		CHARM,
		//vampiric bite, healing the hero for part of the damage
		DRAIN,
		//throw a boulder
		ROCK
	}

	public static class Form {

		public final Class<? extends Mob> mob;
		public final Ability ability;
		public final Class<? extends Buff> debuff;
		public final float debuffDuration;
		public final Class<? extends Mob> minion;

		Form( Class<? extends Mob> mob, Ability ability,
			  Class<? extends Buff> debuff, float debuffDuration,
			  Class<? extends Mob> minion ){
			this.mob = mob;
			this.ability = ability;
			this.debuff = debuff;
			this.debuffDuration = debuffDuration;
			this.minion = minion;
		}

		public String abilityName(){
			return Messages.get( Morph.class, "ability." + ability.name().toLowerCase() );
		}

		public String abilityDesc(){
			String desc = Messages.get( Morph.class, "ability_desc." + ability.name().toLowerCase() );
			if (debuff != null){
				desc += "\n\n" + Messages.get( Morph.class, "debuff",
						Messages.titleCase( Messages.get( debuff, "name" ) ) );
			}
			if (minion != null){
				desc += "\n\n" + Messages.get( Morph.class, "minion", nameOf( minion ) );
			}
			return desc;
		}
	}

	private static final Form STRIKE = new Form( null, Ability.STRIKE, null, 0f, null );

	private static final LinkedHashMap<String, ArrayList<Class<? extends Mob>>> GROUPS = new LinkedHashMap<>();
	private static final HashMap<Class<? extends Mob>, Form> FORMS = new HashMap<>();
	private static final HashMap<Class<?>, String> NAMES = new HashMap<>();

	/**
	 * All morphable monsters, keyed by the localized category title.
	 */
	public static LinkedHashMap<String, ArrayList<Class<? extends Mob>>> groups(){
		build();
		return GROUPS;
	}

	/**
	 * Localized name of a monster, without creating one. Results are cached, so this
	 * is cheap enough to call from the UI.
	 */
	public static String nameOf( Class<? extends Mob> cls ){
		String cached = NAMES.get( cls );
		if (cached != null) return cached;

		String name = null;
		try {
			//a few monsters (the mimics) report a name which depends on their state,
			// so those do need an instance. Everything else reads straight from the bundle.
			if (cls.getMethod( "name" ).getDeclaringClass() != Char.class){
				name = Reflection.newInstance( cls ).name();
			} else {
				name = Messages.get( cls, "name" );
				//variants without their own entry inherit their parent's name, which would
				// make several different forms look identical in the list
				Class<?> parent = cls.getSuperclass();
				if (parent != null && Mob.class.isAssignableFrom( parent )
						&& name.equals( Messages.get( parent, "name" ) )){
					name = name + " (" + cls.getSimpleName() + ")";
				}
			}
		} catch (Exception ignored){ }

		if (name == null || name.contains( Messages.NO_TEXT_FOUND )){
			name = cls.getSimpleName();
		}

		name = Messages.titleCase( name );
		NAMES.put( cls, name );
		return name;
	}

	public static Form form( Class<? extends Mob> cls ){
		if (cls == null) return STRIKE;

		Form f = FORMS.get( cls );
		if (f == null){
			f = buildForm( cls );
			FORMS.put( cls, f );
		}
		return f;
	}

	private static void build(){
		if (!GROUPS.isEmpty()) return;

		for (Bestiary category : new Bestiary[]{
				Bestiary.REGIONAL, Bestiary.BOSSES, Bestiary.UNIVERSAL,
				Bestiary.RARE, Bestiary.QUEST, Bestiary.NEUTRAL}){
			ArrayList<Class<? extends Mob>> list = new ArrayList<>();
			for (Class<?> cls : category.entities()){
				add( list, cls );
			}
			if (!list.isEmpty()){
				GROUPS.put( category.title(), list );
			}
		}

		//a few monsters which the bestiary does not list
		ArrayList<Class<? extends Mob>> extra = new ArrayList<>();
		add( extra, FungalCore.class );
		add( extra, FungalSentry.class );
		add( extra, FungalSpinner.class );
		if (!extra.isEmpty()){
			GROUPS.put( Messages.get( Morph.class, "group.other" ), extra );
		}
	}

	@SuppressWarnings("unchecked")
	private static void add( ArrayList<Class<? extends Mob>> list, Class<?> cls ){
		if (cls == null || !Mob.class.isAssignableFrom( cls )) return;
		if (Modifier.isAbstract( cls.getModifiers() )) return;
		if (!list.contains( cls )) list.add( (Class<? extends Mob>)cls );
	}

	private static Form buildForm( Class<? extends Mob> cls ){

		if (Eye.class.isAssignableFrom( cls ))              return of( cls, Ability.GAZE );
		if (Spinner.class.isAssignableFrom( cls ))          return of( cls, Ability.WEB );
		if (Succubus.class.isAssignableFrom( cls ))         return of( cls, Ability.CHARM );
		if (GnollGeomancer.class.isAssignableFrom( cls ))   return of( cls, Ability.ROCK );

		if (Bat.class.isAssignableFrom( cls )
				|| Albino.class.isAssignableFrom( cls )
				|| Ghoul.class.isAssignableFrom( cls ))      return of( cls, Ability.DRAIN );

		if (SpectralNecromancer.class.isAssignableFrom( cls ))
			return new Form( cls, Ability.SUMMON, null, 0f, Wraith.class );
		if (Necromancer.class.isAssignableFrom( cls ))
			return new Form( cls, Ability.SUMMON, null, 0f, Necromancer.NecroSkeleton.class );
		if (DemonSpawner.class.isAssignableFrom( cls ))
			return new Form( cls, Ability.SUMMON, null, 0f, RipperDemon.class );
		if (RotHeart.class.isAssignableFrom( cls ))
			return new Form( cls, Ability.SUMMON, null, 0f, RotLasher.class );
		if (FungalCore.class.isAssignableFrom( cls ))
			return new Form( cls, Ability.SUMMON, null, 0f, FungalSentry.class );

		if (isCaster( cls )){
			return new Form( cls, Ability.BOLT, boltDebuff( cls ), 4f, null );
		}

		return of( cls, Ability.STRIKE );
	}

	private static Form of( Class<? extends Mob> cls, Ability ability ){
		return new Form( cls, ability, null, 0f, null );
	}

	private static boolean isCaster( Class<? extends Mob> cls ){
		return Shaman.class.isAssignableFrom( cls )
				|| Warlock.class.isAssignableFrom( cls )
				|| DM100.class.isAssignableFrom( cls )
				|| DM300.class.isAssignableFrom( cls )
				|| CrystalWisp.class.isAssignableFrom( cls )
				|| CrystalGuardian.class.isAssignableFrom( cls )
				|| CrystalSpire.class.isAssignableFrom( cls )
				|| Pylon.class.isAssignableFrom( cls )
				|| Elemental.class.isAssignableFrom( cls )
				|| Scorpio.class.isAssignableFrom( cls )
				|| Acidic.class.isAssignableFrom( cls )
				|| FungalSentry.class.isAssignableFrom( cls );
	}

	private static Class<? extends Buff> boltDebuff( Class<? extends Mob> cls ){

		if (cls == Shaman.RedShaman.class)      return Weakness.class;
		if (cls == Shaman.BlueShaman.class)     return Vulnerable.class;
		if (cls == Shaman.PurpleShaman.class)   return Hex.class;
		if (Shaman.class.isAssignableFrom( cls )) return Weakness.class;

		if (Warlock.class.isAssignableFrom( cls )) return Degrade.class;

		if (cls == Elemental.FireElemental.class)  return Burning.class;
		if (cls == Elemental.FrostElemental.class) return Chill.class;
		if (cls == Elemental.ShockElemental.class) return Paralysis.class;
		if (cls == Elemental.ChaosElemental.class) return Vertigo.class;
		if (Elemental.class.isAssignableFrom( cls )) return Burning.class;

		if (Acidic.class.isAssignableFrom( cls ))  return Corrosion.class;
		if (Scorpio.class.isAssignableFrom( cls )) return Cripple.class;

		if (FungalSentry.class.isAssignableFrom( cls )) return Vertigo.class;
		if (DM300.class.isAssignableFrom( cls ))        return Paralysis.class;

		//plain bolts: DM-100, crystal mobs, pylons
		return null;
	}
}
