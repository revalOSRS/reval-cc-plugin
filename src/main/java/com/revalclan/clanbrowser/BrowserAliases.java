package com.revalclan.clanbrowser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Collection Log command shortcuts, resolved against the backend's current catalogue. */
final class BrowserAliases {
    private static final String[][] COMMON = {
        {"Abyssal Sire", "sire"},
        {"Alchemical Hydra", "hydra"},
        {"Bryophyta", "bryo"},
        {"Barrows Chests", "barrows"},
        {"Cerberus", "cerb"},
        {"Chaos Elemental", "chaos ele"},
        {"Commander Zilyana", "sara", "saradomin", "zilyana", "zily"},
        {"Corporeal Beast", "corp"},
        {"Crazy Archaeologist", "crazy arch"},
        {"Duke Sucellus", "duke", "succ", "succelus", "ugly", "succellus", "duke succellus"},
        {"Giant Mole", "mole"},
        {"General Graardor", "bando", "bandos", "graardor"},
        {"Grotesque Guardians", "dusk", "dawn", "gargs", "ggs", "gg"},
        {"Hespori", "farming guild", "uim"},
        {"Kalphite Queen", "kq"},
        {"King Black Dragon", "kbd"},
        {"Kraken", "cave kraken"},
        {"Kree'arra", "arma", "kree", "kreearra", "armadyl"},
        {"K'ril Tsutsaroth", "zammy", "zamorak", "kril", "kril trutsaroth", "kril tsutsaroth"},
        {"The Leviathan", "leviathan", "levi", "lev"},
        {"Obor", "hill giant"},
        {"Phantom Muspah", "mus", "muspah", "pm", "the grumbler", "phantom"},
        {"Sarachnis", "saracha", "sarac", "saracnis"},
        {"Scorpia", "scorpa"},
        {"Scurrius", "scur", "scurius", "rat"},
        {"Skotizo", "skotizos", "totems", "totem"},
        {"Tempoross", "tempoross", "temp", "fishtodt", "fishingtodt"},
        {"Thermonuclear Smoke Devil", "smoke devil", "smoke devils", "thermy", "thermey"},
        {"Vardorvis", "vard", "vardo"},
        {"Vorkath", "vork", "vorki"},
        {"Wintertodt", "wt", "todt"},
        {"The Whisperer", "whisperer", "whisp", "wisp"},
        {"Zalcano", "zalc"},
        {"Zulrah", "zul", "profit snake", "bjs"},
        {"Amoxliatl", "amox"},
        {"Deranged Archaeologist", "deranged arch"},
        {"The Hueycoatl", "huey", "hueycoatl"},
        {"Doom of Mokhaiotl", "dom", "doom"},
        {"Chambers of Xeric", "cox", "xeric", "chambers", "olm", "raids"},
        {"Theatre of Blood", "tob", "theatre", "verzik", "verzik vitur", "raids 2"},
        {"Tombs of Amascut", "toa", "tomb", "tombs", "tomb of amascut", "amascut", "warden", "wardens", "raids 3"},
        {"Guardians of the Rift", "rifts closed"}
    };

