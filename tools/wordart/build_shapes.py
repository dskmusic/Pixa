#!/usr/bin/env python3
"""
Genera app/src/main/assets/wordart/shapes.json para la herramienta "Nube de formas".

Descarga los SVG de los repositorios oficiales (Phosphor MIT, Tabler MIT, Lucide ISC,
Material Symbols Apache 2.0), los convierte a path data y construye el indice bilingue
(nombre + etiquetas en espanol e ingles). Solo se conservan los iconos cuyo nombre contiene
alguna palabra del diccionario de abajo, asi ninguna forma se queda sin etiquetas en espanol.

Uso (desde la raiz del proyecto):
    python tools/wordart/build_shapes.py
Opciones:  --cache DIR   guarda las descargas (por defecto tools/wordart/.cache)
"""
import argparse
import io
import json
import re
import sys
import tarfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/wordart/shapes.json"

CATS = {
    "animals": ("animales", "animals"),
    "nature": ("naturaleza", "nature"),
    "food": ("comida", "food"),
    "music": ("musica", "music"),
    "sport": ("deporte", "sports"),
    "symbols": ("simbolos", "symbols"),
    "shapes": ("formas", "shapes"),
    "people": ("personas", "people"),
    "transport": ("transporte", "transport"),
    "objects": ("objetos", "objects"),
    "emoji": ("emojis y caras", "emoji faces"),
}

