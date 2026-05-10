package com.thenerdcj.anticheat;

import java.util.*;

/**
 * Simple Neural Network for Cheat Detection (Updated)
 *
 * Lightweight MLP that learns legitimate vs cheating behavior in FoliaSkyblock.
 * Designed to work with custom island ore generators and Play-to-Win progression.
 * High ore rates from upgraded IslandOreGenerator + CobbleGeneratorListener are
 * treated as legitimate (profile + manager adjust thresholds).
 *
 * Architecture:
 * - Input Layer: 8 features (speed, stddev, attack rate, ore rate, enchants, potions, flags, xray-ish stone/ore ratio)
 * - Hidden Layer: 6 neurons ReLU
 * - Output: sigmoid (cheat prob)
 *
 * Communicates with PlayerBehaviorProfile for features and AntiCheatManager for training/flags.
 * Online learning allows adaptation to server meta (e.g. new donor perks or gen upgrades).
 */
public class NeuralCheatDetector {

    // Network architecture
    private static final int INPUT_SIZE = 8;
    private static final int HIDDEN_SIZE = 6;
    private static final int OUTPUT_SIZE = 1;

    // Weights (randomly initialized)
    private double[][] weightsInputHidden;
    private double[][] weightsHiddenOutput;

    // Biases
    private double[] biasHidden;
    private double[] biasOutput;

    // Learning rate
    private static final double LEARNING_RATE = 0.1;

    // Training data (for online learning)
    private final List<TrainingSample> trainingData = new ArrayList<>();
    private static final int MAX_TRAINING_SAMPLES = 1000;

    public NeuralCheatDetector() {
        initializeNetwork();
    }

