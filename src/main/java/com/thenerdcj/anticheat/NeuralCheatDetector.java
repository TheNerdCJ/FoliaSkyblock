package com.thenerdcj.anticheat;

import java.util.*;

/**
 * Simple Neural Network for Cheat Detection
 *
 * A lightweight multi-layer perceptron that learns to distinguish
 * between legitimate players (with high enchants/potions) and actual cheaters.
 *
 * Architecture:
 * - Input Layer: 8 features (speed, reach, attack rate, ore rate, enchants, potions, flags, streak)
 * - Hidden Layer: 6 neurons with ReLU activation
 * - Output Layer: 1 neuron with sigmoid activation (0 = legitimate, 1 = cheating)
 *
 * Training: Online learning with backpropagation
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

        // Input -> Hidden weights
        weightsInputHidden = new double[INPUT_SIZE][HIDDEN_SIZE];
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                weightsInputHidden[i][j] = random.nextGaussian() * 0.5;
            }
        }

        // Hidden -> Output weights
        weightsHiddenOutput = new double[HIDDEN_SIZE][OUTPUT_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            weightsHiddenOutput[i][0] = random.nextGaussian() * 0.5;
        }

        // Biases
        biasHidden = new double[HIDDEN_SIZE];
        biasOutput = new double[OUTPUT_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            biasHidden[i] = random.nextGaussian() * 0.1;
        }
    }

    /**
     * Forward pass through the network
     */
    public double predict(double[] inputs) {
        // Hidden layer
        double[] hidden = new double[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            double sum = biasHidden[j];
            for (int i = 0; i < INPUT_SIZE; i++) {
                sum += inputs[i] * weightsInputHidden[i][j];
            }
            hidden[j] = relu(sum);
        }

        // Output layer
        double output = biasOutput[0];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            output += hidden[j] * weightsHiddenOutput[j][0];
        }

        return sigmoid(output);
    }

    /**
     * Train the network with a single sample
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

        // Backward pass (gradient descent)
        double outputError = (target - prediction) * sigmoidDerivative(prediction);

        // Update hidden -> output weights
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            weightsHiddenOutput[j][0] += LEARNING_RATE * outputError * hidden[j];
        }
        biasOutput[0] += LEARNING_RATE * outputError;

        // Update input -> hidden weights
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            double hiddenError = outputError * weightsHiddenOutput[j][0] * reluDerivative(hidden[j]);
            for (int i = 0; i < INPUT_SIZE; i++) {
                weightsInputHidden[i][j] += LEARNING_RATE * hiddenError * inputs[i];
            }
            biasHidden[j] += LEARNING_RATE * hiddenError;
        }

        // Store training sample
        trainingData.add(new TrainingSample(inputs, target));
        if (trainingData.size() > MAX_TRAINING_SAMPLES) {
            trainingData.remove(0);
        }
    }

    /**
     * Extract features from player behavior profile
     */
    public double[] extractFeatures(PlayerBehaviorProfile profile) {
        double[] features = new double[INPUT_SIZE];

        // Feature 0: Speed deviation (normalized)
        features[0] = Math.min(1.0, profile.getAverageSpeed() / 20.0);

        // Feature 1: Speed standard deviation
        features[1] = Math.min(1.0, profile.getSpeedStandardDeviation() / 5.0);

        // Feature 2: Recent attack rate (attacks per second)
        features[2] = Math.min(1.0, profile.getRecentAttackCount() / 20.0);

        // Feature 3: Ore mining rate
        features[3] = Math.min(1.0, profile.getOreMiningRate() / 10.0);

        // Feature 4: Has legitimate speed (0 or 1)
        features[4] = profile.hasLegitimateSpeed() ? 1.0 : 0.0;

        // Feature 5: Has high enchantments (0 or 1)
        features[5] = profile.hasHighEnchantments() ? 1.0 : 0.0;

        // Feature 6: Flag count (normalized)
        features[6] = Math.min(1.0, profile.getFlagCount() / 20.0);

        // Feature 7: Has high potions (0 or 1)
        features[7] = profile.hasHighPotions() ? 1.0 : 0.0;

        return features;
    }

    /**
     * Train on a labeled sample
     * @param profile Player behavior profile
     * @param isCheater True if player is confirmed cheating, false if legitimate
     */
    public void learnFromSample(PlayerBehaviorProfile profile, boolean isCheater) {
        double[] features = extractFeatures(profile);
        double target = isCheater ? 1.0 : 0.0;
        train(features, target);
    }

    /**
     * Get cheat probability (0.0 = legitimate, 1.0 = cheating)
     */
    public double getCheatProbability(PlayerBehaviorProfile profile) {
        double[] features = extractFeatures(profile);
        return predict(features);
    }

    /**
     * ReLU activation function
     */
    private double relu(double x) {
        return Math.max(0, x);
    }

    private double reluDerivative(double x) {
        return x > 0 ? 1.0 : 0.0;
    }

    /**
     * Sigmoid activation function
     */
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private double sigmoidDerivative(double x) {
        return x * (1.0 - x);
    }

    /**
     * Training sample for online learning
     */
    private static class TrainingSample {
        double[] inputs;
        double target;

        TrainingSample(double[] inputs, double target) {
            this.inputs = inputs;
            this.target = target;
        }
    }
}