/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import static haven.Inventory.sqlite;
import static haven.Inventory.sqlo;

public class Equipory extends Widget {
    public static final Box[] boxen = {
	new Box(new Coord(250,   0), Resource.loadtex("gfx/hud/inv/head"),   0),
	new Box(new Coord( 50,  70), Resource.loadtex("gfx/hud/inv/face"),   0),
	new Box(new Coord(250,  70), Resource.loadtex("gfx/hud/inv/shirt"),  0),
	new Box(new Coord(300,  70), Resource.loadtex("gfx/hud/inv/torsoa"), 0),
	new Box(new Coord( 50,   0), Resource.loadtex("gfx/hud/inv/keys"),   0),
	new Box(new Coord( 50, 210), Resource.loadtex("gfx/hud/inv/belt"),   0),
	new Box(new Coord( 25, 140), Resource.loadtex("gfx/hud/inv/lhande"), 0),
	new Box(new Coord(275, 140), Resource.loadtex("gfx/hud/inv/rhande"), 0),
	null,
	new Box(new Coord(  0,   0), Resource.loadtex("gfx/hud/inv/wallet"), 0),
	new Box(new Coord(  0, 210), Resource.loadtex("gfx/hud/inv/coat"),   0),
	new Box(new Coord(300,   0), Resource.loadtex("gfx/hud/inv/cape"),   0),
	new Box(new Coord(300, 210), Resource.loadtex("gfx/hud/inv/pants"),  0),
	new Box(new Coord(100,   0), null, 0),
	new Box(new Coord(  0,  70), Resource.loadtex("gfx/hud/inv/back"),   0),
	new Box(new Coord(250, 210), Resource.loadtex("gfx/hud/inv/feet"),   0),

	new Box(new Coord(250,   0), Resource.loadtex("gfx/hud/inv/costumehead"),   1),
	new Box(new Coord(50,   70), Resource.loadtex("gfx/hud/inv/costumeface"),   1),
	new Box(new Coord(250,  70), Resource.loadtex("gfx/hud/inv/costumeshirt"),  1),
	new Box(new Coord(300,  70), Resource.loadtex("gfx/hud/inv/costumetorsoa"), 1),
 	new Box(new Coord(0,   210), Resource.loadtex("gfx/hud/inv/costumecoat"),   1),
	new Box(new Coord(300,   0), Resource.loadtex("gfx/hud/inv/costumecape"),   1),
 	new Box(new Coord(300, 210), Resource.loadtex("gfx/hud/inv/costumepants"),  1),
 	new Box(new Coord(250, 210), Resource.loadtex("gfx/hud/inv/costumefeet"),   1),
    };
    public static final Coord isz = isz();
    public final Widget[] tabs = new Widget[2];
    public final WItem[] slots = new WItem[boxen.length];
    private final Map<GItem, WItem[]> wmap = new HashMap<GItem, WItem[]>();

    private static Coord isz() {
	Coord isz = new Coord();
	for(Box box : boxen) {
	    if(box == null)
		continue;
	    if(box.c.x + sqlite.sz().x > isz.x)
		isz.x = box.c.x + sqlite.sz().x;
	    if(box.c.y + sqlite.sz().y > isz.y)
		isz.y = box.c.y + sqlite.sz().y;
	}
	return(isz);
    }

    public static class Box {
	public final Coord c;
	public final Tex bg;
	public final int tab;

	public Box(Coord c, Tex bg, int tab) {
	    this.c = c;
	    this.bg = bg;
	    this.tab = tab;
	}
    }

    @RName("epry")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    long gobid;
	    if(args.length < 1)
		gobid = parent.getparent(GameUI.class).plid;
	    else
		gobid = (Integer)args[0];
	    return(new Equipory(c, parent, gobid));
	}
    }

    private class Boxen extends Widget implements DTarget {
	final int tab;

	private Boxen(Coord c, Widget parent, int tab) {
	    super(c, isz, parent);
	    this.tab = tab;
	}

	public void draw(GOut g) {
	    for(int i = 0; i < boxen.length; i++) {
		Box box = boxen[i];
		if((box == null) || (box.tab != this.tab))
		    continue;
		g.image(sqlite, box.c);
		if((slots[i] == null) && (box.bg != null))
		    g.image(box.bg, box.c.add(sqlo));
	    }
	}

	public boolean drop(Coord cc, Coord ul) {
	    ul = ul.add(sqlite.sz().div(2));
	    for(int i = 0; i < boxen.length; i++) {
		Box box = boxen[i];
		if((box == null) || (box.tab != this.tab))
		    continue;
		if(ul.isect(box.c, sqlite.sz())) {
		    Equipory.this.wdgmsg("drop", i);
		    return(true);
		}
	    }
	    Equipory.this.wdgmsg("drop", -1);
	    return(true);
	}

	public boolean iteminteract(Coord cc, Coord ul) {
	    return(false);
	}
    }

    public Equipory(Coord c, Widget parent, long gobid) {
	super(c, isz, parent);
	Avaview ava = new Avaview(Coord.z, isz, this, gobid, "equcam") {
		public boolean mousedown(Coord c, int button) {
		    return(false);
		}

		protected java.awt.Color clearcolor() {return(null);}
	    };
	int bx = 0;
	String s1 = "Equipment";
	String s2 = "Costume";
	String s3;
	
	
	for(int i = 0; i < tabs.length; i++) {
	    tabs[i] = new Widget(Coord.z, this.sz, this);
	    tabs[i].show(i == 0);
	    new Boxen(Coord.z, tabs[i], i);
	    final int t = i;
	    /*
	    new Button(new Coord(bx, isz.y + 5), 40, this, "Tab " + (i + 1)) {
		public void click() {
		    for(int i = 0; i < tabs.length; i++)
			tabs[i].show(i == t);
		}
	    };
	    */
	    if(t > 0)
		s3 = s2;
	    else
		s3 = s1;
	    
	    Widget btn = new Button(new Coord(bx, isz.y + 5), 60, this, s3) { //"Tab " + (i + 1)) {
		    public void click() {
			for(int i = 0; i < tabs.length; i++)
			    tabs[i].show(i == t);
		    }
		};
	    if(t > 0)
		btn.tooltip = Text.render("Costume");
	    else
		btn.tooltip = Text.render("Equipment");
	    bx = btn.c.x + btn.sz.x + 228;
	}
	pack();
    }

    public Widget makechild(String type, Object[] pargs, Object[] cargs) {
	Widget ret = gettype(type).create(Coord.z, this, cargs);
	if(ret instanceof GItem) {
	    GItem g = (GItem)ret;
	    WItem[] v = new WItem[pargs.length];
	    for(int i = 0; i < pargs.length; i++) {
		int ep = (Integer)pargs[i];
		Box box = boxen[ep];
		slots[ep] = v[i] = new WItem(box.c.add(sqlo), tabs[box.tab], g);
	    }
	    wmap.put(g, v);
	}
	return(ret);
    }

    public void cdestroy(Widget w) {
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
	    for(WItem v : wmap.remove(i)) {
		ui.destroy(v);
		for(int s = 0; s < slots.length; s++) {
		    if(slots[s] == v)
			slots[s] = null;
		}
	    }
	}
    }
}
