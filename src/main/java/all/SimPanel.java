package all;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Random;

import javax.swing.JPanel;

public class SimPanel extends JPanel implements Runnable {

    // allocate a thread to run 
    Thread simulationThread;

    Camera camera = new Camera();
    
    // keyhandler for input
    KeyHandler keyHandler = new KeyHandler();

    // simulation features
    final static boolean MERGE = false; // if particles should merge (exclusive with collision)
    final static boolean GRAVITY = true; // if particles should have gravity
    final static boolean COLLISION = false; // if particles should collide (exclusive with merge)
    final static boolean DECELERATE = false; // slow down particle speed to compensate for errors

    // simulatin parameters
    public final static double SCALE = MainFrame.HEIGHT / 54; // pixels in a unit (currentely 20 units)
    final static int FPS = 120; // frames per second
    final static double GRAVITYSTRENGTH = 1; // strength of gravity
    final static double DECELERATOR = 0.99; // compensates for errors
    final static double BARRIER = 50; // distance in units to the edges of the universe
    final static double RANDOMVELOCITY = 1;

    // array of particles
    public static ArrayList<Particle> particles = new ArrayList<>(); 

    // matrix of chunks
    private static final double CHUNKSIZE = 10;
    private static final int numberOfChunks = (int)(2 * BARRIER / CHUNKSIZE) + 1;
    public static ArrayList<Particle>[][] chunkGrid = new ArrayList[numberOfChunks][numberOfChunks];

    Double attraction[][] = {{1.0, 1.0, -1.0, 0.0}, 
                             {0.0, 1.0, 1.0, -1.0},
                             {-1.0, 0.0, 1.0, 1.0},
                             {1.0, -1.0, 0.0, 1.0}};

    public SimPanel() {
        //panel settings
        this.setSize(MainFrame.WIDTH, MainFrame.HEIGHT);
        this.setBackground(new Color(20, 0, 20));
        this.setLocation(0, 0);
        this.addKeyListener(keyHandler);
        this.setLayout(null);
        this.setFocusable(true);
        this.setDoubleBuffered(true);
        this.setVisible(true);

        //randomizeAttraction();

        create(100, 0, 1);
        // create(100, 1, 1);
        // create(100, 2, 1);
        // create(100, 3, 1);
    }

    // used to test fine details
    @SuppressWarnings("unused")
    private void addTestParticles() {
        Particle particle1 = new Particle();
        particle1.x = -10;
        particle1.y = -2;
        particle1.vx = 10;
        particle1.vy = 0;
        particle1.changeMass(64);


        Particle particle2 = new Particle();
        particle2.x = 0;
        particle2.y = 0;
        particle2.vx = 5;
        particle2.vy = 0;
        particle2.changeMass(64);

        particles.add(particle1);
        particles.add(particle2);
    }