    private static final String[][] PAGES = {
        {"Callisto and Artio", "artio", "art", "cal", "callisto"},
        {"Dagannoth Kings", "dks"},
        {"The Fight Caves", "jad", "fight caves", "fc"},
        {"The Gauntlet", "gaunt", "gauntlet", "cgaunt", "cgauntlet", "the corrupted gauntlet", "cg"},
        {"The Inferno", "zuk", "inferno"},
        {"The Nightmare", "nightmare", "nm", "tnm", "nmare", "pnm", "phosani", "phosanis", "phosani nm", "phosani nightmare", "phosanis nightmare"},
        {"Venenatis and Spindel", "spin", "spindel", "vene", "venenatis"},
        {"Vet'ion and Calvar'ion", "calv", "calvarion", "calvar ion", "calvar'ion", "vetion", "vet ion", "vet'ion", "veti"},
        {"Chambers of Xeric", "cox cm", "xeric cm", "chambers cm", "olm cm", "raids cm", "chambers of xeric - challenge mode"},
        {"Theatre of Blood", "theatre of blood: story mode", "tob sm", "tob story mode", "tob story", "theatre of blood: entry mode", "tob em", "tob entry mode", "tob entry", "theatre of blood: hard mode", "tob cm", "tob hm", "tob hard mode", "tob hard", "hmt"},
        {"Beginner clues", "begs", "beg clues", "beginners", "beginner clues", "beginner clue", "clues beg", "clues beginner", "clue beg", "clue beginner"},
        {"Easy clues", "easy clues", "easy clue", "easies", "clues easy", "clue easy"},
        {"Medium clues", "meds", "med", "mediums", "medium clues", "medium clue", "clues med", "clues medium", "clue med", "clue medium"},
        {"Hard clues", "hards", "hard clues", "hard clue", "clue hard", "clues hard"},
        {"Master clues", "masters", "master clues", "master clue", "clue master", "clues master"},
        {"Elite clues", "elites", "elite clues", "elite clue", "clue elite", "clues elite"},
        {"Hard clues - rare rewards", "hards rare", "hard clues rare", "hard clue rare", "hards rares", "hard clues rares", "hard clue rares", "rare hards", "rare hard"},
        {"Elite clues - rare rewards", "elites rare", "elite clues rare", "elite clue rare", "elites rares", "elite clues rares", "elite clue rares", "rare elite", "rare elites"},
        {"Master clues - rare rewards", "master treasure trails rare", "masters rare", "master clues rare", "master clue rare", "master treasure trails rares", "masters rares", "master clues rares", "master clue rares", "rare master", "rare masters"},
        {"Shared Treasure Trail Rewards", "shared rewards", "shared clue", "shared clues", "shared"},
        {"Barbarian Assault", "ba", "barb"},
        {"Brimhaven Agility Arena", "brimhaven agility", "brimhaven", "brim agility", "brim"},
        {"Castle Wars", "cw", "cwars"},
        {"Fishing Trawler", "trawler"},
        {"Giants' Foundry", "giants foundry", "foundry", "gf"},
        {"Gnome Restaurant", "gnome", "restaurant"},
        {"Guardians of the Rift", "guardian of the rift", "gotr", "grift", "grifting", "guardians", "runetodt"},
        {"Hallowed Sepulchre", "hs", "sepc", "sepulchre"},
        {"Last Man Standing", "lms"},
        {"Magic Training Arena", "mta", "magic arena"},
        {"Mahogany Homes", "mah homes", "mahogany home", "mh"},
        {"Pest Control", "pc"},
        {"Rogues' Den", "rogues den", "rogue den", "rogues", "rogue"},
        {"Shades of Mort'ton", "shades of mortton", "shade of mortton", "shades", "shade", "mort ton", "mortton"},
        {"Soul Wars", "soul war", "sw"},
        {"Temple Trekking", "trekking", "temple trek"},
        {"Tithe Farm", "tithe"},
        {"Trouble Brewing", "brewing"},
        {"Volcanic Mine", "vm", "volc mine"},
        {"Aerial Fishing", "aerial fish", "aerial"},
        {"All Pets", "all pets", "pets", "pet"},
        {"Champion's Challenge", "champions challenge", "champion challenge", "champion scrolls", "champ scrolls"},
        {"Elder Chaos Druids", "chaos druid", "elder druids", "elder druid", "elder"},
        {"Chompy Bird Hunting", "chompy", "chompies", "bird hunting"},
        {"Creature Creation", "tower of life"},
        {"Cyclopes", "defenders", "defender"},
        {"Fossil Island Notes", "fossil island note", "fossil island"},
        {"Glough's Experiments", "gloughs experiments", "gloughs experiment", "glough experiments", "glough experiment", "demonics", "demonic gorilla", "demonic gorillas"},
        {"Monkey Backpacks", "monkey backpacks", "monkey backpack", "ape atoll", "backpack", "backpacks"},
        {"Motherlode Mine", "motherlode mine", "motherlode", "mlm", "mm"},
        {"My Notes", "my notes", "notes", "note"},
        {"Random Events", "random event", "random", "randoms"},
        {"Revenants", "revs"},
        {"Rooftop Agility", "rooftop", "rooftops", "agility", "graceful"},
        {"Shayzien Armour", "shayzien"},
        {"Shooting Stars", "shooting star", "star mining", "ss", "stars"},
        {"Skilling Pets", "skilling pet", "skill pets", "skill pet", "pets skill", "pet skill", "pets skilling", "pet skilling"},
        {"Slayer", "slay"},
        {"TzHaar", "tz haar", "tz", "tzh"},
        {"Miscellaneous", "misc"},
        {"Moons of Peril", "lunar chest", "lunar chests", "perilous moon", "perilous moons"},
        {"Fortis Colosseum", "sol", "sol heredit", "colo", "colosseum"},
        {"Tombs of Amascut", "toa entry", "toa entry mode", "tombs of amascut - entry", "toa expert", "toa expert mode", "tombs of amascut - expert"},
        {"Hunter Guild", "hunter rumours", "hunterrumour", "hunter contract", "hunter contracts", "hunter tasks", "hunter task", "rumours", "rumour"}
    };

