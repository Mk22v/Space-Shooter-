package sp;
import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.*;
import java.util.List;

public class SpaceShooterGame extends JPanel implements ActionListener, KeyListener {

    private static final long serialVersionUID = 1L;
    private static final int WIDTH = 350, HEIGHT = 400, FPS = 60;
    private static final float PLAYER_SPEED = 6f, BULLET_SPEED = 10f, ENEMY_SPEED = 2.5f;

    private final List<Entity> entities = new ArrayList<>();
    private final List<Entity> bullets = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final BitSet keys = new BitSet(256);
    private final Random random = new Random();

    private Entity player;
    private int score = 0, lives = 3, spawnTimer = 0;
    private GameState state = GameState.MENU;
    private Timer timer;

    // For fade effect on Game Over screen
    private int gameOverAlpha = 0;

    private BufferedImage[] imgs = new BufferedImage[10];
    private BufferedImage buffer;
    private Graphics2D gBuffer;

    enum GameState { MENU, PLAYING, GAME_OVER }
    enum Type { PLAYER, ENEMY, ASTEROID1, ASTEROID2, LASER, EXPLOSION, EXHAUST }

    static class Entity {
        float x, y, dx, dy, w, h;
        Type type;
        BufferedImage img;
        boolean active = true;
        int frame = 0, timer = 0;

        Entity(float x, float y, Type type, BufferedImage img) {
            this.x = x; this.y = y; this.type = type; this.img = img;
            if (img != null) { this.w = img.getWidth(); this.h = img.getHeight(); }
        }

        void update() {
            x += dx; y += dy; timer++;
            if (type == Type.EXPLOSION && timer > 30) active = false;
            if (type == Type.EXHAUST) frame = (frame + 1) % 2;
        }

        boolean hits(Entity other) {
            return x < other.x + other.w && x + w > other.x &&
                   y < other.y + other.h && y + h > other.y;
        }

        void draw(Graphics2D g) {
            if (active && img != null)
                g.drawImage(img, (int) x, (int) y, (int) w, (int) h, null);
        }
    }

    static class Particle {
        float x, y, dx, dy, life = 1f, decay = 0.02f;
        Color color;

        Particle(float x, float y, Color color) {
            this.x = x; this.y = y; this.color = color;
            this.dx = (float)(Math.random() - 0.5) * 4;
            this.dy = (float)(Math.random() - 0.5) * 4;
        }

        void update() { x += dx; y += dy; life -= decay; }
        boolean isDead() { return life <= 0; }

        void draw(Graphics2D g) {
            int alpha = Math.max(0, (int)(255 * life));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.fillOval((int) x, (int) y, 3, 3);
        }
    }

    public SpaceShooterGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        gBuffer = buffer.createGraphics();
        gBuffer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        loadAssets();
        reset();

