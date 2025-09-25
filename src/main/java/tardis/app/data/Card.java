package tardis.app.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
        return String.format("[%s] %s%s - %s (image %dB)",
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
        int imageSize = shiny ? 1024 : 128;

        // Random unique ID (UUID works, or any string)
        String id = UUID.randomUUID().toString();

        return new Card(id, name, description, shiny, imageSize);
    }

    public static String extractId(StringType elem) {
        return elem.getValue().split(" ")[0];
    }

    public byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeUTF(this.id);
            dos.writeUTF(this.name);
            dos.writeUTF(this.description);
            dos.writeBoolean(this.shiny);

            dos.writeInt(this.imageData.length);
            dos.write(this.imageData);

            return baos.toByteArray();
        }
    }

    public static Card fromBytes(byte[] data) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream dis = new DataInputStream(bais)) {

            String id = dis.readUTF();
            String name = dis.readUTF();
            String description = dis.readUTF();
            boolean shiny = dis.readBoolean();

            int length = dis.readInt();
            byte[] bytes = new byte[length];
            dis.readFully(bytes);

            return new Card(id, name, description, shiny, bytes);
        }
    }

}
