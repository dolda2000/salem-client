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

import java.awt.*;
import java.awt.event.KeyEvent;

public class TextEntry extends Widget {
    public static final Text.Foundry fnd = new Text.Foundry(new Font("SansSerif", Font.PLAIN, 12), Color.WHITE);
    public static final int defh = fnd.height() + 2;
    public static final IBox box = Window.tbox;
    public static final int toffx = box.bl.sz().x + 1;
    public static final int wmarg = box.bl.sz().x + box.br.sz().x + 3;
    public LineEdit buf;
    public int sx;
    public boolean pw = false;
    public String text;
    private Text.Line tcache = null;

    @RName("text")
    public static class $_ implements Factory {
	public Widget create(Coord c, Widget parent, Object[] args) {
	    if(args[0] instanceof Coord)
		return(new TextEntry(c, (Coord)args[0], parent, (String)args[1]));
	    else
		return(new TextEntry(c, (Integer)args[0], parent, (String)args[1]));
	}
    }

    public void settext(String text) {
	buf.setline(text);
    }

    public void rsettext(String text) {
	buf = new LineEdit(this.text = text) {
		protected void done(String line) {
		    activate(line);
		}
		
		protected void changed() {
		    TextEntry.this.text = line;
		    TextEntry.this.changed();
		}
	    };
    }

    public void uimsg(String name, Object... args) {
	if(name == "settext") {
	    settext((String)args[0]);
	} else if(name == "get") {
	    wdgmsg("text", buf.line);
	} else if(name == "pw") {
	    pw = ((Integer)args[0]) == 1;
	} else {
	    super.uimsg(name, args);
	}
    }

    protected void drawbg(GOut g) {
	g.chcolor(0, 0, 0, 255);
	g.frect(Coord.z, sz);
	g.chcolor();
    }

    protected void drawbb(GOut g) {
	g.image(box.bt, new Coord(box.ctl.sz().x, 0), new Coord(sz.x - box.ctr.sz().x - box.ctl.sz().x, box.bt.sz().y));
	g.image(box.bb, new Coord(box.cbl.sz().x, sz.y - box.bb.sz().y), new Coord(sz.x - box.cbr.sz().x - box.cbl.sz().x, box.bb.sz().y));
    }

    protected void drawfb(GOut g) {
	g.image(box.bl, new Coord(0, box.ctl.sz().y), new Coord(box.bl.sz().x, sz.y - box.cbl.sz().y - box.ctl.sz().y));
	g.image(box.br, new Coord(sz.x - box.br.sz().x, box.ctr.sz().y), new Coord(box.br.sz().x, sz.y - box.cbr.sz().y - box.ctr.sz().y));
	g.image(box.ctl, Coord.z);
	g.image(box.ctr, new Coord(sz.x - box.ctr.sz().x, 0));
	g.image(box.cbl, new Coord(0, sz.y - box.cbl.sz().y));
	g.image(box.cbr, new Coord(sz.x - box.cbr.sz().x, sz.y - box.cbr.sz().y));
    }

    public void draw(GOut g) {
	super.draw(g);
	String dtext;
	if(pw) {
	    dtext = "";
	    for(int i = 0; i < buf.line.length(); i++)
		dtext += "*";
	} else {
	    dtext = buf.line;
	}
	drawbg(g);
	drawbb(g);
	if((tcache == null) || !tcache.text.equals(dtext))
	    tcache = fnd.render(dtext);
	int cx = tcache.advance(buf.point);
	if(cx < sx) sx = cx;
	if(cx > sx + (sz.x - wmarg)) sx = cx - (sz.x - wmarg);
	int ty = (sz.y - tcache.sz().y) / 2;
	g.image(tcache.tex(), new Coord(toffx - sx, ty));
	if(hasfocus && ((System.currentTimeMillis() % 1000) > 500)) {
	    int lx = toffx + cx - sx + 1;
	    g.line(new Coord(lx, ty + 1), new Coord(lx, ty + tcache.sz().y - 1), 1);
	}
	drawfb(g);
    }

    public TextEntry(Coord c, Coord sz, Widget parent, String deftext) {
	super(c, sz, parent);
	rsettext(deftext);
	setcanfocus(true);
    }

    public TextEntry(Coord c, int w, Widget parent, String deftext) {
	this(c, new Coord(w, defh), parent, deftext);
    }

    protected void changed() {
    }

    public void activate(String text) {
	if(canactivate)
	    wdgmsg("activate", text);
    }

    public boolean type(char c, KeyEvent ev) {
	return(buf.key(ev));
    }

    public boolean keydown(KeyEvent e) {
	buf.key(e);
	return(true);
    }

    public boolean mousedown(Coord c, int button) {
	parent.setfocus(this);
	if(tcache != null) {
	    buf.point = tcache.charat(c.x + sx);
	}
	return(true);
    }
}