        timer = new Timer(1000 / FPS, this);
        timer.start();
    }

    private void loadAssets() {
        String[] names = {
            "BACKGROUND", "PLAYER", "ENEMY", "AST-1", "AST-2",
            "LASER", "EXHAUST1", "EXHAUST2", "EXPLOSION1", "EXPLOSION2"
        };
        try {
            for (int i = 0; i < names.length; i++)
                imgs[i] = ImageIO.read(new File("resources/" + names[i] + ".png"));
        } catch (Exception e) {
            for (int i = 0; i < imgs.length; i++) {
                imgs[i] = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = imgs[i].createGraphics();
                g.setColor(i == 0 ? Color.BLACK : Color.getHSBColor(i * 0.1f, 0.8f, 0.9f));
                g.fillRect(0, 0, 32, 32);
                g.dispose();
            }
        }
    }

    private void reset() {
        entities.clear(); bullets.clear(); particles.clear();
        player = new Entity(WIDTH / 2f - 32, HEIGHT - 80, Type.PLAYER, imgs[1]);
        score = 0; lives = 3; spawnTimer = 0;
        gameOverAlpha = 0;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.PLAYING) updateGame();
        renderGame();
        repaint();
    }

    private void updateGame() {
        if (keys.get(KeyEvent.VK_LEFT) && player.x > 0) player.x -= PLAYER_SPEED;
        if (keys.get(KeyEvent.VK_RIGHT) && player.x < WIDTH - player.w) player.x += PLAYER_SPEED;
        if (keys.get(KeyEvent.VK_UP) && player.y > 0) player.y -= PLAYER_SPEED;
        if (keys.get(KeyEvent.VK_DOWN) && player.y < HEIGHT - player.h) player.y += PLAYER_SPEED;

        if (++spawnTimer % 120 == 0)
            spawnEntity(Type.ENEMY, imgs[2], ENEMY_SPEED);

        if (spawnTimer % 180 == 0)
            spawnEntity(random.nextBoolean() ? Type.ASTEROID1 : Type.ASTEROID2,
                        random.nextBoolean() ? imgs[3] : imgs[4], 3f);

        entities.removeIf(e -> {
            e.update();
            if (e.type == Type.ENEMY && random.nextInt(200) == 0)
                spawnBullet(e.x + e.w / 2, e.y + e.h, 5f);
            return !e.active || e.y > HEIGHT;
        });

        bullets.removeIf(b -> {
            b.update();
            return !b.active || b.y > HEIGHT || b.y < -50;
        });

        particles.removeIf(p -> { p.update(); return p.isDead(); });

        checkCollisions();

        if (lives <= 0) {
            state = GameState.GAME_OVER;
        }
    }

    private void spawnEntity(Type type, BufferedImage img, float dy) {
        Entity e = new Entity(random.nextInt(WIDTH - 64), -64, type, img);
        e.dy = dy;
        entities.add(e);
    }

    private void spawnBullet(float x, float y, float dy) {
        Entity laser = new Entity(x - 4, y, Type.LASER, imgs[5]);
        laser.dy = dy;
        bullets.add(laser);
    }

    private void checkCollisions() {
        List<Entity> bulletsToRemove = new ArrayList<>();
        List<Entity> entitiesToRemove = new ArrayList<>();

        for (Entity bullet : bullets) {
            if (bullet.dy < 0) { // Player bullet
                for (Entity entity : entities) {
                    if ((entity.type == Type.ENEMY || entity.type == Type.ASTEROID1 || 
                         entity.type == Type.ASTEROID2) && bullet.hits(entity)) {
                        explode(entity.x + entity.w / 2, entity.y + entity.h / 2);
                        score += entity.type == Type.ENEMY ? 100 : 50;
                        bulletsToRemove.add(bullet);
                        entitiesToRemove.add(entity);
                        break;
                    }
                }
            } else { // Enemy bullet
                if (bullet.hits(player)) {
                    explode(player.x + player.w / 2, player.y + player.h / 2);
                    lives--;
                    bulletsToRemove.add(bullet);
                }
            }
        }

        for (Entity entity : entities) {
            if ((entity.type == Type.ENEMY || entity.type == Type.ASTEROID1 || 
                 entity.type == Type.ASTEROID2) && entity.hits(player)) {
                explode(player.x + player.w / 2, player.y + player.h / 2);
                explode(entity.x + entity.w / 2, entity.y + entity.h / 2);
                lives--;
                entitiesToRemove.add(entity);
                break;
            }
        }

        bullets.removeAll(bulletsToRemove);
        entities.removeAll(entitiesToRemove);
    }

    private void explode(float x, float y) {
        entities.add(new Entity(x - 32, y - 32, Type.EXPLOSION, imgs[8]));
        for (int i = 0; i < 8; i++)
            particles.add(new Particle(x, y, new Color(255, 100 + random.nextInt(155), 0)));
    }

    private void renderGame() {
        // Gradient background for polish
        GradientPaint gp = new GradientPaint(0, 0, Color.BLACK, 0, HEIGHT, Color.DARK_GRAY);
        gBuffer.setPaint(gp);
        gBuffer.fillRect(0, 0, WIDTH, HEIGHT);

        // Draw background image
        gBuffer.drawImage(imgs[0], 0, 0, WIDTH, HEIGHT, null);

        if (state == GameState.PLAYING) {
            player.draw(gBuffer);
            gBuffer.drawImage(imgs[6 + (spawnTimer / 5) % 2], (int)player.x + 20,
                    (int)player.y + 64, 23, 30, null);
            entities.forEach(e -> e.draw(gBuffer));
            bullets.forEach(b -> b.draw(gBuffer));
            particles.forEach(p -> p.draw(gBuffer));

            gBuffer.setColor(Color.WHITE);
            gBuffer.setFont(new Font("Arial", Font.BOLD, 16));
            gBuffer.drawString("Score: " + score, 10, 25);
            gBuffer.drawString("Lives: " + lives, 10, 45);

        } else {
            // Fade-in for Game Over
            if (state == GameState.GAME_OVER && gameOverAlpha < 255) {
                gameOverAlpha += 5;
                if (gameOverAlpha > 255) gameOverAlpha = 255;
            }

            gBuffer.setColor(new Color(255, 255, 255, (state == GameState.GAME_OVER) ? gameOverAlpha : 255));
            gBuffer.setFont(new Font("Arial", Font.BOLD, 24));
            String title = state == GameState.MENU ? "SPACE SHOOTER" : "GAME OVER";
            FontMetrics fm = gBuffer.getFontMetrics();
            gBuffer.drawString(title, (WIDTH - fm.stringWidth(title)) / 2, HEIGHT / 2 - 50);

            gBuffer.setFont(new Font("Arial", Font.PLAIN, 16));

            if (state == GameState.GAME_OVER) {
                String scoreText = "Final Score: " + score;
                gBuffer.drawString(scoreText, (WIDTH - gBuffer.getFontMetrics().stringWidth(scoreText)) / 2, HEIGHT / 2);

                String restartText = "Press R to Restart";
                gBuffer.drawString(restartText, (WIDTH - gBuffer.getFontMetrics().stringWidth(restartText)) / 2, HEIGHT / 2 + 25);
            } else {
                String sub = "Press SPACE to Start";
                gBuffer.drawString(sub, (WIDTH - gBuffer.getFontMetrics().stringWidth(sub)) / 2, HEIGHT / 2);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(buffer, 0, 0, null);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        keys.set(k);

        if (state == GameState.MENU && k == KeyEvent.VK_SPACE) {
            state = GameState.PLAYING;
            reset();
        } else if (state == GameState.GAME_OVER && k == KeyEvent.VK_R) {
            state = GameState.PLAYING;
            reset();
        } else if (state == GameState.PLAYING && k == KeyEvent.VK_SPACE) {
            spawnBullet(player.x + player.w / 2, player.y, -BULLET_SPEED);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) { keys.clear(e.getKeyCode()); }
    @Override
    public void keyTyped(KeyEvent e) {}
}
