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
import java.io.*;
import java.net.*;
import java.awt.Font;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class Store extends Window {
    public static final Tex bg = Resource.loadtex("gfx/hud/store/bg");
    public static final Text.Foundry textf = new Text.Foundry(new Font("Sans", Font.BOLD, 16), Color.BLACK).aa(true);
    public static final Text.Foundry texto = new Text.Foundry(new Font("Sans", Font.BOLD, 14), Color.BLACK).aa(true);
    public static final RichText.Foundry descfnd = new RichText.Foundry(java.awt.font.TextAttribute.FAMILY, "SansSerif",
									java.awt.font.TextAttribute.SIZE, 12,
									java.awt.font.TextAttribute.FOREGROUND, Button.defcol).aa(true);
    public static final SslHelper ssl;
    public final URI base;

    static {
	ssl = new SslHelper();
	try {
	    ssl.trust(Resource.class.getResourceAsStream("ressrv.crt"));
	} catch(java.security.cert.CertificateException e) {
	    throw(new Error("Invalid built-in certificate", e));
	} catch(IOException e) {
	    throw(new Error(e));
	}
    }

    public Store(Coord c, Widget parent, URI base) {
	super(c, new Coord(750, 450), parent, "Salem Store");
	Widget bg = new Img(Coord.z, this.bg, this);
	bg.c = new Coord((sz.x - bg.sz.x) / 2, 0);
	this.base = base;
	new Loader();
    }

    public static abstract class Currency {
	private static final Map<String, Currency> defined = new HashMap<>();
	public final String symbol;

	public Currency(String symbol) {
	    this.symbol = symbol;
	}

	public abstract String format(int amount);

	public static Currency define(Currency c) {
	    defined.put(c.symbol, c);
	    return(c);
	}
	public static Currency decimal(String symbol, int dec, String sep, String fmt) {
	    return(define(new Currency(symbol) {
		    int u = (int)Math.round(Math.pow(10, dec));
		    private String formata(int amount) {
			int a = amount / u, b = amount - (a * u);
			return(String.format("%d%s%0" + dec + "d", a, sep, b));
		    }

		    public String format(int amount) {
			String a = (amount < 0) ? "-" + formata(-amount) : formata(amount);
			return(String.format(fmt, a));
		    }
		}));
	}

	public static Currency get(String symbol) {
	    Currency ret = defined.get(symbol);
	    if(ret == null)
		throw(new IllegalArgumentException(symbol));
	    return(ret);
	}
    }
    static {Currency.decimal("USD", 2, ".", "$%s");}

    public static class Price {
	public final Currency c;
	public final int a;

	public Price(Currency c, int a) {
	    this.c = c;
	    this.a = a;
	}

	public String toString() {
	    return(c.format(a));
	}

	public static Price parse(Object[] enc) {
	    return(new Price(Currency.get((String)enc[0]), (Integer)enc[1]));
	}
    }

    public static class Offer {
	public final String id, ver;
	public String name = "", desc = null, category = null;
	public Price price;
	public Defer.Future<BufferedImage> img = null;
	public boolean singleton;
	public int sortkey;

	public Offer(String id, String ver) {
	    this.id = id;
	    this.ver = ver;
	}
    }

    public static class Category {
	public final String id;
	public String name = "", parent = null;
	public int sortkey;

	public Category(String id) {
	    this.id = id;
	}
    }

    public static class Catalog {
	public final List<Offer> offers;
	public final List<Category> catgs;
	public final Map<String, Category> catgid;
	public final Price credit;

	public Catalog(List<Offer> offers, List<Category> catgs, Price credit) {
	    this.offers = offers;
	    this.catgs = catgs;
	    catgid = new HashMap<>();
	    for(Category catg : catgs)
		catgid.put(catg.id, catg);
	    this.credit = credit;
	}
    }

    public static class Cart {
	public final List<Item> items = new ArrayList<>();
	public final Currency currency;

	public Cart(Currency currency) {
	    this.currency = currency;
	}

	public Cart(Catalog cat) {
	    this(Utils.el(cat.offers).price.c);
	}

	public static class Item {
	    public final Offer offer;
	    public int num;

	    public Item(Offer offer, int num) {
		this.offer = offer;
		this.num = num;
	    }

	    public Price total() {
		return(new Price(offer.price.c, offer.price.a * num));
	    }
	}

	public Item byoffer(Offer offer, boolean creat) {
	    for(Item item : items) {
		if(item.offer == offer)
		    return(item);
	    }
	    if(creat) {
		Item ret = new Item(offer, 0);
		items.add(ret);
		return(ret);
	    }
	    return(null);
	}

	public boolean remove(Item item) {
	    return(items.remove(item));
	}

	public Price total() {
	    int a = 0;
	    for(Item item : items) {
		Price ip = item.total();
		if(ip.c != currency)
		    throw(new RuntimeException("conflicting currencies"));
		a += ip.a;
	    }
	    return(new Price(currency, a));
	}
    }

    public static class MessageError extends RuntimeException {
	public MessageError(String msg) {
	    super(msg);
	}
    }

    public class Loader extends Widget {
	private final Defer.Future<Catalog> cat;

	public Loader() {
	    super(Coord.z, Store.this.asz, Store.this);
	    this.cat = Defer.later(Store.this::catalog);
	    Label l = new Label(Coord.z, this, "Loading...", textf);
	    l.c = sz.sub(l.sz).div(2);
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(cat.done()) {
		Catalog cat;
		try {
		    cat = this.cat.get();
		} catch(Defer.DeferredException de) {
		    Throwable exc = de.getCause();
		    new Reloader(exc);
		    ui.destroy(this);
		    return;
		}
		new Browser(cat);
		ui.destroy(this);
	    }
	}
    }

    public class Reloader extends Widget {
	private boolean done = false;

	public Reloader(Throwable exc) {
	    super(Coord.z, Store.this.asz, Store.this);
	    String msg = "Error loading catalog";
	    if(exc instanceof MessageError)
		msg = exc.getMessage();
	    Label l = new Label(Coord.z, this, msg, textf);
	    l.c = sz.sub(l.sz).div(2);
	    new Button(Coord.z, 75, this, "Reload") {
		public void click() {
		    done = true;
		}
	    }.c = new Coord((sz.x - 75) / 2, l.c.y + l.sz.y + 10);
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(done) {
		new Loader();
		ui.destroy(this);
	    }
	}
    }

    public static abstract class OButton extends Widget {
	public static final int imgsz = 40;
	public static final PUtils.Convolution filter = new PUtils.Lanczos(2);
	public final Text text;
	public final Defer.Future<BufferedImage> img;
	private boolean a;
	private Tex rimg;

	public OButton(Coord c, Coord sz, Widget parent, String text, Defer.Future<BufferedImage> img) {
	    super(c, sz, parent);
	    this.img = img;
	    int w = (img == null) ? sz.x - 20 : sz.x - 25 - imgsz;
	    this.text = texto.renderwrap(text, Button.defcol, w);
	}

	public void draw(GOut g) {
	    Coord off = a ? new Coord(2, 2) : Coord.z;
	    if(img == null) {
		g.image(text.tex(), sz.sub(text.sz()).div(2).add(off));
	    } else {
		try {
		    if(this.rimg == null) {
			BufferedImage rimg = this.img.get();
			Coord rsz = Utils.imgsz(rimg);
			Coord ssz = (rsz.x > rsz.y) ? new Coord(imgsz, (rsz.y * imgsz) / rsz.x) : new Coord((rsz.x * imgsz) / rsz.y, imgsz);
			BufferedImage simg = PUtils.convolvedown(rimg, ssz, filter);
			this.rimg = new TexI(simg);
		    }
		    g.image(this.rimg, new Coord(10 + ((imgsz - rimg.sz().x) / 2), (sz.y - rimg.sz().y) / 2).add(off));
		} catch(Loading l) {
		} catch(Defer.DeferredException e) {
		}
		g.image(text.tex(), new Coord(imgsz + 15, (sz.y - text.sz().y) / 2).add(off));
	    }
	    Window.wbox.draw(g, Coord.z, sz);
	}

	public abstract void click();

	public boolean mousedown(Coord c, int btn) {
	    if(btn != 1)
		return(false);
	    a = true;
	    ui.grabmouse(this);
	    return(true);
	}

	public boolean mouseup(Coord c, int btn) {
	    if(a && (btn == 1)) {
		a = false;
		ui.grabmouse(null);
		if(c.isect(Coord.z, sz))
		    click();
		return(true);
	    }
	    return(false);
	}
    }

    public static class NumberEntry extends TextEntry {
	private String prev;

	public NumberEntry(Coord c, int w, Widget parent, int def) {
	    super(c, w, parent, Integer.toString(def));
	    prev = text;
	}

	private boolean valid(String t) {
	    if(t.equals(""))
		return(true);
	    try {
		Integer.parseInt(t);
	    } catch(NumberFormatException e) {
		return(false);
	    }
	    return(true);
	}

	protected void changed() {
	    if(valid(text))
		prev = text;
	    else
		settext(prev);
	}

	public int get() {
	    try {
		return(Integer.parseInt(text));
	    } catch(NumberFormatException e) {
		return(0);
	    }
	}
    }

    public static class CartWidget extends Widget {
	public static final Text empty = Text.render("Cart is empty", Button.defcol);
	public static final int numw = 30, pricew = 50;
	public final Cart cart;
	public Button checkout;

	public CartWidget(Coord c, Coord sz, Widget parent, Cart cart) {
	    super(c, sz, parent);
	    this.cart = cart;
	    new ListWidget(Coord.z, sz.sub(0, 25), this);
	}

	public class ListWidget extends Widget {
	    public final Scrollport scr;
	    private final Map<Cart.Item, ItemWidget> items = new HashMap<>();

	    public ListWidget(Coord c, Coord sz, Widget parent) {
		super(c, sz, parent);
		this.scr = new Scrollport(Window.fbox.btloff(), sz.sub(Window.fbox.bisz()), this);
	    }

	    private void update() {
		Map<Cart.Item, ItemWidget> old = new HashMap<>(items);
		int y = 0;
		boolean ch = false;
		for(Cart.Item item : cart.items) {
		    ItemWidget wdg = items.get(item);
		    if(wdg == null) {
			wdg = new ItemWidget(new Coord(0, y), scr.cont.sz.x, scr.cont, item);
			items.put(item, wdg);
			ch = true;
		    } else {
			old.remove(item);
			if(wdg.c.y != y) {
			    wdg.c = new Coord(wdg.c.x, y);
			    ch = true;
			}
		    }
		    y += wdg.sz.y;
		}
		for(ItemWidget wdg : old.values()) {
		    ui.destroy(wdg);
		    ch = true;
		}
		if(ch)
		    scr.cont.update();
	    }

	    public void tick(double dt) {
		update();
		super.tick(dt);
	    }

	    public void draw(GOut g) {
		g.chcolor(0, 0, 0, 128);
		g.frect(Coord.z, sz);
		g.chcolor();
		super.draw(g);
		if(cart.items.isEmpty())
		    g.image(empty.tex(), sz.sub(empty.sz()).div(2));
		Window.fbox.draw(g, Coord.z, sz);
	    }
	}

	public class ItemWidget extends Widget {
	    public final int nmw, numx, pricex;
	    public final Cart.Item item;
	    public final Widget rbtn;

	    public ItemWidget(Coord c, int w, Widget parent, Cart.Item item) {
		super(c, new Coord(w, 20), parent);
		this.item = item;
		rbtn = new IButton(Coord.z, this, Window.cbtni[0], Window.cbtni[1], Window.cbtni[2]) {
			public void click() {
			    cart.remove(item);
			}
		    };
		rbtn.c = new Coord(sz.x - rbtn.sz.x, (sz.y - rbtn.sz.y) / 2);
		pricex = rbtn.c.x - 5 - pricew;
		numx = pricex - 5 - numw;
		nmw = numx - 7;
	    }

	    private Text rname, rnum, rprice;
	    private int cnum;
	    public void draw(GOut g) {
		if(rname == null)
		    rname = Text.render(item.offer.name, Button.defcol);
		g.image(rname.tex(), new Coord(5, (sz.y - rname.sz().y) / 2), Coord.z, new Coord(nmw, sz.y));
		if(!item.offer.singleton) {
		    if((rnum == null) || (cnum != item.num)) {
			rnum = Text.render("\u00d7" + item.num, Button.defcol);
			rprice = null;
			cnum = item.num;
		    }
		    g.image(rnum.tex(), new Coord(numx, (sz.y - rnum.sz().y) / 2));
		}
		if(rprice == null)
		    rprice = Text.render(item.total().toString(), Button.defcol);
		g.image(rprice.tex(), new Coord(pricex, (sz.y - rprice.sz().y) / 2));
		super.draw(g);
	    }

	    public boolean mousedown(Coord c, int btn) {
		if(super.mousedown(c, btn))
		    return(true);
		return(clickitem(item, btn));
	    }
	}

	protected boolean clickitem(Cart.Item item, int btn) {
	    return(false);
	}

	protected void checkout() {
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(cart.items.isEmpty() && (checkout != null)) {
		ui.destroy(checkout);
		checkout = null;
	    } else if(!cart.items.isEmpty() && (checkout == null)) {
		checkout = new Button(new Coord(5, sz.y - 23), 75, this, "Checkout") {
			public void click() {
			    checkout();
			}
		    };
	    }
	}

	private Text rtotal = null;
	public void draw(GOut g) {
	    super.draw(g);
	    if(!cart.items.isEmpty()) {
		String total = "Total: " + cart.total();
		if((rtotal == null) || !rtotal.text.equals(total))
		    rtotal = Text.render(total, Button.defcol);
		g.image(rtotal.tex(), new Coord(sz.x - 5 - rtotal.sz().x, sz.y - ((25 + rtotal.sz().y) / 2)));
	    }
	}
    }

    public abstract class Checkouter extends Widget {
	public final Catalog cat;
	public final Cart cart;
	private Widget status, detail, buttons[];

	public Checkouter(Catalog cat, Cart cart) {
	    super(Coord.z, Store.this.asz, Store.this);
	    this.cat = cat;
	    this.cart = cart;
	}

	public void reload() {
	    ui.destroy(this);
	    new Loader();
	}

	public void back() {
	    ui.destroy(this);
	    new Browser(cat, cart);
	}

	public void statusv(String msg, String detail, String[] buttons, Runnable[] actions) {
	    if(this.status != null) {ui.destroy(this.status); this.status = null;}
	    if(this.detail != null) {ui.destroy(this.detail); this.detail = null;}
	    if(this.buttons != null) {
		for(Widget btn : this.buttons)
		    ui.destroy(btn);
		this.buttons = null;
	    }
	    int y = sz.y / 3;
	    if(msg != null) {
		this.status = new Img(Coord.z, textf.render(msg, Button.defcol).tex(), this);
		this.status.c = new Coord((sz.x - this.status.sz.x) / 2, y); y += this.status.sz.y + 5;
	    }
	    if(detail != null) {
		this.detail = new Img(Coord.z, descfnd.render(detail, 400).tex(), this);
		this.detail.c = new Coord((sz.x - this.detail.sz.x) / 2, y); y += this.detail.sz.y + 5;
	    }
	    if(buttons.length > 0) {
		this.buttons = new Widget[buttons.length];
		int x = (sz.x - ((buttons.length * 100) + ((buttons.length - 1) * 20))) / 2;
		for(int i = 0; i < buttons.length; i++) {
		    Runnable action = actions[i];
		    this.buttons[i] = new Button(new Coord(x, y), 100, this, buttons[i]) {
			    public void click() {
				action.run();
			    }
			};
		    x += 100 + 20;
		}
	    }
	}

	public void status(String msg, String detail, String button, Runnable action) {
	    statusv(msg, detail, (button == null) ? new String[0] : new String[] {button}, (button == null) ? new Runnable[0] : new Runnable[] {action});
	}
    }

    public class BrowserCheckouter extends Checkouter {
	public final Price credit;
	private Defer.Future<Object[]> submit;

	public BrowserCheckouter(Catalog cat, Cart cart, Price credit) {
	    super(cat, cart);
	    this.credit = credit;
	    submit = Defer.later(this::submit);
	    status("Checking out...", null, null, null);
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(submit != null) {
		try {
		    Map<Object, Object> stat = Utils.mapdecf(submit.get());
		    submit = null;
		    if(Utils.eq(stat.get("status"), "ok")) {
			try {
			    URL url = new URL((String)stat.get("url"));
			    WebBrowser.sshow(url);
			    done();
			} catch(WebBrowser.BrowserException | IOException e) {
			    status("Could not launch web browser.", RichText.Parser.quote(String.valueOf(e)), "Return", this::back);
			}
		    } else if(Utils.eq(stat.get("status"), "obsolete")) {
			status("The catalog has changed while you were browsing.", null, "Reload", this::reload);
		    } else if(Utils.eq(stat.get("status"), "invalid")) {
			status("The purchase has become invalid.", RichText.Parser.quote((String)stat.get("msg")), "Reload", this::reload);
		    }
		} catch(Loading l) {
		} catch(Defer.DeferredException e) {
		    submit = null;
		    status("An unexpected error occurred.", RichText.Parser.quote(String.valueOf(e.getCause())), "Return", this::back);
		    e.printStackTrace();
		}
	    }
	}

	private Object[] submit() {
	    Map<String, Object> data = new HashMap<>();
	    data.put("cart", encode(cart));
	    data.put("method", "paypal");
	    if(credit != null)
		data.put("usecredit", credit.a);
	    URLConnection conn = req("checkout");
	    send(conn, Utils.mapencf(data));
	    return(fetch(conn));
	}

	private void done() {
	    status("Thank you!", "Please follow the instructions in the web browser to complete your purchase.", "Return", this::reload);
	}
    }

    public class CreditCheckouter extends Checkouter {
        private Defer.Future<Object[]> submit, execute;
        private String txnid;

        public CreditCheckouter(Catalog cat, Cart cart) {
            super(cat, cart);
            submit = Defer.later(this::submit);
            status("Checking out...", null, null, null);
        }

	private void authorize() {
	    status("Executing order...", null, null, null);
	    execute = Defer.later(this::execute);
	}

        public void tick(double dt) {
            super.tick(dt);
            if(submit != null) {
                try {
                    Map<Object, Object> stat = Utils.mapdecf(submit.get());
                    submit = null;
                    if(Utils.eq(stat.get("status"), "ok")) {
                        statusv("Your purchase can be completed on credit alone.", RichText.Parser.quote("Do you wish to continue? " + cart.total() + " of store credit will be used."),
				new String[] {"Confirm", "Return"}, new Runnable[] {this::authorize, this::back});
                        txnid = (String)stat.get("cart");
                    } else if(Utils.eq(stat.get("status"), "obsolete")) {
                        status("The catalog has changed while you were browsing.", null, "Reload", this::reload);
                    } else if(Utils.eq(stat.get("status"), "invalid")) {
                        status("The purchase has become invalid.", RichText.Parser.quote((String)stat.get("msg")), "Reload", this::reload);
                    } else if(Utils.eq(stat.get("status"), "err")) {
                        status("An unexpected error occurred.", RichText.Parser.quote((String)stat.get("msg")), "Return", this::back);
                    }
                } catch(Loading l) {
                } catch(Defer.DeferredException e) {
                    submit = null;
                    status("An unexpected error occurred.", RichText.Parser.quote(String.valueOf(e.getCause())), "Return", this::back);
                }
            }
            if(execute != null) {
                try {
                    Map<Object, Object> stat = Utils.mapdecf(execute.get());
                    execute = null;
                    if(Utils.eq(stat.get("status"), "ok")) {
                        done();
                    } else if(Utils.eq(stat.get("status"), "obsolete")) {
                        status("The catalog has changed while you were browsing.", null, "Reload", this::reload);
                    } else if(Utils.eq(stat.get("status"), "invalid")) {
                        status("The purchase has become invalid.", RichText.Parser.quote((String)stat.get("msg")), "Reload", this::reload);
                    } else if(Utils.eq(stat.get("status"), "err")) {
                        status("An unexpected error occurred.", RichText.Parser.quote((String)stat.get("msg")), "Return", this::back);
                    }
                } catch(Loading l) {
                } catch(Defer.DeferredException e) {
                    execute = null;
                    status("An unexpected error occurred.", RichText.Parser.quote(String.valueOf(e.getCause())), "Return", this::back);
                }
            }
        }

        private Object[] submit() {
            Map<String, Object> data = new HashMap<>();
            data.put("cart", encode(cart));
            data.put("method", "credit");
	    data.put("usecredit", cart.total().a);
            URLConnection conn = req("checkout");
            send(conn, Utils.mapencf(data));
            return(fetch(conn));
        }

        private Object[] execute() {
            URLConnection conn = req("creditfin", "cart", txnid);
            conn.setDoOutput(true);
            return(fetch(conn));
        }

        private void done() {
            status("Thank you!", "Your purchase has been completed.", "Return", this::reload);
        }
    }

    public class Browser extends Widget {
	public final Coord bsz = new Coord(175, 80);
	public final Cart cart;
	public final Catalog cat;
	public final HScrollport btns;
	private Img clbl;
	private IButton bbtn;

	public Browser(Catalog cat, Cart cart) {
	    super(Coord.z, Store.this.asz, Store.this);
	    this.cat = cat;
	    this.cart = cart;
	    this.btns = new HScrollport(new Coord(10, sz.y - 180), new Coord(sz.x - 20, 180), this);
	    new CartWidget(new Coord(sz.x - 200 - 10, 0), new Coord(200, sz.y - 200), this, cart) {
		public boolean clickitem(Cart.Item item, int btn) {
		    new Viewer(item.offer, Browser.this, cart);
		    return(true);
		}

		public void checkout() {
		    if((cat.credit != null) && (cat.credit.a >= cart.total().a)) {
			new CreditCheckouter(cat, cart);
		    } else {
			new BrowserCheckouter(cat, cart, cat.credit);
		    }
		    ui.destroy(Browser.this);
		}
	    };
	    point(null);
	}

	public Browser(Catalog cat) {
	    this(cat, new Cart(cat));
	}

	public class OfferButton extends OButton {
	    public final Offer offer;

	    public OfferButton(Coord c, Coord sz, Widget parent, Offer offer) {
		super(c, sz, parent, offer.name, offer.img);
		this.offer = offer;
	    }

	    public void click() {
		new Viewer(offer, Browser.this, cart);
	    }
	}

	public class CategoryButton extends OButton {
	    public final Category catg;

	    public CategoryButton(Coord c, Coord sz, Widget parent, Category catg) {
		super(c, sz, parent, catg.name, null);
		this.catg = catg;
	    }

	    public void click() {
		point(catg.id);
	    }
	}

	private void linebtns(Widget p, List<? extends Widget> btns, int y) {
	    int tw = 0;
	    for(Widget w : btns)
		tw += w.sz.x;
	    int e = 0, x = 0;
	    for(Widget w : btns) {
		e += p.sz.x - tw;
		int a = e / (btns.size() + 1);
		e -= a * (btns.size() + 1);
		x += a;
		w.c = new Coord(x, y);
		x += w.sz.x;
	    }
	}

	public void point(String catg) {
	    if(clbl != null) {ui.destroy(clbl); clbl = null;}
	    if(bbtn != null) {ui.destroy(bbtn); bbtn = null;}
	    while(btns.cont.child != null)
		ui.destroy(btns.cont.child);

	    if(catg != null) {
		Category ccat = cat.catgid.get(catg);
		String catp = ccat.name;
		for(Category pcat = cat.catgid.get(ccat.parent); pcat != null; pcat = cat.catgid.get(pcat.parent))
		    catp = pcat.name + " / " + catp;
		clbl = new Img(btns.c.add(25, -25), textf.render(catp, Button.defcol).tex(), this);
		bbtn = new IButton(clbl.c.add(-25, 0).add(new Coord(25, clbl.sz.y).sub(Utils.imgsz(Window.lbtni[0])).div(2)), this,
				   Window.lbtni[0], Window.lbtni[1], Window.lbtni[2]) {
			public void click() {
			    point(ccat.parent);
			}
		    };
	    }
	    List<OButton> nbtns = new ArrayList<>();
	    int x = 0, y = 0;
	    for(Category sub : cat.catgs) {
		if(sub.parent == catg) {
		    nbtns.add(new CategoryButton(new Coord(x, y).mul(bsz.add(10, 10)), bsz, btns.cont, sub));
		    if(++y > 1) {
			x++;
			y = 0;
		    }
		}
	    }
	    for(Offer offer : cat.offers) {
		if(offer.category == catg) {
		    nbtns.add(new OfferButton(new Coord(x, y).mul(bsz.add(10, 10)), bsz, btns.cont, offer));
		    if(++y > 1) {
			x++;
			y = 0;
		    }
		}
	    }
	    if(nbtns.size() <= 4) {
		linebtns(btns.cont, nbtns, 0);
	    } else if(nbtns.size() <= 8) {
		int fn = (nbtns.size() + 1) / 2;
		linebtns(btns.cont, nbtns.subList(0, fn), 0);
		linebtns(btns.cont, nbtns.subList(fn, nbtns.size()), bsz.y + 10);
	    }
	    btns.cont.update();
	    btns.bar.ch(-btns.bar.val);
	}
    }

    public class Viewer extends Widget {
	public final Offer offer;
	public final Widget back;
	public final Cart cart;
	private Defer.Future<Object[]> status;
	private Tex rimg;

	public Viewer(Offer offer, Widget back, Cart cart) {
	    super(Coord.z, Store.this.asz, Store.this);
	    this.offer = offer;
	    this.back = back;
	    this.cart = cart;
	    this.status = Defer.later(() -> fetch("validate", "offer", offer.id, "ver", offer.ver));
	    Widget prev = new Img(new Coord(25, 175), textf.render(offer.name, Button.defcol).tex(), this);
	    new IButton(new Coord(0, 175).add(new Coord(25, prev.sz.y).sub(Utils.imgsz(Window.lbtni[0])).div(2)), this,
			Window.lbtni[0], Window.lbtni[1], Window.lbtni[2]) {
		public void click() {
		    back();
		}
	    };
	    prev = new Img(new Coord(0, 175), textf.render(offer.price.toString(), Button.defcol).tex(), this);
	    prev.c = new Coord(500 - prev.sz.x, prev.c.y);
	    if(offer.desc != null) {
		RichTextBox dbox = new RichTextBox(new Coord(0, 200), new Coord(500, 200), this, offer.desc, descfnd);
		dbox.bg = null;
	    }
	    back.hide();
	}

	private void back() {
	    ui.destroy(this);
	    back.show();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(this.status != null) {
		Object[] status;
		try {
		    status = this.status.get();
		} catch(Loading l) {
		    return;
		} catch(Defer.DeferredException e) {
		    status = new Object[] {"status", "ok"};
		}
		this.status = null;
		Map<String, Object> stat = Utils.mapdecf(status, String.class, Object.class);
		if(Utils.eq(stat.get("status"), "ok")) {
		    NumberEntry num = null;
		    if(!offer.singleton) {
			new Label(new Coord(300, 430), this, "Quantity:");
			num = new NumberEntry(new Coord(350, 427), 25, this, 1);
		    }
		    NumberEntry fnum = num;
		    new Button(new Coord(400, 425), 100, this, "Add to cart") {
			public void click() {
			    if(offer.singleton) {
				cart.byoffer(offer, true).num = 1;
			    } else {
				int n = fnum.get();
				if(n <= 0) {
				    Cart.Item item = cart.byoffer(offer, false);
				    if(item != null)
					cart.remove(item);
				} else {
				    cart.byoffer(offer, true).num = Math.min(n, 99);
				}
			    }
			    back();
			}
		    };
		} else if(Utils.eq(stat.get("status"), "invalid")) {
		    new Img(new Coord(200, 400), Text.std.renderwrap((String)stat.get("msg"), new Color(255, 64, 64), 200).tex(), this);
		} else if(Utils.eq(stat.get("status"), "obsolete")) {
		    ui.destroy(this);
		    ui.destroy(back);
		    new Loader();
		}
	    }
	}

	public void draw(GOut g) {
	    if(offer.img != null) {
		try {
		    if(rimg == null)
			rimg = new TexI(offer.img.get());
		    g.image(rimg, new Coord(sz.x - 25 - rimg.sz().x, 300 - (rimg.sz().y / 2)));
		} catch(Loading l) {
		}
	    }
	    super.draw(g);
	}
    }

    public static class IOError extends RuntimeException {
	public IOError(Throwable cause) {
	    super(cause);
	}
    }

    public static Object[] encode(Cart cart) {
	List<Object> cbuf = new ArrayList<>();
	for(Cart.Item item : cart.items)
	    cbuf.add(new Object[] {"offer", item.offer.id, "ver", item.offer.ver, "num", item.num});
	return(cbuf.toArray(new Object[0]));
    }

    private URL fun(String fun, String... pars) {
	try {
	    URL ret = base.resolve(fun).toURL();
	    if(pars.length > 0)
		ret = Utils.urlparam(ret, pars);
	    return(ret);
	} catch(IOException e) {
	    throw(new IOError(e));
	}
    }

    private URLConnection req(URL url) {
	try {
	    URLConnection conn;
	    if(url.getProtocol().equals("https"))
		conn = ssl.connect(url);
	    else
		conn = url.openConnection();
	    Message auth = new Message(0);
	    auth.addstring(ui.sess.username);
	    auth.addbytes(ui.sess.sesskey);
	    conn.setRequestProperty("Authorization", "Haven " + Utils.base64enc(auth.blob));
	    return(conn);
	} catch(IOException e) {
	    throw(new IOError(e));
	}
    }

    private URLConnection req(String fun, String... pars) {
	return(req(fun(fun, pars)));
    }

    private void send(URLConnection conn, Object[] data) {
	Message buf = new Message(0);
	buf.addlist(data);
	conn.setDoOutput(true);
	try(OutputStream fp = conn.getOutputStream()) {
	    fp.write(buf.blob);
	} catch(IOException e) {
	    throw(new IOError(e));
	}
    }

    private Object[] fetch(URLConnection conn) {
	try(InputStream fp = conn.getInputStream()) {
	    if(!conn.getContentType().equals("application/x-haven-ttol"))
		throw(new IOException("unexpected content-type: " + conn.getContentType()));
	    return(new Message(0, Utils.readall(fp)).list());
	} catch(IOException e) {
	    throw(new IOError(e));
	}
    }

    private Object[] fetch(String fun, String... pars) {
	return(fetch(req(fun, pars)));
    }

    private Defer.Future<BufferedImage> image(URI uri) {
	return(Defer.later(() -> {
		    try {
			try(InputStream fp = req(uri.toURL()).getInputStream()) {
			    return(ImageIO.read(fp));
			}
		    } catch(IOException e) {
			throw(new RuntimeException(e));
		    }
		}, false));
    }

    private Catalog catalog() {
	List<Offer> offers = new ArrayList<>();
	List<Category> catgs = new ArrayList<>();
	Object[] ls = fetch("offers");
	int order = 0;
	Price credit = null;
	for(Object item : ls) {
	    Object[] enc = (Object[])item;
	    String type = (String)enc[0];
	    if(type.equals("offer")) {
		String id = (String)enc[1];
		String ver = (String)enc[2];
		Offer offer = new Offer(id, ver);
		offer.sortkey = order++;
		for(int a = 3; a < enc.length; a += 2) {
		    String key = (String)enc[a];
		    Object val = enc[a + 1];
		    switch(key) {
		    case "name":    offer.name      = (String)val; break;
		    case "desc":    offer.desc      = (String)val; break;
		    case "img":     offer.img       = image(base.resolve((String)val)); break;
		    case "cat":     offer.category  = ((String)val).intern(); break;
		    case "price":   offer.price     = Price.parse((Object[])val);; break;
		    case "monad":   offer.singleton = true;; break;
		    }
		}
		offers.add(offer);
	    } else if(type.equals("cat")) {
		String id = ((String)enc[1]).intern();
		Category catg = new Category(id);
		catg.sortkey = order++;
		for(int a = 2; a < enc.length; a += 2) {
		    String key = (String)enc[a];
		    Object val = enc[a + 1];
		    switch(key) {
		    case "name": catg.name   = (String)val; break;
		    case "cat":  catg.parent = ((String)val).intern(); break;
		    }
		}
		catgs.add(catg);
	    } else if(type.equals("error")) {
		throw(new MessageError((String)enc[1]));
	    } else if(type.equals("credit")) {
		credit = Price.parse((Object[])enc[1]);
	    }
	}
	Collections.sort(offers, (a, b) -> (a.sortkey - b.sortkey));
	Collections.sort(catgs, (a, b) -> (a.sortkey - b.sortkey));
	return(new Catalog(offers, catgs, credit));
    }
}
