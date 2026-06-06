package com.thenerdcj.anticheat;

import java.util.*;

/**
 * Simple Neural Network for Cheat Detection (Updated)
 * <p>
 * === TIER 3 REVIEW DECISION ===
 * This is an experimental, handwritten neural network (MLP).
 * It is kept for advanced users but is NOT recommended for most servers.
 * <p>
 * Strong recommendation: Disable the neural component in production or replace the entire
 * AntiCheat system with a mature plugin. The custom heuristics in AntiCheatManager
 * (fast break, xray, dupe, XP) are more valuable than the NN for a Skyblock context.
 * <p>
 * The network does NOT persist learned weights between restarts.
 * <p>
 * Lightweight MLP that learns legitimate vs cheating behavior in FoliaSkyblock.
 * High ore rates from upgraded IslandOreGenerator are treated as legitimate.
 */
public class NeuralCheatDetector {

    private static final int INPUT_SIZE = 8;
    private static final int HIDDEN_SIZE = 6;
    private static final int OUTPUT_SIZE = 1;

    private double[][] weightsInputHidden;
    private double[][] weightsHiddenOutput;
    private double[] biasHidden;
    private double[] biasOutput;

    private static final double LEARNING_RATE = 0.1;

    private final List<TrainingSample> trainingData = new ArrayList<>();
    private static final int MAX_TRAINING_SAMPLES = 1000;

    public NeuralCheatDetector() {
        initializeNetwork();
    }

    private void initializeNetwork() {
        Random random = new Random(42);

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

    public void train(double[] inputs, double target) {
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

        double[] hiddenErrors = new double[HIDDEN_SIZE];
        for (int j = 0; j < HIDDEN_SIZE; j++) {
            hiddenErrors[j] = outputError * weightsHiddenOutput[j][0] * reluDerivative(hidden[j]);
        }

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
            trainingData.removeFirst();
        }
    }

    public double[] extractFeatures(PlayerBehaviorProfile profile) {
        double[] features = new double[INPUT_SIZE];

        features[0] = Math.min(1.0, profile.getAverageSpeed() / 20.0);
        features[1] = Math.min(1.0, profile.getSpeedStandardDeviation() / 5.0);
        features[2] = Math.min(1.0, profile.getRecentAttackCount() / 20.0);
        features[3] = Math.min(1.0, profile.getOreMiningRate() / 10.0);

        features[4] = profile.hasHighEnchantments() ? 1.0 : 0.0;
        features[5] = profile.hasHighPotions() ? 1.0 : 0.0;
        features[6] = Math.min(1.0, profile.getFlagCount() / 20.0);

        // X-ray heuristic: high ore/low stone ratio is suspicious.
        // Generator ores are excluded upstream in AntiCheatManager → only suspicious ores counted here.
        double stone = Math.max(1, profile.getStoneMinedCount());
        double oreRatio = profile.getBlocksBrokenTotal() > 0
                ? (profile.getOreMinedCount() * 1.0 / stone)
                : 0;
        features[7] = Math.min(1.0, oreRatio / 3.0);

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