package tardis.app.data;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Card {
    private final String id;
    private final String name;
    private final String description;
    private final boolean shiny;
    private final ByteBuffer imageData;

    public Card(String id, String name, String description, boolean shiny, int imageSize) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.shiny = shiny;
        this.imageData = randomBytes(imageSize);
    }

    private static ByteBuffer randomBytes(int size) {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        return ByteBuffer.wrap(bytes);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isShiny() { return shiny; }
    public ByteBuffer getImageData() { return imageData.asReadOnlyBuffer(); }

    @Override
    public String toString() {
        return String.format("[%s] %s%s - %s (image %dB)",
                id,
                shiny ? "✨ " : "",
                name,
                description,
                imageData.capacity());
    }

    // Factory for random cards
    public static Card randomCard() {
        String[] names = {"Pikachu", "Charmander", "Squirtle", "Bulbasaur", "Eevee"};
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        String name = names[rng.nextInt(names.length)];
        String description = "Card of " + name;

        boolean shiny = rng.nextDouble() < 0.05;
        int imageSize = shiny ? 1024 : 128;

        // Random unique ID (UUID works, or any string)
        String id = UUID.randomUUID().toString();

        return new Card(id, name, description, shiny, imageSize);
    }
}