    private static final String[][] BOSSES = {
        {"Callisto", "cal"},
        {"Artio", "art"},
        {"Vet'ion", "vetion", "veti", "vet ion"},
        {"Calvar'ion", "calv", "calvarion", "calvar ion"},
        {"Venenatis", "vene"},
        {"Spindel", "spin"},
        {"Nightmare", "nm", "tnm", "nmare", "the nightmare"},
        {"Phosani's Nightmare", "pnm", "phosani", "phosanis", "phosani nm", "phosani nightmare", "phosanis nightmare"},
        {"The Gauntlet", "gaunt", "gauntlet"},
        {"The Corrupted Gauntlet", "cg", "cgaunt", "cgauntlet", "corrupted gauntlet"},
        {"TzTok-Jad", "jad", "fight caves", "fc", "tzhaar fight cave", "the fight caves"},
        {"TzKal-Zuk", "zuk", "inferno", "the inferno"},
        {"Sol Heredit", "sol", "colo", "colosseum"},
        {"Moons of Peril", "perilous moon", "perilous moons"},
        {"Lunar Chest", "lunar chests"},
        {"Chambers Of Xeric (CM)", "cox cm", "xeric cm", "chambers cm", "olm cm", "raids cm", "chambers of xeric challenge mode", "chambers of xeric - challenge mode"},
        {"Theatre Of Blood Entry Mode", "tob sm", "tob story mode", "tob story", "tob entry mode", "tob em", "tob entry", "theatre of blood story mode"},
        {"Theatre Of Blood (HM)", "tob cm", "tob hm", "tob hard mode", "tob hard", "hmt", "theatre of blood hard mode"},
        {"Tombs Of Amascut Entry Mode", "toa entry", "toa entry mode", "tombs of amascut - entry"},
        {"Tombs of Amascut (Expert Mode)", "toa expert", "toa expert mode", "tombs of amascut - expert"},
        {"Dagannoth Supreme", "supreme"},
        {"Dagannoth Rex", "rex"},
        {"Dagannoth Prime", "prime"},
        {"Duke Sucellus Awakened", "duke awakened"},
        {"Leviathan Awakened", "levi awakened", "the leviathan awakened"},
        {"Vardorvis Awakened", "vard awakened"},
        {"Whisperer Awakened", "whisp awakened", "wisp awakened", "the whisperer awakened"}
    };

    private BrowserAliases() {}

    static void apply(BrowserModels.Catalogue catalogue) {
        apply(catalogue.pages, COMMON, PAGES);
        apply(catalogue.bosses, COMMON, BOSSES);
    }

    private static void apply(List<? extends BrowserModels.Choice> choices, String[][]... tables) {
        Map<String, BrowserModels.Choice> targets = new HashMap<>();
        for (BrowserModels.Choice choice : choices) {
            targets.putIfAbsent(BrowserCommand.normalize(choice.value), choice);
            targets.putIfAbsent(BrowserCommand.normalize(choice.label), choice);
        }
        Map<String, BrowserModels.Choice> owners = new HashMap<>(targets);
        for (BrowserModels.Choice choice : choices) {
            if (choice.aliases == null) choice.aliases = new ArrayList<>();
            for (String alias : choice.aliases) owners.putIfAbsent(BrowserCommand.normalize(alias), choice);
        }
        for (String[][] table : tables) for (String[] group : table) {
            BrowserModels.Choice target = targets.get(BrowserCommand.normalize(group[0]));
            if (target == null) continue;
            for (int i = 1; i < group.length; i++) {
                // Backend aliases and real category names take precedence over defaults.
                if (owners.putIfAbsent(BrowserCommand.normalize(group[i]), target) == null) target.aliases.add(group[i]);
            }
        }
    }
}