# categoria ; claves del nombre del icono (ingles) ; etiquetas en espanol ; sinonimos en ingles
DICT = """
animals;cat;gato,felino,mascota,minino;kitty,feline,pet
animals;dog;perro,can,mascota,cachorro;puppy,pet,canine
animals;paw,pets,pawprint;huella,pata,mascota,animal;paw print,pet
animals;bird;pajaro,ave;fly,wings
animals;butterfly;mariposa,insecto;insect
animals;fish;pez,pescado;sea,aquarium
animals;rabbit,bunny;conejo,liebre;hare
animals;horse;caballo,equino;pony
animals;cow;vaca,ganado;cattle,farm
animals;pig;cerdo,puerco,chancho;farm
animals;bug,beetle,ladybug;insecto,bicho,escarabajo,mariquita;insect
animals;spider;arana;web
animals;snake;serpiente,culebra,vibora;reptile
animals;turtle;tortuga;reptile
animals;elephant;elefante;trunk
animals;owl;buho,lechuza;night bird
animals;bat;murcielago;vampire
animals;duck;pato;bird
animals;squirrel;ardilla;rodent
animals;whale;ballena;sea
animals;dolphin;delfin;sea
animals;shark;tiburon;sea
animals;crab;cangrejo;sea
animals;octopus;pulpo;sea
animals;bone;hueso;dog
animals;egg;huevo;
animals;feather;pluma;bird
animals;rat,mouse;raton,rata;rodent
animals;panda;oso panda;bear
animals;bear;oso;
animals;unicorn;unicornio;fantasy
animals;dinosaur,dino;dinosaurio;prehistoric
animals;chicken,rooster;gallina,gallo,pollo;farm
animals;sheep;oveja;farm
animals;deer;ciervo,venado;
animals;monkey;mono;
animals;lion;leon;
animals;giraffe;jirafa;
animals;penguin;pinguino;
animals;frog;rana;
animals;snail;caracol;
animals;bee;abeja,miel;honey
animals;ghost;fantasma;spooky
animals;skull;calavera,craneo;skeleton
nature;tree,trees,park;arbol,bosque,parque;forest
nature;leaf,leaves,eco,plant,plant;hoja,planta,ecologia;green
nature;flower,tulip,rose,daisy,sunflower,blossom;flor,rosa,tulipan,girasol;bloom
nature;sun,sunny;sol,soleado;day
nature;moon;luna;night
nature;star,stars;estrella,estrellas;night
nature;cloud,clouds;nube,nubes;sky
nature;rain,umbrella;lluvia,paraguas;weather
nature;snowflake,snow;copo de nieve,nieve,invierno;winter
nature;lightning,bolt,thunder;rayo,relampago,tormenta;storm
nature;wind;viento,brisa;air
nature;drop,droplet,water;gota,agua;liquid
nature;fire,flame;fuego,llama;hot
nature;mountain,mountains;montana,monte;peak
nature;volcano;volcan;lava
nature;wave,waves;ola,olas,mar;sea
nature;rainbow;arcoiris,arco iris;colors
nature;globe,earth,planet,world;mundo,tierra,planeta,globo;earth
nature;cactus;cactus;desert
nature;clover;trebol,suerte;luck
nature;mushroom;seta,hongo;fungi
nature;palm;palmera;tropical
nature;island;isla;beach
nature;campfire,fire-pit;hoguera,fogata;camp
nature;thermometer;termometro,temperatura;weather
nature;sunrise,sunset;amanecer,atardecer,puesta de sol;dawn
nature;comet,meteor,rocket;cometa,meteorito,cohete;space
nature;atom,flask;atomo,ciencia;science
food;pizza;pizza;italian
food;burger,hamburger;hamburguesa;fast food
food;coffee,cup,mug;cafe,taza;drink
food;tea;te,infusion;drink
food;beer,wine,cocktail,martini,glass;cerveza,vino,copa,coctel;drink
food;cake,birthday-cake,cupcake;tarta,pastel,cumpleanos,magdalena;dessert
food;cookie,donut;galleta,rosquilla,dulce;sweet
food;ice-cream,popsicle;helado,polo;dessert
food;apple;manzana,fruta;fruit
food;cherry,cherries;cereza,cerezas,fruta;fruit
food;lemon,orange,carrot,banana,grapes,pear,strawberry;limon,naranja,zanahoria,platano,uvas,pera,fresa,fruta;fruit
food;pepper,chili;pimiento,chile,picante;spicy
food;bread,croissant;pan,croissant;bakery
food;fish-simple,sushi;sushi,pescado;japanese
food;egg-crack;huevo frito;breakfast
food;fork,knife,utensils,restaurant,cooking-pot,chef-hat;tenedor,cuchillo,cubiertos,restaurante,cocina,chef;kitchen
food;candy,lollipop,chocolate;caramelo,piruleta,chocolate;sweet
food;popcorn,hotdog,taco,sandwich;palomitas,perrito,taco,bocadillo;snack
food;bottle,baby-bottle;botella,biberon;drink
music;music,note,notes,music-notes;musica,nota,notas,melodia;song
music;guitar,guitar-pick;guitarra;strings
music;piano,keyboard-music;piano,teclado;keys
music;microphone,mic;microfono,cantar;sing
music;headphones,headset;auriculares,cascos;listen
music;speaker,volume,speaker-high;altavoz,volumen,sonido;sound
music;drum,drums;bateria,tambor;percussion
music;vinyl,record,disc,cd;vinilo,disco;album
music;radio;radio;broadcast
music;playlist,queue-music,library-music;lista de reproduccion;songs
music;violin,trumpet,saxophone,harp;violin,trompeta,saxofon,arpa;instrument
music;treble-clef,clef;clave de sol;score
music;equalizer,waveform;ecualizador,onda de sonido;audio
sport;soccer,football;futbol,balon;ball
sport;basketball;baloncesto,basket;ball
sport;tennis,racquet;tenis,raqueta;ball
sport;baseball;beisbol;ball
sport;volleyball;voleibol;ball
sport;golf;golf;club
sport;bicycle,bike;bicicleta,ciclismo;cycling
sport;trophy,medal,award;trofeo,medalla,premio,campeon;winner
sport;barbell,dumbbell,fitness,gym;pesas,gimnasio,fitness;workout
sport;boxing,boxing-glove;boxeo,guante;fight
sport;swimming,pool;natacion,piscina;swim
sport;running,run;correr,carrera;jog
sport;skateboard,skiing,snowboarding,surfing,hiking;skate,esqui,snowboard,surf,senderismo;outdoor
sport;target,bullseye,dart;diana,objetivo,dardo;aim
sport;whistle,stopwatch,timer;silbato,cronometro;referee
sport;chess,sports-esports,gamepad;ajedrez,videojuego,mando;game
sport;dice,puzzle,cards;dado,puzzle,cartas,juego;game
symbols;heart,favorite,hearts;corazon,amor,favorito;love
symbols;check,checkmark;visto,correcto,marca;ok
symbols;plus,cross;mas,cruz;add
symbols;crown;corona,rey,reina;king,queen
symbols;shield,security;escudo,seguridad;protect
symbols;lock,key;candado,llave;secure
symbols;flag;bandera;country
symbols;bell,notification;campana,notificacion;alert
symbols;infinity;infinito;endless
symbols;peace,yin-yang,hand-peace;paz,yin yang;calm
symbols;recycle;reciclaje,reciclar;eco
symbols;anchor;ancla;ship
symbols;diamond,gem,jewel;diamante,gema,joya;precious
symbols;sparkle,sparkles,magic,wand,auto-awesome;brillo,chispa,magia,varita;shine
symbols;bookmark,tag,label;marcador,etiqueta;mark
symbols;warning,alert-triangle;aviso,advertencia,peligro;danger
symbols;question,help;pregunta,ayuda;ask
symbols;info,information;informacion;about
symbols;copyright,trademark;derechos de autor;legal
symbols;cube,hexagon,pentagon;cubo,hexagono,pentagono;geometry
symbols;asterisk,hash,at,percent,dollar,euro;asterisco,almohadilla,arroba,porcentaje,dolar;sign
symbols;thumbs-up,thumb-up,like;me gusta,pulgar arriba;approve
symbols;thumbs-down,thumb-down;pulgar abajo;dislike
symbols;flash,power;energia,encendido;power
symbols;light,lightbulb,bulb,idea;bombilla,idea;lamp
symbols;eye,visibility;ojo,vista;look
symbols;fingerprint;huella dactilar;id
symbols;qr,qrcode;codigo qr;scan
symbols;sticker,badge,seal,verified;sello,insignia,verificado,pegatina;stamp
symbols;lifebuoy,life-buoy,lifebelt;salvavidas;rescue
symbols;confetti,celebration,party,party-popper;confeti,fiesta,celebracion;party
symbols;gift,present;regalo,presente;birthday
symbols;ribbon,bow;lazo,cinta;gift
symbols;balloon;globo,globos;party
symbols;mask,theater;mascara,teatro;carnival
symbols;christmas,santa,snowman,candy-cane;navidad,papa noel,muneco de nieve;xmas
shapes;circle,disc,dot;circulo,punto;round
shapes;square,rectangle;cuadrado,rectangulo;box
shapes;triangle;triangulo;
shapes;octagon,stop;octagono;
shapes;oval,ellipse;ovalo,elipse;
shapes;rhombus,kite;rombo,cometa;
shapes;spiral,swirl,loader;espiral,remolino;
shapes;puzzle-piece,jigsaw;pieza de puzzle;
shapes;bubble,speech,chat,message,comment;burbuja,globo de dialogo,mensaje,chat;talk
shapes;cloud-fill,badge-check;nube,insignia;
shapes;tag-fill,shape,shapes;forma,formas;
people;person,user,users,people,man,woman,human;persona,usuario,gente,hombre,mujer,humano;individual
people;baby,child,kid,boy,girl;bebe,nino,nina,infantil;children
people;family,group,team;familia,grupo,equipo;together
people;hand,hands,handshake,fist,clap,wave-hand;mano,manos,apreton,puno,aplauso;gesture
people;heartbeat,stethoscope,doctor,nurse,medical,first-aid;corazon latiendo,estetoscopio,medico,enfermera,primeros auxilios;health
people;graduation,student,school,mortarboard;graduacion,estudiante,escuela,birrete;education
people;couple,wedding,ring,rings;pareja,boda,anillo,anillos;marriage
people;pregnant,stroller;embarazada,carrito de bebe;maternity
people;footprints,shoe,sneaker,boot;huellas,pie,zapato,zapatilla,bota;foot
people;tooth,brain,bone-person,skeleton,lungs;diente,cerebro,pulmones;body
people;head,face-mask,hair,glasses,sunglasses;cabeza,mascarilla,pelo,gafas;accessory
transport;car,sedan,taxi;coche,automovil,carro,taxi;auto
transport;bus,tram,train,subway,metro;autobus,tren,tranvia,metro;public
transport;airplane,plane,flight,jet;avion,vuelo,aeroplano;fly
transport;helicopter,drone;helicoptero,dron;fly
transport;ship,sailboat,boat,ferry,sail;barco,velero,bote,ferry;sea
transport;motorcycle,scooter,moped;moto,motocicleta,patinete;bike
transport;truck,van,tractor,excavator;camion,furgoneta,tractor,excavadora;vehicle
transport;rocket,spaceship,satellite;cohete,nave espacial,satelite;space
transport;road,traffic,traffic-cone,signpost;carretera,trafico,cono,senal;route
transport;gas,fuel,gas-pump;gasolina,combustible,gasolinera;station
transport;compass,map,navigation,pin,location,pin-location;brujula,mapa,navegacion,ubicacion,chincheta;travel
transport;suitcase,luggage,backpack,passport,ticket;maleta,equipaje,mochila,pasaporte,billete,entrada;trip
transport;tent,camping,caravan;tienda de campana,camping,caravana;outdoor
objects;house,home,cabin,castle,tower,building,buildings,city;casa,hogar,cabana,castillo,torre,edificio,ciudad;place
objects;phone,smartphone,mobile,telephone;telefono,movil,celular;call
objects;camera,photo,video,film;camara,foto,video,pelicula;picture
objects;laptop,computer,monitor,desktop,keyboard,mouse-simple;portatil,ordenador,pantalla,teclado,raton;pc
objects;book,books,notebook,newspaper,bookmarks;libro,libros,cuaderno,periodico;read
objects;pencil,pen,paintbrush,palette,brush,paint,eraser,ruler;lapiz,boligrafo,pincel,paleta,brocha,pintura,goma,regla;draw
objects;scissors,knife-utility,hammer,wrench,screwdriver,toolbox,gear,settings;tijeras,martillo,llave inglesa,destornillador,herramientas,engranaje,ajustes;tool
objects;clock,watch,alarm,hourglass,calendar,timer-clock;reloj,alarma,reloj de arena,calendario;time
objects;envelope,mail,email,letter,paper-plane;sobre,correo,carta,avion de papel;message
objects;shopping,cart,bag,basket-shopping,store,shop,wallet,credit-card,money,coins,coin,receipt;compras,carrito,bolsa,tienda,cartera,tarjeta,dinero,monedas,ticket;buy
objects;bed,couch,sofa,chair,table,lamp,bathtub,toilet,door,window;cama,sofa,silla,mesa,lampara,banera,puerta,ventana;furniture
objects;tshirt,shirt,dress,hat,cap,sock,pants,handbag,necklace,crown-simple;camiseta,camisa,vestido,sombrero,gorra,calcetin,pantalones,bolso,collar;clothes
objects;trash,bin,delete,garbage;papelera,basura,borrar;waste
objects;flashlight,candle,lantern,battery,plug,bulb-filled;linterna,vela,farol,bateria,enchufe;light
objects;trophy-star,package,box,archive,folder,file,clipboard,paperclip,pushpin,sticky-note;paquete,caja,archivo,carpeta,documento,portapapeles,clip,nota;office
objects;gamepad,joystick,controller,tv,television,monitor-play,popcorn-cinema;mando,television,cine;entertainment
objects;umbrella-simple,sunglasses-simple,binoculars,telescope,microscope,magnet,magnifying-glass,search;paraguas,binoculares,telescopio,microscopio,iman,lupa,buscar;explore
objects;syringe,pill,pills,bandaids,band-aid,test-tube,thermometer-simple;jeringa,pastilla,pastillas,tirita,tubo de ensayo;medicine
objects;wifi,bluetooth,signal,antenna,cloud-upload,cloud-download,share,link;wifi,bluetooth,senal,antena,compartir,enlace;network
objects;robot,android,alien,ufo,rocket-launch,space;robot,alien,ovni,extraterrestre;sci-fi
objects;lipstick,perfume,nail-polish,mirror,comb,razor;pintalabios,perfume,esmalte,espejo,peine;beauty
objects;paw-print-simple,leash,collar-pet,fish-bowl;correa,pecera;pet
emoji;smiley,smile,happy,mood-happy,laugh,grin,mood;sonrisa,feliz,risa,carita feliz,contento;face
emoji;sad,mood-sad,cry,frown,mood-cry;triste,llorar,carita triste;face
emoji;angry,mood-angry,mad,rage;enfadado,enojado,furioso,carita enfadada;face
emoji;wink,mood-wink,kiss,mood-kid,tongue,mood-tongue;guino,beso,lengua;face
emoji;neutral,mood-neutral,meh,expressionless,mood-empty,mood-nervous,mood-surprised,surprise,shocked;neutral,sorprendido,sorpresa,nervioso;face
emoji;sleep,sleeping,zzz,sick,mood-sick,cool,sunglasses-face,nerd,mood-crazy,crazy,mood-confuzed,confused;durmiendo,dormido,enfermo,guay,loco,confundido;face
emoji;smiley-sticker,mood-smile,sticker-face,emoticon,emoji,face;emoji,emoticono,cara,carita;face
emoji;heart-face,mood-heart,in-love,hearts-eyes;enamorado,ojos de corazon;face
"""