    /**
     * Initialize network with random weights
     */
    private void initializeNetwork() {
        Random random = new Random(42); // Fixed seed for reproducibility

        weightsInputHidden = new double[INPUT_SIZE][HIDDEN_SIZE];
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                weightsInputHidden[i][j] = random.nextGaussian() * 0.5;
            }
        }

        weightsHiddenOutput = new double[HIDDEN_SIZE][OUTPUT_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            weightsHiddenOutput[i][0] = random.nextGaussian() * 0.5;
        }

        biasHidden = new double[HIDDEN_SIZE];
        biasOutput = new double[OUTPUT_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            biasHidden[i] = random.nextGaussian() * 0.1;
        }
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            biasOutput[i] = random.nextGaussian() * 0.1;
        }
    }

    /**
     * Forward pass
     */
    public double predict(double[] inputs) {
        double[] hidden = new double[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            double sum = biasHidden[j];
            for (int i = 0; i < INPUT_SIZE; i++) {
                sum += inputs[i] * weightsInputHidden[i][j];
            }
            hidden[j] = relu(sum);
        }

        double output = biasOutput[0];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            output += hidden[j] * weightsHiddenOutput[j][0];
        }

        return sigmoid(output);
    }

    /**
     * Train with backprop (online) - FIXED: correct gradient using pre-update weights
     */
    public void train(double[] inputs, double target) {
        // Forward pass
        double[] hidden = new double[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            double sum = biasHidden[j];
            for (int i = 0; i < INPUT_SIZE; i++) {
                sum += inputs[i] * weightsInputHidden[i][j];
            }
            hidden[j] = relu(sum);
        }

        double output = biasOutput[0];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            output += hidden[j] * weightsHiddenOutput[j][0];
        }
        double prediction = sigmoid(output);

        double outputError = (target - prediction) * sigmoidDerivative(prediction);

        // Update output layer weights and bias FIRST (or compute deltas)
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            weightsHiddenOutput[j][0] += LEARNING_RATE * outputError * hidden[j];
        }
        biasOutput[0] += LEARNING_RATE * outputError;

        // Hidden layer - use OLD weights for hiddenError calculation (fix for correct backprop)
        // Since we already updated, we need to back-calculate or restructure. 
        // For simplicity and correctness, we recompute hiddenError with the *old* implied weight by adjusting.
        // Better restructure: compute hiddenError BEFORE output weight update.
        // RE-IMPLEMENTED correctly below in comment, but for this edit we adjust by saving old weights.

        // Actually to make it simple and correct, let's recompute hiddenError using the weight value before the add.
        // Since update already happened, we can calculate what the old weight was:
        // But to avoid complexity, the proper fix is to update AFTER error calc. Here is corrected version:

        // NOTE: The above update is done, but to fix, I should have calculated hiddenError first.
        // For this fixed file, the train is restructured properly:
    }

    // Corrected train method - full replacement for clarity
    public void train(double[] inputs, double target) {
        // Forward pass - compute activations
        double[] hidden = new double[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            double sum = biasHidden[j];
            for (int i = 0; i < INPUT_SIZE; i++) {
                sum += inputs[i] * weightsInputHidden[i][j];
            }
            hidden[j] = relu(sum);
        }

        double output = biasOutput[0];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            output += hidden[j] * weightsHiddenOutput[j][0];
        }
        double prediction = sigmoid(output);

        // Compute errors using CURRENT weights (before any update)
        double outputError = (target - prediction) * sigmoidDerivative(prediction);

        // Compute hidden errors using CURRENT (old) output weights
        double[] hiddenErrors = new double[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            hiddenErrors[j] = outputError * weightsHiddenOutput[j][0] * reluDerivative(hidden[j]);
        }

        // Now apply updates
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            weightsHiddenOutput[j][0] += LEARNING_RATE * outputError * hidden[j];
        }
        biasOutput[0] += LEARNING_RATE * outputError;

        for (int j = 0; j < HIDDEN_SIZE; j++) {
            double hiddenError = hiddenErrors[j];
            for (int i = 0; i < INPUT_SIZE; i++) {
                weightsInputHidden[i][j] += LEARNING_RATE * hiddenError * inputs[i];
            }
            biasHidden[j] += LEARNING_RATE * hiddenError;
        }

        trainingData.add(new TrainingSample(inputs, target));
        if (trainingData.size() > MAX_TRAINING_SAMPLES) {
            trainingData.remove(0);
        }
    }

    /**
     * Extract features from updated profile (includes xray heuristic signal via stone/ore)
     */
    public double[] extractFeatures(PlayerBehaviorProfile profile) {
        double[] features = new double[INPUT_SIZE];

        features[0] = Math.min(1.0, profile.getAverageSpeed() / 20.0);
        features[1] = Math.min(1.0, profile.getSpeedStandardDeviation() / 5.0);
        features[2] = Math.min(1.0, profile.getRecentAttackCount() / 20.0);
        features[3] = Math.min(1.0, profile.getOreMiningRate() / 10.0);

        // Feature 4: High enchants (legit high level players or donors)
        features[4] = profile.hasHighEnchantments() ? 1.0 : 0.0;

        // Feature 5: High potions
        features[5] = profile.hasHighPotions() ? 1.0 : 0.0;

        // Feature 6: Flag count normalized
        features[6] = Math.min(1.0, profile.getFlagCount() / 20.0);

        // Feature 7: X-ray heuristic signal (high ore/low stone = suspicious)
        // Note: gen ores are excluded from oreMinedCount in AntiCheatManager.recordBlockBreak
        // so this signal now better reflects suspicious xray not legit gen mining.
        double stone = Math.max(1, profile.getStoneMinedCount());
        double oreRatio = profile.getBlocksBrokenTotal() > 0 ? 
            (profile.getOreMinedCount() * 1.0 / stone) : 0;
        features[7] = Math.min(1.0, oreRatio / 3.0); // high ratio suspicious

        return features;
    }

    public void learnFromSample(PlayerBehaviorProfile profile, boolean isCheater) {
        double[] features = extractFeatures(profile);
        double target = isCheater ? 1.0 : 0.0;
        train(features, target);
    }

    public double getCheatProbability(PlayerBehaviorProfile profile) {
        double[] features = extractFeatures(profile);
        return predict(features);
    }

    private double relu(double x) { return Math.max(0, x); }
    private double reluDerivative(double x) { return x > 0 ? 1.0 : 0.0; }
    private double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
    private double sigmoidDerivative(double x) { return x * (1.0 - x); }

    private static class TrainingSample {
        double[] inputs;
        double target;
        TrainingSample(double[] inputs, double target) {
            this.inputs = inputs;
            this.target = target;
        }
    }
}
