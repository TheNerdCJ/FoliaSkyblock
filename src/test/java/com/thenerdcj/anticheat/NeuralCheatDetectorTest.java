package com.thenerdcj.anticheat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeuralCheatDetectorTest {

    private NeuralCheatDetector detector;

    @BeforeEach
    void setUp() {
        detector = new NeuralCheatDetector();
    }

    @Test
    void testPredictReturnsValueBetweenZeroAndOne() {
        double[] normalInput = {4.0, 1.2, 3.0, 12.0, 0, 0, 1, 0.6};
        double result = detector.predict(normalInput);
        assertTrue(result >= 0.0 && result <= 1.0, "Output should be in [0,1] range");
    }

    @Test
    void testTrainDoesNotThrow() {
        double[] input = {3.8, 0.9, 2.5, 8.0, 0, 0, 0, 0.5};
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 50; i++) {
                detector.train(input, 0.1); // label as not cheating
            }
        });
    }

    @Test
    void testGetCheatProbabilityWithProfile() {
        // This is a smoke test — real profiles would come from AntiCheatManager
        PlayerBehaviorProfile profile = new PlayerBehaviorProfile(java.util.UUID.randomUUID());
        // Add some normal movement
        for (int i = 0; i < 30; i++) {
            profile.addMovementSample(4.0 + (Math.random() - 0.5));
        }

        double prob = detector.getCheatProbability(profile);
        assertTrue(prob >= 0.0 && prob <= 1.0);
    }

    // Task 6: more integration-like samples for museum/minion AC (party/dim flows tested in other DB tests)
    @Test
    void testTrainExpandedAbuseSamples() {
        assertDoesNotThrow(() -> {
            double[] minionMacro = {40.0, 25.0, 0.2, 80.0, 1, 0, 0, 0.85};
            for (int i=0; i<15; i++) detector.train(minionMacro, 0.9);
            double[] museumSpam = {1.0, 0.5, 15.0, 3.0, 0, 0, 1, 0.75};
            detector.train(museumSpam, 0.7);
        });
    }
}