STOP_TOKENS = {"fill", "filled", "bold", "outline", "outlined", "rounded", "sharp", "simple", "thin", "light",
               "duotone", "regular", "off", "slash", "x", "circle-fill"}

HEADERS = {"User-Agent": "pixa-wordart-shape-builder/1.0"}


def fetch(url, cache_dir: Path):
    cache_dir.mkdir(parents=True, exist_ok=True)
    key = re.sub(r"[^A-Za-z0-9]+", "_", url)[-120:]
    f = cache_dir / key
    if f.exists():
        return f.read_bytes()
    print("  descargando", url)
    req = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=180) as r:
        data = r.read()
    f.write_bytes(data)
    return data


def npm_tarball(pkg, cache_dir):
    meta = json.loads(fetch(f"https://registry.npmjs.org/{pkg}/latest", cache_dir / "meta"))
    return fetch(meta["dist"]["tarball"], cache_dir)


def iter_tar(data, pattern):
    rx = re.compile(pattern)
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as tf:
        for m in tf:
            if m.isfile() and rx.search(m.name):
                yield Path(m.name).stem, tf.extractfile(m).read().decode("utf-8", "replace")


def iter_zip(data, pattern):
    rx = re.compile(pattern)
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        for n in zf.namelist():
            if rx.search(n):
                yield Path(n).stem, zf.read(n).decode("utf-8", "replace")


