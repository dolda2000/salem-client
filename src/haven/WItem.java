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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.*;
import static haven.ItemInfo.find;

public class WItem extends Widget implements DTarget {
    public static final Resource missing = Resource.load("gfx/invobjs/missing");
    public final GItem item;
    private Tex ltex = null;
    private Tex mask = null;
    private Resource cmask = null;
    
    public WItem(Coord c, Widget parent, GItem item) {
	super(c, Inventory.sqsz, parent);
	this.item = item;
    }
    
    private static Coord upsize(Coord sz) {
	int w = sz.x, h = sz.y;
	if((w % Inventory.sqsz.x) != 0)
	    w = Inventory.sqsz.x * ((w / Inventory.sqsz.x) + 1);
	if((h % Inventory.sqsz.y) != 0)
	    h = Inventory.sqsz.y * ((h / Inventory.sqsz.y) + 1);
	return(new Coord(w, h));
    }

    public void drawmain(GOut g, Tex tex) {
	g.image(tex, Coord.z);
	if(tex != ltex) {
	    resize(upsize(tex.sz()));
	    ltex = tex;
	}
    }

    public static BufferedImage rendershort(List<ItemInfo> info) {
	ItemInfo.Name nm = find(ItemInfo.Name.class, info);
	if(nm == null)
	    return(null);
	BufferedImage img = nm.str.img;
	Alchemy ch = find(Alchemy.class, info);
	if(ch != null)
	    img = ItemInfo.catimgsh(5, img, ch.smallmeter(),
				    Text.std.renderf("(%d%% pure)", (int)(ch.a[0] * 100)).img);
	return(img);
    }

    public static BufferedImage shorttip(List<ItemInfo> info) {
	BufferedImage img = rendershort(info);
	ItemInfo.Contents cont = find(ItemInfo.Contents.class, info);
	if(cont != null) {
	    BufferedImage rc = rendershort(cont.sub);
	    if((img != null) && (rc != null))
		img = ItemInfo.catimgs(0, img, rc);
	    else if((img == null) && (rc != null))
		img = rc;
	}
	if(img == null)
	    return(null);
	return(img);
    }
    
    public static BufferedImage longtip(GItem item, List<ItemInfo> info) {
	BufferedImage img = ItemInfo.longtip(info);
	Resource.Pagina pg = item.res.get().layer(Resource.pagina);
	if(pg != null)
	    img = ItemInfo.catimgs(0, img, RichText.render("\n" + pg.text, 200).img);
	return(img);
    }
    
    public BufferedImage longtip(List<ItemInfo> info) {
	return(longtip(item, info));
    }
    
    public class ItemTip implements Indir<Tex> {
	private final TexI tex;
	
	public ItemTip(BufferedImage img) {
	    if(img == null)
		throw(new Loading());
	    tex = new TexI(img);
	}
	
	public GItem item() {
	    return(item);
	}
	
	public Tex get() {
	    return(tex);
	}
    }
    
    public class ShortTip extends ItemTip {
	public ShortTip(List<ItemInfo> info) {super(shorttip(info));}
    }
    
    public class LongTip extends ItemTip {
	public LongTip(List<ItemInfo> info) {super(longtip(info));}
    }

    private long hoverstart;
    private ItemTip shorttip = null, longtip = null;
    private List<ItemInfo> ttinfo = null;
    public Object tooltip(Coord c, Widget prev) {
	long now = System.currentTimeMillis();
	if(prev == this) {
	} else if(prev instanceof WItem) {
	    long ps = ((WItem)prev).hoverstart;
	    if(now - ps < 1000)
		hoverstart = now;
	    else
		hoverstart = ps;
	} else {
	    hoverstart = now;
	}
	try {
	    List<ItemInfo> info = item.info();
	    if(info.size() < 1)
		return(null);
	    if(info != ttinfo) {
		shorttip = longtip = null;
		ttinfo = info;
	    }
	    if(now - hoverstart < 1000) {
		if(shorttip == null)
		    shorttip = new ShortTip(info);
		return(shorttip);
	    } else {
		if(longtip == null)
		    longtip = new LongTip(info);
		return(longtip);
	    }
	} catch(Loading e) {
	    return("...");
	}
    }

    public abstract class AttrCache<T> {
	private List<ItemInfo> forinfo = null;
	private T save = null;
	
	public T get() {
	    try {
		List<ItemInfo> info = item.info();
		if(info != forinfo) {
		    save = find(info);
		    forinfo = info;
		}
	    } catch(Loading e) {
		return(null);
	    }
	    return(save);
	}
	
	protected abstract T find(List<ItemInfo> info);
    }
    
    public final AttrCache<Color> olcol = new AttrCache<Color>() {
	protected Color find(List<ItemInfo> info) {
	    GItem.ColorInfo cinf = ItemInfo.find(GItem.ColorInfo.class, info);
	    return((cinf == null)?null:cinf.olcol());
	}
    };
    
    public final AttrCache<Tex> itemnum = new AttrCache<Tex>() {
	protected Tex find(List<ItemInfo> info) {
	    GItem.NumberInfo ninf = ItemInfo.find(GItem.NumberInfo.class, info);
	    if(ninf == null) return(null);
	    return(new TexI(Utils.outline2(Text.render(Integer.toString(ninf.itemnum()), Color.WHITE).img, Utils.contrast(Color.WHITE))));
	}
    };
    
    public void draw(GOut g) {
	try {
	    Resource res = item.res.get();
	    Tex tex = res.layer(Resource.imgc).tex();
	    drawmain(g, tex);
	    if(item.num >= 0) {
		g.atext(Integer.toString(item.num), tex.sz(), 1, 1);
	    } else if(itemnum.get() != null) {
		g.aimage(itemnum.get(), tex.sz(), 1, 1);
	    }
	    if(item.meter > 0) {
		double a = ((double)item.meter) / 100.0;
		g.chcolor(255, 255, 255, 64);
		Coord half = Inventory.isqsz.div(2);
		g.prect(half, half.inv(), half, a * Math.PI * 2);
		g.chcolor();
	    }
	    if(olcol.get() != null) {
		if(cmask != res) {
		    mask = null;
		    if(tex instanceof TexI)
			mask = ((TexI)tex).mkmask();
		    cmask = res;
		}
		if(mask != null) {
		    g.chcolor(olcol.get());
		    g.image(mask, Coord.z);
		    g.chcolor();
		}
	    }
	} catch(Loading e) {
	    missing.loadwait();
	    g.image(missing.layer(Resource.imgc).tex(), Coord.z, sz);
	}
    }
    
    public boolean mousedown(Coord c, int btn) {
	if(btn == 1) {
	    if(ui.modshift)
		item.wdgmsg("transfer", c, ui.modmeta ? -1 : 1);
	    else if(ui.modctrl)
		item.wdgmsg("drop", c, ui.modmeta ? -1 : 1);
	    else
		item.wdgmsg("take", c);
	    return(true);
	} else if(btn == 3) {
	    item.wdgmsg("iact", c);
	    return(true);
	}
	return(false);
    }

    public boolean drop(Coord cc, Coord ul) {
	return(false);
    }
	
    public boolean iteminteract(Coord cc, Coord ul) {
	item.wdgmsg("itemact", ui.modflags());
	return(true);
    }
}