    private void randomizeAttraction() {
        Random random = new Random();

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++ )
                attraction[i][j] = random.nextDouble(-1, 1);
        }
    }

    public void startSimulationThread() {
        simulationThread = new Thread(this);
        simulationThread.start();
    }

    @Override
    public void run() {

        // calculations are in nanoseconds nextDrawTime is the next frame
        double drawInterval = 1e9 / FPS; 
        double nextDrawTime = System.nanoTime() + drawInterval;

        while (simulationThread != null) {

            // check for paused game
            if (!keyHandler.spacePressed) 
                updatePhysics();

            updateCamera();
            repaint();

            try {
                // the target time minus current time
                double remainingTime = nextDrawTime - System.nanoTime();
                
                // avoiding negatives and convert from nano to milisecods
                remainingTime = Math.max(remainingTime, 0) / 1e6;

                // wait until next draw
                Thread.sleep((long) remainingTime);

                nextDrawTime += drawInterval;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // moves the camera position
    private void updateCamera() {
        
        if (keyHandler.upPressed)
            camera.y -= camera.speed;
        
        if (keyHandler.downPressed)
            camera.y += camera.speed;
        
        if (keyHandler.leftPressed)
            camera.x -= camera.speed;

        if (keyHandler.rightPressed)
            camera.x += camera.speed;
    }

    // this method controls the mechanics of each frame
    public void updatePhysics() {
        
        if (MERGE) updateMerge();

        if (COLLISION) updateCollisions();

        if (GRAVITY) updateGravity();

        if (DECELERATE) updateDeceleration();

        updateBarrierCollisons();

        updatePositions();
    }

    // this method calculates the resulting collision velocities between all particles
    // it must be one way since a call of collideParticles() computes for both
    private void updateCollisions() {
        for (int i = 0; i < particles.size() - 1; i++) {
            Particle first = particles.get(i);
            for (int j = i + 1; j < particles.size(); j++) {
                Particle second = particles.get(j);

                if (touching(first, second)) {
                    // printTotalKineticEnergy();
                    fixOverlap(first, second);
                    collideParticles(first, second);
                    // printTotalKineticEnergy();
                    System.out.println("\n");
                }
            }
        }
    }

    private void fixOverlap(Particle first, Particle second) {
        double distance = calculateDistance(first, second);
        double overlap = first.radius + second.radius - distance; // overlap is positive
        double shiftX = (second.x - first.x) * overlap / distance;
        double shiftY = (second.y - first.y) * overlap / distance;

        shiftX /= 2;
        shiftY /= 2;

        first.x -= shiftX;
        first.y -= shiftY;

        second.x += shiftX;
        second.y += shiftY;
    }

    private void collideParticles(Particle first, Particle second) {
        double dx = second.x - first.x;
        double dy = second.y - first.y;
        double dvx = second.vx - first.vx;
        double dvy = second.vy - first.vy;
        double velocityRatio = (dvx * dx + dvy * dy) / (dx * dx + dy * dy); // vr = vel * pos / dist^2

        // calculate velocity of first
        double massRatio1 = 2 * second.mass / (first.mass + second.mass);
        first.vx += massRatio1 * velocityRatio * dx;
        first.vy += massRatio1 * velocityRatio * dy;

        // calculate velocity of second
        double massRatio2 = 2 * first.mass / (first.mass + second.mass);
        second.vx += massRatio2 * velocityRatio * -dx;
        second.vy += massRatio2 * velocityRatio * -dy;
    }

    @SuppressWarnings("unused")
    private void printTotalKineticEnergy() {
        double sum = 0.0;

        for (Particle particle : particles) {
            double velocitySquared = (particle.vx * particle.vx) + (particle.vy * particle.vy);
            sum += 0.5 * particle.mass * velocitySquared; // 1/2 * m * |v|^2
        }

        System.out.println(sum);
    }

    @SuppressWarnings("unused")
    private void printTotalMomentum() {
        double momentumX = 0.0;
        double momentumY = 0.0;

        for (Particle particle : particles) {
            momentumX += particle.mass * particle.vx;
            momentumY += particle.mass * particle.vy;
        }

        System.out.println(momentumX + " " + momentumY);
    }
    
    @SuppressWarnings("unused")
    private Vector newtonianForce(Particle first, Particle second) {
        // the distance betweent the particles
        Vector distance = new Vector(second.x - first.x, second.y - first.y);
        double distanceSquared = Math.max(distance.magSquared(), 0.1);

        // calculate force (squared comes from force formula and projection)
        double forceX = GRAVITYSTRENGTH * first.mass * second.mass / distanceSquared * distance.x;
        double forceY = GRAVITYSTRENGTH * first.mass * second.mass / distanceSquared * distance.y;

        // multiply by attraction
        forceX *= attraction[first.type][second.type];
        forceY *= attraction[first.type][second.type];

        // combine and return
        Vector force = new Vector(forceX, forceY);
        return force;
    }
    
    @SuppressWarnings("unused")
    private Vector logForce(Particle first, Particle second) {
        // the distance betweent the particles
        Vector distance = new Vector(second.x - first.x, second.y - first.y);
        double safeDistance = Math.max(distance.magnitude(), 0.1);

        double logarithm = Math.log(safeDistance);
        double function = -(4 * logarithm * logarithm / safeDistance)  + 2.16536;
        double force = GRAVITYSTRENGTH * first.mass * second.mass * function;

        Vector forceVector = new Vector(force * distance.x / safeDistance, force * distance.y / safeDistance);
        return forceVector;
    }

    private Vector electrostaticForce(Particle first, Particle second) {
        // the distance between the particles
        Vector distance = new Vector(second.x - first.x, second.y - first.y);
        double safeDistance = Math.max(distance.magnitude(), 0.1);

        double force = GRAVITYSTRENGTH * first.mass * second.mass;

        if (safeDistance < 2) {
            force *= safeDistance - 2;
        } else {
            force *= attraction[first.type][second.type];
            if (safeDistance < 6)
                force *= (safeDistance / 2) - 1;
            else {
                if (safeDistance < 10)
                    force *= - (safeDistance / 2) + 5 ;
                    else force *= 0;
            }
        }

        // TODO: can completely cut safeDistance
        Vector forceVector = new Vector(force * distance.x / safeDistance, force * distance.y / safeDistance);
        return forceVector;
    }
    
    // this method calculates the attraction between all particles
    // we calculate the velocity through the forces particle have on eachother
    private void updateGravity() {
        for (Particle first : particles) {

            Vector totalForce = new Vector(0, 0);

            for (Particle second : particles) {
                if (first != second) {
                    
                    totalForce.sum(newtonianForce(first, second));
                }
            }

            // convert force into acceleration F = m * a
            Vector acceleration = new Vector(0, 0);
            acceleration.x = totalForce.x / first.mass;
            acceleration.y = totalForce.y / first.mass;
            
            // devide by fps because small time interval vf = vi + a * t
            first.vx += acceleration.x / FPS;
            first.vy += acceleration.y / FPS;   
        } 
    }

    // decelerate particle to compensate for approximation errors
    private void updateDeceleration() {
        for (Particle particle : particles){
            particle.vx *= DECELERATOR;
            particle.vy *= DECELERATOR; 
        }
    }

    // returns the distance between two particles
    private double calculateDistance(Particle first, Particle second) {
        double distanceX = second.x - first.x;
        double distanceY = second.y - first.y;
        double distance = Math.sqrt(distanceX * distanceX + distanceY * distanceY);

        return distance;
    }

    // merge particles with combined mass and momentum vf = (m1*v1 + m2*v2) / (m1+m2)
    private void updateMerge() {
        ArrayList<Particle> particlesToAdd = new ArrayList<>();

        for (Particle first : particles) {
            for (Particle second : particles) {
                
                double distance = calculateDistance(first, second);

                if (!(first.marked || second.marked) && (distance < first.radius + second.radius)){
                    particlesToAdd.add(mergeParticles(first, second));
                }
            }
        }

        // move them from one to the other
        for (Particle particle : particlesToAdd)
            particles.add(particle);

        deleteMarked();
    }

    // marges two particles by preserving mass and momentum
    private Particle mergeParticles(Particle first, Particle second) {
        Particle result = new Particle();

        result.vx = (first.mass * first.vx + second.mass * second.vx) / (first.mass + second.mass);
        result.vy = (first.mass * first.vy + second.mass * second.vy) / (first.mass + second.mass);
        result.changeMass(first.mass + second.mass);
        result.x = (first.x + second.x) / 2;
        result.y = (first.y + second.y) / 2;
        result.type = first.type; // TODO: think abot this

        first.marked = true;
        second.marked = true;

        return result;
    }
    
    private void updatePositions() {
        for (Particle particle : particles) {
            // remove from current chunk
            // chunkGrid[particle.chunkX][particle.chunkY].remove(particle);

            // we calculate position after a frame xf = xi + v * t
            particle.x += particle.vx / FPS;
            particle.y += particle.vy / FPS;

            // add to a new chunk
            // particle.chunkX = (int)(particle.x / CHUNKSIZE);
            // particle.chunkY = (int)(particle.y / CHUNKSIZE);
            // chunkGrid[particle.chunkX][particle.chunkY].add(particle);
        }
    }

    // change the velocities to bounce off barrier
    private void updateBarrierCollisons() {
        for (Particle particle : particles){
            // left side
            if (particle.x - particle.radius < - BARRIER )
                particle.vx = Math.abs(particle.vx);
            
            // right side
            if (BARRIER  < particle.x + particle.radius)
                particle.vx = -Math.abs(particle.vx);

            // bottom side
            if (particle.y - particle.radius < - BARRIER )
                particle.vy = Math.abs(particle.vy);

            // top side
            if (BARRIER < particle.y + particle.radius)
                particle.vy = -Math.abs(particle.vy);
        }
    }

    // return true if particles are touching
    private boolean touching(Particle first, Particle second) {
        double distance = calculateDistance(first, second);

        return distance < first.radius + second.radius;
    }

    // delete all marked
    private void deleteMarked() {
        for (int i=0; i<particles.size(); i++){
            if (particles.get(i).marked)
                particles.remove(i);
        }
    }

    // draw the border of the universe
    private void drawBorder(Graphics2D g2d) {
        // the coordinates of the the bounding box
        double drawx = -BARRIER;
        double drawy = -BARRIER;
        double drawlen = 2 * BARRIER;

        // compensate for scale
        drawx *= SCALE;
        drawy *= SCALE;
        drawlen *= SCALE;

        // compensate for screen center
        drawx += MainFrame.WIDTH / 2;
        drawy += MainFrame.HEIGHT / 2;

        // compensate for camera
        drawx -= camera.x * SCALE;
        drawy -= camera.y * SCALE;

        g2d.setColor(Color.white);
        g2d.drawRect((int)drawx, (int)drawy, (int)drawlen, (int)drawlen);
    }

    // draw all of the particles
    private void drawParticles(Graphics2D g2d) {
        for (Particle particle : particles) {
            // the coordinates of the the bounding box
            double drawx = particle.x - particle.radius;
            double drawy = particle.y - particle.radius;
            double drawrad = 2 * particle.radius;

            // compensate for scale
            drawx *= SCALE;
            drawy *= SCALE;
            drawrad *= SCALE;

            // compensate for screen center
            drawx += MainFrame.WIDTH / 2;
            drawy += MainFrame.HEIGHT / 2;

            // compensate for camera
            drawx -= camera.x * SCALE;
            drawy -= camera.y * SCALE;

            switch (particle.type) {
                case 0 -> g2d.setColor(Color.red);
                case 1 -> g2d.setColor(Color.cyan);
                case 2 -> g2d.setColor(Color.green);
                case 3 -> g2d.setColor(Color.yellow);
                default -> System.out.println("big fuk");
            }

            g2d.fillOval((int)drawx, (int)drawy, (int)drawrad, (int)drawrad);
        }
    }

    // draws the white refrence of a unit in the corner
    private void drawReference(Graphics2D g2d) {
        // coordinates relative to frame
        for (int i=0; i<10; i++) {

            int bufferX = MainFrame.WIDTH / 100;
            int bufferY = MainFrame.HEIGHT / 20;
            int fromX = bufferX+ i * (int)SCALE;
            int fromY = MainFrame.HEIGHT - bufferY;
            int toX = fromX + (int)SCALE;
            int toY = fromY;

            // draw the lines
            if (i % 2 == 0) g2d.setColor(new Color(255, 255, 255, 255));
            else g2d.setColor(new Color(255, 255, 255, 128));

            g2d.drawLine(fromX, fromY, toX, toY);
        }
    }

    // the primary paint method called each frame
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        drawParticles(g2d);
        drawBorder(g2d);
        drawReference(g2d);

        g2d.dispose();
    }

    // creates a specified amount of particles with random positions
    public static void create(int number, int type, double mass){
        Random random = new Random();

        for (int i=0; i<number; i++){
            Particle particle = new Particle();

            particle.x = random.nextDouble(-BARRIER / 2, BARRIER / 2);
            particle.y = random.nextDouble(-BARRIER / 2, BARRIER / 2);

            // give particles random velocity bounded by RANDOMVELOCITY, must not be 0
            double randomPositiveVelocity = Math.max(0.0001, RANDOMVELOCITY);
            particle.vx = random.nextDouble(-randomPositiveVelocity, randomPositiveVelocity);
            particle.vy = random.nextDouble(-randomPositiveVelocity, randomPositiveVelocity);
            
            particle.type = type;
            particle.changeMass(mass);

            particles.add(particle);
        }
    }
}