# ---------------------------------------------------------------- SVG -> path data
def num(v, default=0.0):
    try:
        return float(re.match(r"-?[\d.]+(?:e-?\d+)?", str(v)).group(0))
    except Exception:
        return default


def elem_to_d(tag, a):
    if tag == "path":
        return a.get("d", "")
    if tag in ("circle", "ellipse"):
        cx, cy = num(a.get("cx")), num(a.get("cy"))
        rx = num(a.get("r")) if tag == "circle" else num(a.get("rx"))
        ry = rx if tag == "circle" else num(a.get("ry"))
        return (f"M{cx - rx} {cy}a{rx} {ry} 0 1 0 {2 * rx} 0a{rx} {ry} 0 1 0 {-2 * rx} 0z")
    if tag == "rect":
        x, y, w, h = num(a.get("x")), num(a.get("y")), num(a.get("width")), num(a.get("height"))
        rx = num(a.get("rx"), num(a.get("ry")))
        ry = num(a.get("ry"), rx)
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0:
            return f"M{x} {y}h{w}v{h}h{-w}z"
        return (f"M{x + rx} {y}h{w - 2 * rx}a{rx} {ry} 0 0 1 {rx} {ry}v{h - 2 * ry}a{rx} {ry} 0 0 1 {-rx} {ry}"
                f"h{-(w - 2 * rx)}a{rx} {ry} 0 0 1 {-rx} {-ry}v{-(h - 2 * ry)}a{rx} {ry} 0 0 1 {rx} {-ry}z")
    if tag == "line":
        return f"M{num(a.get('x1'))} {num(a.get('y1'))}L{num(a.get('x2'))} {num(a.get('y2'))}"
    if tag in ("polyline", "polygon"):
        pts = re.findall(r"-?[\d.]+", a.get("points", ""))
        if len(pts) < 4:
            return ""
        d = "M" + " ".join(pts[0:2]) + "L" + " ".join(pts[2:])
        return d + ("z" if tag == "polygon" else "")
    return ""


