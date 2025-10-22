package tardis.app.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

public class Card {
	private static final Random rand = new Random(42);
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

    private static final int NORMAL_BASE_SIZE_BYTES = 1_000;
    private static final int SHINY_SIZE_MULTIPLIER = 5;

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
        rand.nextBytes(bytes);
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
        for (int i = 0 ; i<names.length;i++) {
        if (names[i].equals(name)) return String.format("'%d'", i);
        }
        return "";
        // return String.format("[%s] %s%s - %s (%dB)",
        //         String.format("%s...%s", id.substring(0, 3), id.substring(id.length() - 3)),
        //         shiny ? "✨ " : "",
        //         name,
        //         description,
        //         imageData.length);
    }

    // Factory for random cards
    public static Card randomCard() {
        // ThreadLocalRandom rng = ThreadLocalRandom.current();

        String name = names[rand.nextInt(names.length)];
        String description = "Card of " + name;

        boolean shiny = rand.nextDouble() < 0.05;

        int imageSize;
        if (shiny) {
            imageSize = rand.nextInt(NORMAL_BASE_SIZE_BYTES, NORMAL_BASE_SIZE_BYTES * SHINY_SIZE_MULTIPLIER + 1);
        } else {
            int fluctuation = (int) (NORMAL_BASE_SIZE_BYTES * 0.2);
            int variation = rand.nextInt(-fluctuation, fluctuation);
            imageSize = NORMAL_BASE_SIZE_BYTES + variation;
        }

        String id = UUID.randomUUID().toString();

        return new Card(id, name, description, shiny, randomBytes(imageSize));
    }

    public byte[] toBytes() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(baos)) {

            out.writeUTF(id);
            out.writeUTF(name);
            out.writeUTF(description);
            out.writeBoolean(shiny);
            out.writeInt(imageData.length);
            out.write(imageData);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Card", e);
        } catch (Exception e) {
            System.err.println("Caught unexpected exception in Card.toBytes");
            System.err.println(e);
            throw e;
        }
    }

    public static Card fromBytes(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String id = in.readUTF();
            String name = in.readUTF();
            String description = in.readUTF();
            boolean shiny = in.readBoolean();
            int len = in.readInt();
            byte[] image = new byte[len];
            in.readFully(image);
            return new Card(id, name, description, shiny, image);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize Card", e);
        } catch (Exception e) {
            System.err.println("Caught unexpected exception in Card.fromBytes");
            System.err.println(e);
            throw e;
        }
    }

    @Override
    public boolean equals(Object obj) {
        return this.id.equals(((Card) obj).getId());
    }
}
