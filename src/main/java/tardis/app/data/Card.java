package tardis.app.data;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import pt.unl.fct.di.novasys.babel.crdts.utils.datatypes.StringType;

public class Card {
    private static final String[] names = { "Bulbasaur",
            "Ivysaur",
            "Venusaur",
            "Charmander",
            "Charmeleon",
            "Charizard",
            "Squirtle",
            "Wartortle",
            "Blastoise",
            "Caterpie",
            "Metapod",
            "Butterfree",
            "Weedle",
            "Kakuna",
            "Beedrill",
            "Pidgey",
            "Pidgeotto",
            "Pidgeot",
            "Rattata",
            "Raticate",
            "Spearow",
            "Fearow",
            "Ekans",
            "Arbok",
            "Pikachu",
            "Raichu",
            "Sandshrew",
            "Sandslash",
            "Nidoran♀",
            "Nidorina",
            "Nidoqueen",
            "Nidoran♂",
            "Nidorino",
            "Nidoking",
            "Clefairy",
            "Clefable",
            "Vulpix",
            "Ninetales",
            "Jigglypuff",
            "Wigglytuff",
            "Zubat",
            "Golbat",
            "Oddish",
            "Gloom",
            "Vileplume",
            "Paras",
            "Parasect",
            "Venonat",
            "Venomoth",
            "Diglett",
            "Dugtrio",
            "Meowth",
            "Persian",
            "Psyduck",
            "Golduck",
            "Mankey",
            "Primeape",
            "Growlithe",
            "Arcanine",
            "Poliwag",
            "Poliwhirl",
            "Poliwrath",
            "Abra",
            "Kadabra",
            "Alakazam",
            "Machop",
            "Machoke",
            "Machamp",
            "Bellsprout",
            "Weepinbell",
            "Victreebel",
            "Tentacool",
            "Tentacruel",
            "Geodude",
            "Graveler",
            "Golem",
            "Ponyta",
            "Rapidash",
            "Slowpoke",
            "Slowbro",
            "Magnemite",
            "Magneton",
            "Farfetch'd",
            "Doduo",
            "Dodrio",
            "Seel",
            "Dewgong",
            "Grimer",
            "Muk",
            "Shellder",
            "Cloyster",
            "Gastly",
            "Haunter",
            "Gengar",
            "Onix",
            "Drowzee",
            "Hypno",
            "Krabby",
            "Kingler",
            "Voltorb",
            "Electrode",
            "Exeggcute",
            "Exeggutor",
            "Cubone",
            "Marowak",
            "Hitmonlee",
            "Hitmonchan",
            "Lickitung",
            "Koffing",
            "Weezing",
            "Rhyhorn",
            "Rhydon",
            "Chansey",
            "Tangela",
            "Kangaskhan",
            "Horsea",
            "Seadra",
            "Goldeen",
            "Seaking",
            "Staryu",
            "Starmie",
            "Mr. Mime",
            "Scyther",
            "Jynx",
            "Electabuzz",
            "Magmar",
            "Pinsir",
            "Tauros",
            "Magikarp",
            "Gyarados",
            "Lapras",
            "Ditto",
            "Eevee",
            "Vaporeon",
            "Jolteon",
            "Flareon",
            "Porygon",
            "Omanyte",
            "Omastar",
            "Kabuto",
            "Kabutops",
            "Aerodactyl",
            "Snorlax",
            "Articuno",
            "Zapdos",
            "Moltres",
            "Dratini",
            "Dragonair",
            "Dragonite",
            "Mewtwo",
            "Mew" };

    private static final int NORMAL_BASE_SIZE = 1_000_000;
    private static final int SHINY_MAX_SIZE = 5_000_000;

    private final String id;
    private final String name;
    private final String description;
    private final boolean shiny;
    private final byte[] imageData;

    public Card(String id, String name, String description, boolean shiny, byte[] image) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.shiny = shiny;
        this.imageData = image;
    }

    public Card(String id, String name, String description, boolean shiny, int imageSize) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.shiny = shiny;
        this.imageData = randomBytes(imageSize);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isShiny() {
        return shiny;
    }

    public byte[] getImageData() {
        return imageData;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s%s - %s [%s]",
                id,
                shiny ? "✨ " : "",
                name,
                description,
                Base64.getEncoder().encodeToString(imageData));
    }

    public String toStringShort() {
        return String.format("[%s] %s%s - %s (%dB)",
                id,
                shiny ? "✨ " : "",
                name,
                description,
                imageData.length);
    }

    // Factory for random cards
    public static Card randomCard() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        String name = names[rng.nextInt(names.length)];
        String description = "Card of " + name;

        boolean shiny = rng.nextDouble() < 0.05;

        int imageSize;
        if (shiny) {
            imageSize = rng.nextInt(NORMAL_BASE_SIZE, SHINY_MAX_SIZE + 1);
        } else {
            int fluctuation = (int) (NORMAL_BASE_SIZE * 0.2);
            int variation = rng.nextInt(-fluctuation, fluctuation);
            imageSize = NORMAL_BASE_SIZE + variation;
        }

        String id = UUID.randomUUID().toString();

        return new Card(id, name, description, shiny, randomBytes(imageSize));
    }

    public static String extractId(StringType elem) {
        return elem.getValue().split(" ")[0];
    }

    public static String preview(String val) {
		// Split only first 4 spaces (ID, shiny?, name, description + Base64)
		String[] parts = val.split(" ", 4);

		String id = parts[0].replace("[", "").replace("]", "");
		boolean shiny = parts[1].equals("✨");
		String name = shiny ? parts[2] : parts[1];

		// Extract Base64 part
		int b64Start = val.lastIndexOf('[');
		int b64End = val.lastIndexOf(']');
		String b64 = "?";
		if (b64Start >= 0 && b64End > b64Start) {
			String fullB64 = val.substring(b64Start + 1, b64End);
			int len = fullB64.length();
			b64 = fullB64.substring(0, Math.min(8, len)) + "..." + fullB64.substring(Math.max(len - 8, 0));
		}

		return "[" + id.substring(0, Math.min(8, id.length())) + "] "
				+ (shiny ? "✨ " : "")
				+ name + " "
				+ "img[" + b64 + "]";
    }
}