def svg_to_d(svg_text, stroke_mode):
    try:
        root = ET.fromstring(svg_text)
    except ET.ParseError:
        return ""
    parts = []

    def walk(el):
        tag = el.tag.split("}")[-1]
        if tag in ("defs", "clipPath", "mask", "title", "desc", "style"):
            return
        a = el.attrib
        hidden = a.get("fill") == "none" and not stroke_mode
        if tag != "svg" and tag != "g" and not hidden:
            if not (a.get("fill") == "none" and a.get("stroke") in (None, "none")):
                d = elem_to_d(tag, a)
                if d:
                    parts.append(d)
        for c in el:
            walk(c)

    walk(root)
    d = " ".join(parts)
    return re.sub(r"-?\d+\.\d+", lambda m: ("%.2f" % float(m.group(0))).rstrip("0").rstrip("."), d)


# ---------------------------------------------------------------- diccionario / etiquetas
def load_dict():
    entries = []
    for line in DICT.strip().splitlines():
        cat, keys, es, en = (line.split(";") + ["", "", "", ""])[:4]
        entries.append((cat.strip(), [k.strip() for k in keys.split(",") if k.strip()],
                        [t.strip() for t in es.split(",") if t.strip()],
                        [t.strip() for t in en.split(",") if t.strip()]))
    return entries


def name_tokens(name):
    t = re.split(r"[-_]+", name.lower())
    return [x for x in t if x and x not in STOP_TOKENS and not x.isdigit()]


def match_entries(name, entries):
    toks = name_tokens(name)
    joined = "-".join(toks)
    hits = []
    for idx, (cat, keys, es, en) in enumerate(entries):
        for k in keys:
            kt = k.lower().replace("_", "-")
            if "-" in kt:
                ok = kt in joined
            else:
                ok = any(t == kt or t == kt + "s" or t == kt + "es" for t in toks)
            if ok:
                hits.append((idx, k))
                break
    return hits


def build_record(coll, name, d, entries, stroke_w, counts):
    hits = match_entries(name, entries)
    if not hits:
        return None
    first_idx, first_key = hits[0]
    cat = entries[first_idx][0]
    es_tags, en_tags = [], []
    for idx, _ in hits[:3]:
        _, keys, es, en = entries[idx]
        es_tags += es
        en_tags += keys + en
    en_tags += name_tokens(name)
    es_tags.append(CATS[cat][0])
    en_tags.append(CATS[cat][1])
    uniq = lambda xs: list(dict.fromkeys(x.replace("-", " ") for x in xs if x))
    es_u, en_u = uniq(es_tags), uniq(en_tags)
    rec = {
        "i": f"{coll}:{name}",
        "c": coll,
        "k": cat,
        "n": " ".join(name_tokens(name)) or name,
        "e": " ".join(en_u),
        "s": " ".join(es_u),
        "d": d,
    }
    if stroke_w:
        rec["w"] = stroke_w
    rec["_key"] = first_key
    return rec


# ---------------------------------------------------------------- colecciones
SOURCES = [
    # id, etiqueta, cap, descarga, patron, stroke
    ("ph", "Phosphor", 360, lambda c: iter_zip(fetch("https://codeload.github.com/phosphor-icons/core/zip/refs/heads/main", c),
                                                 r"assets/fill/[^/]+-fill\.svg$"), 0),
    ("tb", "Tabler", 220, lambda c: iter_tar(npm_tarball("@tabler/icons", c), r"package/icons/filled/[^/]+\.svg$"), 0),
    ("lu", "Lucide", 170, lambda c: iter_tar(npm_tarball("lucide-static", c), r"package/icons/[^/]+\.svg$"), 2),
    ("ms", "Material Symbols", 200, lambda c: iter_tar(npm_tarball("@material-symbols/svg-400", c),
                                                       r"package/outlined/[^/]+-fill\.svg$"), 0),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=str(Path(__file__).parent / ".cache"))
    args = ap.parse_args()
    cache = Path(args.cache)
    entries = load_dict()
    out, seen_d = [], set()
    for coll, label, cap, getter, stroke_w in SOURCES:
        print(f"[{label}]")
        cands = []
        try:
            icons = sorted(getter(cache), key=lambda x: x[0])
        except Exception as e:  # una coleccion caida no tumba el resto
            print("  ERROR:", e)
            continue
        for name, svg in icons:
            d = svg_to_d(svg, bool(stroke_w))
            if not d or d in seen_d:
                continue
            clean = re.sub(r"-fill$", "", name) if coll in ("ph", "ms") else name
            rec = build_record(coll, clean, d, entries, stroke_w, None)
            if rec:
                cands.append(rec)
        # Reparto equilibrado: nombres simples primero y turnos entre categorias,
        # con un maximo de 4 iconos por palabra clave para que haya variedad.
        groups = {}
        for r in sorted(cands, key=lambda r: (len(r["n"].split()), r["n"])):
            groups.setdefault(r["k"], []).append(r)
        per_key, n, progress = {}, 0, True
        while n < cap and progress:
            progress = False
            for cat in list(groups):
                while groups[cat]:
                    r = groups[cat].pop(0)
                    if per_key.get((coll, r["_key"]), 0) >= 4:
                        continue
                    per_key[(coll, r["_key"])] = per_key.get((coll, r["_key"]), 0) + 1
                    seen_d.add(r["d"])
                    out.append(r)
                    n += 1
                    progress = True
                    break
                if n >= cap:
                    break
        print(f"  {n} formas")
    for r in out:
        r.pop("_key", None)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(f"\nTotal {len(out)} formas -> {OUT} ({OUT.stat().st_size // 1024} KB)")
    missing = [r["i"] for r in out if not r["s"].strip()]
    if missing:
        sys.exit("Formas sin etiquetas en espanol: " + ", ".join(missing[:10]))


if __name__ == "__main__":
    main()
